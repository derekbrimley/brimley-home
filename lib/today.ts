import { activeCards } from "./cards";
import { fetchEvents, normalizeEvents, parseFamily } from "./calendar";
import { listenSourceKey, resolveListen, resolveWatch, toCatalogItems, watchSourceKey, type CatalogRow } from "./catalog";
import { fetchKitchenPlaying } from "./crate";
import { buildJobsBlocks } from "./jobs";
import { getCardRows, getCatalogRows, getCompletions, getSheetCache, setSheetCache, upsertCatalogRow } from "./queries";
import { fetchSheet, serviceLabelFor, writeWatchResult, type SheetData } from "./sheet";
import { addDays, localDateString, localWeekday, weekStart } from "./time";
import type { Today, TodayEvent } from "./types";
import { fetchWeather } from "./weather";

const SHEET_STALE_MS = 10 * 60 * 1000;
const RESOLVE_STALE_MS = 24 * 60 * 60 * 1000;
const MAX_RESOLVES_PER_REQUEST = 4;

async function settle<T>(label: string, p: Promise<T>, warnings: string[]): Promise<T | null> {
  try {
    return await p;
  } catch (e) {
    warnings.push(`${label}: ${(e as Error).message}`);
    return null;
  }
}

export async function freshSheet(force = false): Promise<SheetData> {
  const cached = await getSheetCache();
  if (!force && cached && Date.now() - Date.parse(cached.fetchedAt) < SHEET_STALE_MS) return cached;
  try {
    const live = await fetchSheet();
    await setSheetCache(live);
    return live;
  } catch (e) {
    if (cached) return cached;
    throw e;
  }
}

// Resolve rows that are new or older than a day, a few at a time so a request
// never waits on a dozen TMDB calls. The daily cron calls this with no limit.
export async function syncCatalog(sheet: SheetData, limit = MAX_RESOLVES_PER_REQUEST): Promise<CatalogRow[]> {
  const existing = new Map((await getCatalogRows()).map((r) => [r.source_key, r]));
  const wanted: { key: string; run: () => Promise<CatalogRow> }[] = [];

  for (const row of sheet.watch) {
    if (!row.shelf) continue;
    const key = watchSourceKey(row);
    wanted.push({
      key,
      run: async () => {
        const r = await resolveWatch(row, sheet.services);
        const out: CatalogRow = {
          source_key: key, shelf: row.shelf!, title: r.title, service: r.service, link: r.link, poster_url: r.posterUrl,
          year: r.year, runtime_minutes: r.runtimeMinutes, resolved_at: new Date().toISOString(), sheet_row: row.row,
        };
        const foundOn = r.service ? serviceLabelFor(r.service) : "";
        if (row.foundOn !== foundOn || (r.link ?? "") !== row.link) {
          try { await writeWatchResult(row.row, foundOn, r.link ?? ""); } catch { /* sheet write is cosmetic */ }
        }
        return out;
      },
    });
  }
  for (const row of sheet.listen) {
    const key = listenSourceKey(row);
    wanted.push({
      key,
      run: async () => {
        const r = await resolveListen(row);
        return {
          source_key: key, shelf: "listen", title: r.title, service: r.feedUrl ? "podcast" : null, link: r.feedUrl, poster_url: r.posterUrl,
          year: null, runtime_minutes: null, resolved_at: new Date().toISOString(), sheet_row: row.row,
        };
      },
    });
  }

  const due = wanted.filter((w) => {
    const have = existing.get(w.key);
    return !have || !have.resolved_at || Date.now() - Date.parse(have.resolved_at) > RESOLVE_STALE_MS;
  });
  for (const w of due.slice(0, limit)) {
    try {
      const row = await w.run();
      await upsertCatalogRow(row);
      existing.set(w.key, row);
    } catch {
      // leave it unresolved; next request tries again
    }
  }
  // Only rows still in the sheet count.
  const keys = new Set(wanted.map((w) => w.key));
  return [...existing.values()].filter((r) => keys.has(r.source_key));
}

export async function buildToday(now = new Date()): Promise<Today> {
  const tz = process.env.HOME_TZ ?? "America/Denver";
  const date = localDateString(now, tz);
  const weekday = localWeekday(now, tz);
  const warnings: string[] = [];
  const family = parseFamily(process.env.FAMILY_MEMBERS);

  const sheet = await settle("sheet", freshSheet(), warnings);

  const dayStart = new Date(`${date}T00:00:00`);
  const [weather, eventsRaw, completions, cardRows, catalogRows, kitchen] = await Promise.all([
    settle("weather", process.env.HOME_LATLON ? fetchWeather(process.env.HOME_LATLON, tz) : Promise.resolve(null), warnings),
    settle("calendar", fetchEvents(isoAtLocal(date, "00:00", tz), isoAtLocal(addDays(date, 2), "00:00", tz)), warnings),
    settle("jobs", getCompletions(weekStart(date, weekday), date), warnings),
    settle("cards", getCardRows(), warnings),
    sheet ? settle("catalog", syncCatalog(sheet), warnings) : Promise.resolve(null),
    settle("crate", fetchKitchenPlaying(), warnings),
  ]);
  void dayStart;

  const all = eventsRaw ? normalizeEvents(eventsRaw, family) : [];
  const tomorrow = addDays(date, 1);
  const events = all.filter((e) => e.start.slice(0, 10) === date || (e.allDay && e.start <= date && (e.end ?? date) > date));
  const tomorrowFirst: TodayEvent | null = all.find((e) => e.start.slice(0, 10) === tomorrow) ?? null;

  return {
    generatedAt: now.toISOString(),
    date,
    tz,
    weather,
    events,
    tomorrowFirst,
    jobs: sheet ? buildJobsBlocks(sheet.jobs, sheet.bounties, completions ?? new Set(), date, tz, now) : [],
    cards: cardRows ? activeCards(cardRows, now.toISOString()) : [],
    playing: kitchen ? [kitchen] : [],
    catalog: catalogRows && sheet ? toCatalogItems(catalogRows, sheet.services) : [],
    makes: sheet ? sheet.makes.map((m) => ({ title: m.title, who: m.who || null, link: m.link || null, imageUrl: m.imageUrl || null })) : [],
    warnings,
  };
}

// Google Calendar wants RFC3339 with an offset. Build "YYYY-MM-DDTHH:MM:00" in
// the household zone by asking Intl for that zone's offset on that date.
export function isoAtLocal(ymd: string, hhmm: string, tz: string): string {
  const guess = new Date(`${ymd}T${hhmm}:00Z`);
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: tz, timeZoneName: "longOffset" }).formatToParts(guess);
  const off = parts.find((p) => p.type === "timeZoneName")?.value ?? "GMT";
  const m = off.match(/GMT([+-])(\d{2}):?(\d{2})?/);
  const sign = m?.[1] ?? "+";
  const hh = m?.[2] ?? "00";
  const mm = m?.[3] ?? "00";
  return `${ymd}T${hhmm}:00${sign}${hh}:${mm}`;
}
