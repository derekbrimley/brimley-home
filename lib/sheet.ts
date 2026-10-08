// The master Google Sheet. Tabs: Watch, Listen, Jobs, Bounties, Makes, Services.
// Parents fill the human columns; the app fills the rest.
import { SHEETS_RW, googleGet, googlePut } from "./google";
import type { Shelf } from "./types";

export interface WatchRow { row: number; title: string; shelf: Shelf | null; foundOn: string; link: string; }
export interface ListenRow { row: number; title: string; feedUrl: string; }
export interface JobRow { row: number; kid: string; title: string; days: number[]; } // 1=Mon..7=Sun
export interface BountyRow { row: number; title: string; amountCents: number; status: "open" | "waiting" | "paid"; kid: string | null; }
export interface MakeRow { row: number; title: string; who: string; link: string; imageUrl: string; }
export interface SheetData {
  watch: WatchRow[];
  listen: ListenRow[];
  jobs: JobRow[];
  bounties: BountyRow[];
  makes: MakeRow[];
  services: string[];     // normalized ids: disneyplus, max, netflix, prime, ...
  fetchedAt: string;
}

export const TABS = ["Watch", "Listen", "Jobs", "Bounties", "Makes", "Services"] as const;

type Grid = string[][];

function cell(r: string[] | undefined, i: number): string {
  return (r?.[i] ?? "").toString().trim();
}

export function parseShelf(s: string): Shelf | null {
  const v = s.toLowerCase().trim();
  if (["show", "shows", "tv", "series"].includes(v)) return "shows";
  if (["movie", "movies", "film", "films"].includes(v)) return "movies";
  if (["video", "videos", "youtube"].includes(v)) return "videos";
  if (["listen", "podcast", "podcasts", "audio"].includes(v)) return "listen";
  return null;
}

// "Mon, Wed, Fri" | "weekdays" | "daily" | "every day" | "weekends" | "M/W/F"
export function parseDays(s: string): number[] {
  const v = s.toLowerCase().trim();
  if (!v || v === "daily" || v === "every day" || v === "everyday" || v === "all") return [1, 2, 3, 4, 5, 6, 7];
  if (v === "weekdays" || v === "school days") return [1, 2, 3, 4, 5];
  if (v === "weekends" || v === "weekend") return [6, 7];
  const names: Record<string, number> = { mon: 1, m: 1, tue: 2, tues: 2, tu: 2, t: 2, wed: 3, w: 3, thu: 4, thur: 4, thurs: 4, th: 4, r: 4, fri: 5, f: 5, sat: 6, sa: 6, sun: 7, su: 7 };
  const out = new Set<number>();
  for (const tok of v.split(/[\s,/;]+/)) {
    const key = tok.replace(/[^a-z]/g, "");
    if (!key) continue;
    const full = Object.keys(names).find((n) => n.length >= 3 && key.startsWith(n));
    const n = full ? names[full] : names[key];
    if (n) out.add(n);
  }
  return [...out].sort();
}

export function parseAmountCents(s: string): number {
  const m = s.replace(/[^0-9.]/g, "");
  if (!m) return 0;
  return Math.round(parseFloat(m) * 100);
}

export function normalizeService(s: string): string {
  const v = s.toLowerCase().replace(/[^a-z0-9]/g, "");
  if (["disney", "disneyplus", "disneyp"].some((p) => v.startsWith(p))) return "disneyplus";
  if (v === "max" || v === "hbomax" || v === "hbo") return "max";
  if (v.startsWith("netflix")) return "netflix";
  if (v.startsWith("prime") || v.startsWith("amazon")) return "prime";
  if (v.startsWith("hulu")) return "hulu";
  if (v.startsWith("apple")) return "appletv";
  if (v.startsWith("paramount")) return "paramount";
  if (v.startsWith("peacock")) return "peacock";
  if (v.startsWith("youtube")) return "youtube";
  return v;
}

export const SERVICE_LABELS: Record<string, string> = {
  disneyplus: "Disney+", max: "Max", netflix: "Netflix", prime: "Prime", hulu: "Hulu", appletv: "Apple TV+",
  paramount: "Paramount+", peacock: "Peacock", youtube: "YouTube", podcast: "Podcast",
};

export function parseSheet(grids: Record<string, Grid>, fetchedAt = new Date().toISOString()): SheetData {
  const body = (tab: string) => (grids[tab] ?? []).slice(1); // drop header row
  const watch: WatchRow[] = body("Watch")
    .map((r, i) => ({ row: i + 2, title: cell(r, 0), shelf: parseShelf(cell(r, 1)), foundOn: cell(r, 2), link: cell(r, 3) }))
    .filter((r) => r.title);
  const listen: ListenRow[] = body("Listen")
    .map((r, i) => ({ row: i + 2, title: cell(r, 0), feedUrl: cell(r, 1) }))
    .filter((r) => r.title);
  const jobs: JobRow[] = body("Jobs")
    .map((r, i) => ({ row: i + 2, kid: cell(r, 0), title: cell(r, 1), days: parseDays(cell(r, 2)) }))
    .filter((r) => r.kid && r.title);
  const bounties: BountyRow[] = body("Bounties")
    .map((r, i) => {
      const status = cell(r, 2).toLowerCase();
      return {
        row: i + 2,
        title: cell(r, 0),
        amountCents: parseAmountCents(cell(r, 1)),
        status: (status.startsWith("paid") ? "paid" : status.startsWith("wait") || status.startsWith("done") ? "waiting" : "open") as BountyRow["status"],
        kid: cell(r, 3) || null,
      };
    })
    .filter((r) => r.title);
  const makes: MakeRow[] = body("Makes")
    .map((r, i) => ({ row: i + 2, title: cell(r, 0), who: cell(r, 1), link: cell(r, 2), imageUrl: cell(r, 3) }))
    .filter((r) => r.title);
  const services = body("Services").map((r) => normalizeService(cell(r, 0))).filter(Boolean);
  return { watch, listen, jobs, bounties, makes, services, fetchedAt };
}

export async function fetchSheet(): Promise<SheetData> {
  const id = process.env.GOOGLE_SHEET_ID;
  if (!id) throw new Error("GOOGLE_SHEET_ID is not set");
  const ranges = TABS.map((t) => `ranges=${encodeURIComponent(`${t}!A1:F500`)}`).join("&");
  const url = `https://sheets.googleapis.com/v4/spreadsheets/${id}/values:batchGet?${ranges}&majorDimension=ROWS`;
  const data = await googleGet<{ valueRanges: { range: string; values?: Grid }[] }>(url, SHEETS_RW);
  const grids: Record<string, Grid> = {};
  data.valueRanges.forEach((vr, i) => { grids[TABS[i]] = vr.values ?? []; });
  return parseSheet(grids);
}

// Write "Found on" (C) and link (D) back to a Watch row so the sheet shows what
// the app decided. Blank foundOn means "not streaming on a service you pay for".
export async function writeWatchResult(row: number, foundOn: string, link: string): Promise<void> {
  const id = process.env.GOOGLE_SHEET_ID;
  if (!id) return;
  const range = `Watch!C${row}:D${row}`;
  await googlePut(
    `https://sheets.googleapis.com/v4/spreadsheets/${id}/values/${encodeURIComponent(range)}?valueInputOption=RAW`,
    SHEETS_RW,
    { range, majorDimension: "ROWS", values: [[foundOn, link]] }
  );
}

export async function writeBountyStatus(row: number, status: string): Promise<void> {
  const id = process.env.GOOGLE_SHEET_ID;
  if (!id) return;
  const range = `Bounties!C${row}`;
  await googlePut(
    `https://sheets.googleapis.com/v4/spreadsheets/${id}/values/${encodeURIComponent(range)}?valueInputOption=RAW`,
    SHEETS_RW,
    { range, majorDimension: "ROWS", values: [[status]] }
  );
}

export function serviceLabelFor(service: string): string {
  return SERVICE_LABELS[service] ?? service;
}
