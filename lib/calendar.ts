import { CALENDAR_RO, googleGet } from "./google";
import type { TodayEvent } from "./types";

export interface FamilyMember {
  name: string;
  keywords: string[];
}

// "Sylvie:sylvie,Dad:dad,derek,Mom:mom" -> members. A keyword list runs until
// the next "Name:" segment.
export function parseFamily(env: string | undefined): FamilyMember[] {
  const members: FamilyMember[] = [];
  for (const part of (env ?? "").split(",")) {
    const p = part.trim();
    if (!p) continue;
    const i = p.indexOf(":");
    if (i > 0) {
      members.push({ name: p.slice(0, i).trim(), keywords: [p.slice(i + 1).trim().toLowerCase()].filter(Boolean) });
    } else if (members.length) {
      members[members.length - 1].keywords.push(p.toLowerCase());
    }
  }
  for (const m of members) if (!m.keywords.includes(m.name.toLowerCase())) m.keywords.push(m.name.toLowerCase());
  return members;
}

// Assign an event to a person from its title. "Sylvie: piano" and "piano
// (Sylvie)" both match; a bare "piano" is everyone's.
export function whoFromTitle(title: string, family: FamilyMember[]): { who: string | null; title: string } {
  const prefix = title.match(/^\s*([^:]{1,30}):\s*(.+)$/);
  if (prefix) {
    const m = family.find((f) => f.keywords.includes(prefix[1].trim().toLowerCase()));
    if (m) return { who: m.name, title: prefix[2].trim() };
  }
  const lower = title.toLowerCase();
  for (const m of family) {
    for (const k of m.keywords) {
      if (new RegExp(`\\b${k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`).test(lower)) {
        const stripped = title.replace(new RegExp(`\\s*[\\(\\[]\\s*${k}\\s*[\\)\\]]`, "i"), "").trim();
        return { who: m.name, title: stripped || title };
      }
    }
  }
  return { who: null, title: title.trim() };
}

interface GCalEvent {
  summary?: string;
  start: { dateTime?: string; date?: string };
  end: { dateTime?: string; date?: string };
  status?: string;
}

export function normalizeEvents(items: GCalEvent[], family: FamilyMember[]): TodayEvent[] {
  return items
    .filter((e) => e.status !== "cancelled" && (e.start.dateTime || e.start.date))
    .map((e) => {
      const { who, title } = whoFromTitle(e.summary ?? "(untitled)", family);
      const allDay = !e.start.dateTime;
      return {
        start: e.start.dateTime ?? e.start.date!,
        end: e.end?.dateTime ?? e.end?.date ?? null,
        title,
        who,
        allDay,
      };
    })
    .sort((a, b) => (a.allDay === b.allDay ? a.start.localeCompare(b.start) : a.allDay ? -1 : 1));
}

export async function fetchEvents(timeMinIso: string, timeMaxIso: string): Promise<GCalEvent[]> {
  const calendarId = process.env.GOOGLE_CALENDAR_ID;
  if (!calendarId) throw new Error("GOOGLE_CALENDAR_ID is not set");
  const url =
    `https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(calendarId)}/events` +
    `?singleEvents=true&orderBy=startTime&timeMin=${encodeURIComponent(timeMinIso)}&timeMax=${encodeURIComponent(timeMaxIso)}&maxResults=50`;
  const data = await googleGet<{ items?: GCalEvent[] }>(url, CALENDAR_RO);
  return data.items ?? [];
}
