// Turns master-sheet rows into catalog items. TMDB (official) says where a
// title streams; JustWatch's GraphQL endpoint (public, undocumented) gives the
// title's page on that service, which is the link the TV opens. Everything
// here is best effort: a missing link still leaves a poster and a service.
import type { ListenRow, WatchRow } from "./sheet";
import { SERVICE_LABELS, normalizeService } from "./sheet";
import type { CatalogItem, Shelf } from "./types";

export interface ResolvedWatch {
  service: string | null;     // null = not on a paid service -> hidden
  link: string | null;
  posterUrl: string | null;
  year: number | null;
  runtimeMinutes: number | null;
  tmdbId: number | null;
  tmdbType: "movie" | "tv" | null;
  title: string;              // canonical title from TMDB when found
}

export function slug(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

export function youtubeId(input: string): string | null {
  const s = input.trim();
  if (/^[A-Za-z0-9_-]{11}$/.test(s)) return s;
  try {
    const u = new URL(s);
    if (u.hostname === "youtu.be") return u.pathname.slice(1, 12) || null;
    if (u.hostname.endsWith("youtube.com")) {
      const v = u.searchParams.get("v");
      if (v) return v.slice(0, 11);
      const m = u.pathname.match(/\/(?:shorts|embed|live)\/([A-Za-z0-9_-]{11})/);
      if (m) return m[1];
    }
  } catch {
    // not a URL
  }
  return null;
}

// Paid services in the sheet's order win ties, so put the preferred one first.
export function pickService(providers: string[], paid: string[]): string | null {
  const have = new Set(providers.map(normalizeService));
  for (const p of paid) if (have.has(p)) return p;
  return null;
}

export interface JWOffer { monetizationType: string; standardWebURL?: string | null; deeplinkURL?: string | null; package: { technicalName: string; clearName?: string } }

const JW_PACKAGE_TO_SERVICE: Record<string, string> = {
  disneyplus: "disneyplus", max: "max", hbomax: "max", netflix: "netflix", netflixkids: "netflix",
  amazonprime: "prime", amazonprimevideo: "prime", hulu: "hulu", appletvplus: "appletv", appletv: "appletv",
  paramountplus: "paramount", peacocktv: "peacock", peacock: "peacock", youtube: "youtube",
};

export function pickOffer(offers: JWOffer[], service: string): string | null {
  const match = offers.filter((o) => {
    const key = o.package.technicalName.toLowerCase().replace(/[^a-z0-9]/g, "");
    const mapped = JW_PACKAGE_TO_SERVICE[key] ?? normalizeService(key);
    return mapped === service && /^(FLATRATE|FREE|ADS)$/i.test(o.monetizationType);
  });
  const best = match.find((o) => o.standardWebURL) ?? match[0];
  return best?.standardWebURL ?? best?.deeplinkURL ?? null;
}

interface TmdbResult { id: number; media_type: "movie" | "tv" | "person"; title?: string; name?: string; poster_path?: string | null; release_date?: string; first_air_date?: string; popularity?: number }

export function bestTmdbResult(results: TmdbResult[], shelf: Shelf | null, query: string): TmdbResult | null {
  const wanted = shelf === "movies" ? "movie" : shelf === "shows" ? "tv" : null;
  const q = slug(query);
  const candidates = results.filter((r) => r.media_type !== "person" && (!wanted || r.media_type === wanted));
  const exact = candidates.find((r) => slug(r.title ?? r.name ?? "") === q);
  return exact ?? candidates[0] ?? null;
}

async function tmdb<T>(path: string, params: Record<string, string> = {}): Promise<T> {
  const key = process.env.TMDB_API_KEY;
  if (!key) throw new Error("TMDB_API_KEY is not set");
  const qs = new URLSearchParams({ api_key: key, ...params });
  const res = await fetch(`https://api.themoviedb.org/3${path}?${qs}`);
  if (!res.ok) throw new Error(`TMDB ${res.status} for ${path}`);
  return (await res.json()) as T;
}

async function justwatchLink(title: string, year: number | null, service: string, country: string): Promise<string | null> {
  const query = `query Search($filter: TitleFilter!, $country: Country!, $language: Language!, $first: Int!) {
    popularTitles(country: $country, filter: $filter, first: $first) {
      edges { node { ... on MovieOrShow {
        objectType
        content(country: $country, language: $language) { title originalReleaseYear }
        offers(country: $country, platform: WEB) { monetizationType standardWebURL package { technicalName clearName } }
      } } }
    } }`;
  try {
    const res = await fetch("https://apis.justwatch.com/graphql", {
      method: "POST",
      headers: { "Content-Type": "application/json", "User-Agent": "brimley-home/0.1" },
      body: JSON.stringify({ query, variables: { filter: { searchQuery: title }, country, language: "en", first: 5 } }),
    });
    if (!res.ok) return null;
    const data = (await res.json()) as { data?: { popularTitles?: { edges: { node: { content: { title: string; originalReleaseYear: number | null }; offers: JWOffer[] } }[] } } };
    const edges = data.data?.popularTitles?.edges ?? [];
    const q = slug(title);
    const node =
      edges.find((e) => slug(e.node.content.title) === q && (!year || e.node.content.originalReleaseYear === year))?.node ??
      edges.find((e) => slug(e.node.content.title) === q)?.node ??
      edges[0]?.node;
    if (!node) return null;
    return pickOffer(node.offers ?? [], service);
  } catch {
    return null;
  }
}

export async function resolveWatch(row: WatchRow, paidServices: string[]): Promise<ResolvedWatch> {
  const region = process.env.TMDB_REGION ?? "US";
  const empty: ResolvedWatch = { service: null, link: null, posterUrl: null, year: null, runtimeMinutes: null, tmdbId: null, tmdbType: null, title: row.title };

  // Videos never need TMDB.
  if (row.shelf === "videos") {
    const id = youtubeId(row.link || row.title);
    if (!id) return empty;
    let title = row.title;
    let posterUrl: string | null = `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
    try {
      const o = await fetch(`https://www.youtube.com/oembed?url=${encodeURIComponent(`https://www.youtube.com/watch?v=${id}`)}&format=json`);
      if (o.ok) {
        const j = (await o.json()) as { title?: string; thumbnail_url?: string };
        if (j.title && youtubeId(row.title)) title = j.title; // the sheet cell was a URL; use the real title
        if (j.thumbnail_url) posterUrl = j.thumbnail_url;
      }
    } catch { /* keep defaults */ }
    return { ...empty, service: "youtube", link: `https://www.youtube.com/watch?v=${id}`, posterUrl, title };
  }

  const search = await tmdb<{ results: TmdbResult[] }>("/search/multi", { query: row.title, include_adult: "false" });
  const hit = bestTmdbResult(search.results ?? [], row.shelf, row.title);
  if (!hit || hit.media_type === "person") return empty;
  const type = hit.media_type;
  const year = Number((hit.release_date ?? hit.first_air_date ?? "").slice(0, 4)) || null;
  const title = hit.title ?? hit.name ?? row.title;
  const posterUrl = hit.poster_path ? `https://image.tmdb.org/t/p/w342${hit.poster_path}` : null;

  const providers = await tmdb<{ results: Record<string, { flatrate?: { provider_name: string }[]; free?: { provider_name: string }[]; ads?: { provider_name: string }[] }> }>(`/${type}/${hit.id}/watch/providers`);
  const region_ = providers.results?.[region];
  const names = [...(region_?.flatrate ?? []), ...(region_?.free ?? []), ...(region_?.ads ?? [])].map((p) => p.provider_name);
  const service = pickService(names, paidServices);

  let runtimeMinutes: number | null = null;
  if (type === "movie") {
    try {
      const d = await tmdb<{ runtime?: number }>(`/movie/${hit.id}`);
      runtimeMinutes = d.runtime ?? null;
    } catch { /* optional */ }
  }

  if (!service) return { ...empty, posterUrl, year, runtimeMinutes, tmdbId: hit.id, tmdbType: type, title };
  const link = await justwatchLink(title, year, service, region);
  return { service, link, posterUrl, year, runtimeMinutes, tmdbId: hit.id, tmdbType: type, title };
}

export async function resolveListen(row: ListenRow): Promise<{ feedUrl: string | null; posterUrl: string | null; title: string }> {
  if (/^https?:\/\//i.test(row.feedUrl)) return { feedUrl: row.feedUrl, posterUrl: null, title: row.title };
  if (/^https?:\/\//i.test(row.title)) return { feedUrl: row.title, posterUrl: null, title: row.title };
  try {
    const res = await fetch(`https://itunes.apple.com/search?media=podcast&limit=3&term=${encodeURIComponent(row.title)}`);
    if (!res.ok) return { feedUrl: null, posterUrl: null, title: row.title };
    const j = (await res.json()) as { results: { collectionName: string; feedUrl?: string; artworkUrl600?: string }[] };
    const q = slug(row.title);
    const hit = j.results.find((r) => slug(r.collectionName) === q) ?? j.results[0];
    if (!hit?.feedUrl) return { feedUrl: null, posterUrl: null, title: row.title };
    return { feedUrl: hit.feedUrl, posterUrl: hit.artworkUrl600 ?? null, title: hit.collectionName };
  } catch {
    return { feedUrl: null, posterUrl: null, title: row.title };
  }
}

export function serviceLabel(service: string): string {
  return SERVICE_LABELS[service] ?? service;
}

export interface CatalogRow {
  source_key: string;
  shelf: Shelf;
  title: string;
  service: string | null;
  link: string | null;
  poster_url: string | null;
  year: number | null;
  runtime_minutes: number | null;
  resolved_at: string | null;
  sheet_row: number;
}

// Items the tablet gets: resolved, on a paid service (or youtube/podcast), in sheet order.
export function toCatalogItems(rows: CatalogRow[], paidServices: string[]): CatalogItem[] {
  const paid = new Set([...paidServices, "youtube", "podcast"]);
  return rows
    .filter((r) => r.service && paid.has(r.service))
    .sort((a, b) => a.shelf.localeCompare(b.shelf) || a.sheet_row - b.sheet_row)
    .map((r) => ({
      id: r.source_key,
      shelf: r.shelf,
      title: r.title,
      service: r.service!,
      serviceLabel: serviceLabel(r.service!),
      posterUrl: r.poster_url,
      link: r.link,
      runtimeMinutes: r.runtime_minutes,
      year: r.year,
    }));
}

export function watchSourceKey(row: WatchRow): string {
  return `watch:${slug(row.title)}:${row.shelf ?? "unknown"}`;
}
export function listenSourceKey(row: ListenRow): string {
  return `listen:${slug(row.title)}`;
}
