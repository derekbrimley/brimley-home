// A show's seasons and episodes, so a kid can pick one and the TV starts it
// instead of stopping on the show's page. JustWatch gives each episode's link
// on our service; TMDB adds a still so the tiles read without reading. Cached
// a day in `kv` (episodes:<source_key>); the daily cron warms every show.
import type { CatalogRow, JWOffer, TmdbResult } from "./catalog";
import { bestTmdbResult, justwatchOrThrow, justwatchTitle, pickOffer, serviceLabel, tmdb } from "./catalog";
import { getKv, setKv } from "./queries";
import type { ShowEpisodes, ShowSeason } from "./types";

const STALE_MS = 24 * 60 * 60 * 1000;

export interface JWEpisode {
  season: number | null;
  episode: number | null;
  title: string | null;
  offers: JWOffer[];
}

export interface TmdbEpisode { name: string | null; still: string | null }

// season -> episode -> TMDB details
export type TmdbStills = Map<number, Map<number, TmdbEpisode>>;

// Pure: JustWatch's episodes (plus TMDB stills) into the seasons the tablet shows.
// A link two episodes share is the show's or season's page, not the episode's,
// so those are dropped; so are specials (season 0) and seasons left empty.
export function buildSeasons(episodes: JWEpisode[], service: string, stills: TmdbStills = new Map()): ShowSeason[] {
  const bySeason = new Map<number, { number: number; title: string; link: string; stillUrl: string | null }[]>();
  for (const e of episodes) {
    if (!e.season || !e.episode) continue;
    const link = pickOffer(e.offers, service);
    if (!link) continue;
    const t = stills.get(e.season)?.get(e.episode);
    const list = bySeason.get(e.season) ?? [];
    if (list.some((x) => x.number === e.episode)) continue;
    list.push({
      number: e.episode,
      title: e.title?.trim() || t?.name?.trim() || `Episode ${e.episode}`,
      link,
      stillUrl: t?.still ? `https://image.tmdb.org/t/p/w300${t.still}` : null,
    });
    bySeason.set(e.season, list);
  }
  const uses = new Map<string, number>();
  for (const list of bySeason.values()) for (const e of list) uses.set(e.link, (uses.get(e.link) ?? 0) + 1);
  return [...bySeason.entries()]
    .sort(([a], [b]) => a - b)
    .map(([number, list]) => ({ number, episodes: list.filter((e) => uses.get(e.link) === 1).sort((a, b) => a.number - b.number) }))
    .filter((s) => s.episodes.length > 0);
}

// Pure: why a show ended up with no episodes, in a line a parent can act on.
// Shown on the tablet's TV test screen and in the cron summary.
export function explainEmpty(episodes: JWEpisode[], service: string): string {
  const label = serviceLabel(service);
  const numbered = episodes.filter((e) => e.season && e.episode);
  if (numbered.length === 0) return "JustWatch lists no episodes for this show";
  const links = numbered.map((e) => pickOffer(e.offers, service)).filter((l): l is string => !!l);
  if (links.length === 0) return `JustWatch has no ${label} links for its ${numbered.length} episodes`;
  const counts = new Map<string, number>();
  for (const l of links) counts.set(l, (counts.get(l) ?? 0) + 1);
  const [top, n] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
  return `${n} of ${links.length} ${label} episode links are the same page: ${top}`;
}

const SEASONS_QUERY = `query Seasons($nodeId: ID!, $country: Country!, $language: Language!) {
  node(id: $nodeId) { ... on Show {
    seasons(sortDirection: ASC) { id content(country: $country, language: $language) { ... on SeasonContent { seasonNumber } } }
  } } }`;

const EPISODES_QUERY = `query Episodes($nodeId: ID!, $country: Country!, $language: Language!) {
  node(id: $nodeId) { ... on Season {
    episodes(sortDirection: ASC) {
      content(country: $country, language: $language) { title ... on EpisodeContent { seasonNumber episodeNumber } }
      offers(country: $country, platform: WEB) { monetizationType standardWebURL package { technicalName clearName } }
    }
  } } }`;

type JWSeasonsData = { node?: { seasons?: { id: string; content?: { seasonNumber?: number | null } }[] } };
type JWEpisodesData = { node?: { episodes?: { content?: { title?: string | null; seasonNumber?: number | null; episodeNumber?: number | null }; offers?: JWOffer[] }[] } };

async function justwatchEpisodes(title: string, year: number | null, country: string): Promise<JWEpisode[]> {
  const show = await justwatchTitle(title, year, country);
  if (!show) throw new Error("JustWatch doesn't know this show");
  // A failed call throws rather than return a short list, so it isn't cached for a day.
  const data = await justwatchOrThrow<JWSeasonsData>(SEASONS_QUERY, { nodeId: show.id, country, language: "en" });
  const wanted = (data.node?.seasons ?? []).filter((s) => (s.content?.seasonNumber ?? 0) > 0);
  const lists = await Promise.all(wanted.map(async (s) => {
    const page = await justwatchOrThrow<JWEpisodesData>(EPISODES_QUERY, { nodeId: s.id, country, language: "en" });
    return (page.node?.episodes ?? []).map((e) => ({
      season: e.content?.seasonNumber ?? s.content?.seasonNumber ?? null,
      episode: e.content?.episodeNumber ?? null,
      title: e.content?.title ?? null,
      offers: e.offers ?? [],
    }));
  }));
  return lists.flat();
}

// Best effort: no TMDB key, no match or a failed season just means no pictures.
async function tmdbStills(title: string, seasons: number[]): Promise<TmdbStills> {
  const out: TmdbStills = new Map();
  try {
    const search = await tmdb<{ results: TmdbResult[] }>("/search/tv", { query: title, include_adult: "false" });
    const hit = bestTmdbResult((search.results ?? []).map((r) => ({ ...r, media_type: "tv" as const })), "shows", title);
    if (!hit) return out;
    await Promise.all(seasons.map(async (n) => {
      try {
        const s = await tmdb<{ episodes?: { episode_number: number; name?: string; still_path?: string | null }[] }>(`/tv/${hit.id}/season/${n}`);
        out.set(n, new Map((s.episodes ?? []).map((e) => [e.episode_number, { name: e.name ?? null, still: e.still_path ?? null }])));
      } catch { /* this season has no pictures */ }
    }));
  } catch { /* no pictures */ }
  return out;
}

async function fetchEpisodes(row: CatalogRow, service: string, country: string): Promise<ShowEpisodes> {
  const episodes = await justwatchEpisodes(row.title, row.year, country);
  const numbers = [...new Set(episodes.map((e) => e.season).filter((n): n is number => !!n))];
  const seasons = buildSeasons(episodes, service, await tmdbStills(row.title, numbers));
  return { seasons, note: seasons.length ? null : explainEmpty(episodes, service) };
}

interface CachedSeasons { fetchedAt: string; service: string; seasons: ShowSeason[]; note?: string | null }

export function episodesKey(sourceKey: string): string {
  return `episodes:${sourceKey}`;
}

// Cached seasons while fresh; a failed refresh falls back to the stale copy.
export async function showEpisodes(row: CatalogRow, opts: { force?: boolean } = {}): Promise<ShowEpisodes> {
  const service = row.service;
  if (row.shelf !== "shows" || !service) return { seasons: [], note: "Not a show on a paid service" };
  const key = episodesKey(row.source_key);
  const cached = await getKv<CachedSeasons>(key);
  const usable = cached && cached.service === service ? cached : null;
  const fromCache = (c: CachedSeasons): ShowEpisodes => ({ seasons: c.seasons, note: c.note ?? null });
  if (!opts.force && usable && Date.now() - Date.parse(usable.fetchedAt) < STALE_MS) return fromCache(usable);
  try {
    const out = await fetchEpisodes(row, service, process.env.TMDB_REGION ?? "US");
    await setKv(key, { fetchedAt: new Date().toISOString(), service, ...out } satisfies CachedSeasons);
    return out;
  } catch (e) {
    if (usable) return fromCache(usable);
    throw e;
  }
}
