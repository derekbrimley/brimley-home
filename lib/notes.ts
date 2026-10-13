import type { FridgeNote } from "./types";

// Notes and drawings from the tablet are rows in `cards`. /api/today carries only
// the newest; the Fridge asks GET /api/notes for the ones before it.
export const NOTE_KINDS = ["note", "drawing"] as const;

export interface NoteRow {
  id: number;
  kind: string;
  body: string | null;
  data: Record<string, unknown> | null;
  created_at: string;
}

const DEFAULT_LIMIT = 12;
const MAX_LIMIT = 24;

// ?limit= as sent: missing or junk gives the default, anything else is clamped.
export function noteLimit(raw: unknown): number {
  const n = Number(Array.isArray(raw) ? raw[0] : raw);
  if (raw == null || raw === "" || !Number.isFinite(n)) return DEFAULT_LIMIT;
  return Math.min(MAX_LIMIT, Math.max(1, Math.trunc(n)));
}

export function toFridgeNotes(rows: NoteRow[]): FridgeNote[] {
  const out: FridgeNote[] = [];
  for (const r of rows) {
    if (r.kind === "drawing") {
      const png = typeof r.data?.png === "string" ? r.data.png : "";
      if (png) out.push({ id: r.id, kind: "drawing", body: null, png, createdAt: r.created_at });
    } else if (r.kind === "note" && r.body?.trim()) {
      out.push({ id: r.id, kind: "note", body: r.body, png: null, createdAt: r.created_at });
    }
  }
  return out;
}
