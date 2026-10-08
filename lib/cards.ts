import type { Card } from "./types";

export interface CardRow {
  id: number;
  kind: string;
  title: string;
  body: string | null;
  data: Record<string, unknown> | null;
  show_from: string | null;
  show_until: string | null;
  priority: number;
  source: string;
}

export function activeCards(rows: CardRow[], nowIso: string): Card[] {
  return rows
    .filter((r) => (!r.show_from || r.show_from <= nowIso) && (!r.show_until || r.show_until >= nowIso))
    .sort((a, b) => b.priority - a.priority || b.id - a.id)
    .map((r) => ({
      id: r.id,
      kind: r.kind,
      title: r.title,
      body: r.body,
      data: r.data,
      showFrom: r.show_from,
      showUntil: r.show_until,
      priority: r.priority,
      source: r.source,
    }));
}

// Validate a POSTed card (from Zo or a phone). Returns an error string or null.
export function validateCardInput(body: unknown): string | null {
  if (!body || typeof body !== "object") return "body must be a JSON object";
  const b = body as Record<string, unknown>;
  if (typeof b.kind !== "string" || !b.kind.trim()) return "kind is required";
  if (typeof b.title !== "string" || !b.title.trim()) return "title is required";
  for (const k of ["show_from", "show_until"]) {
    if (b[k] != null && (typeof b[k] !== "string" || Number.isNaN(Date.parse(b[k] as string)))) return `${k} must be an ISO datetime`;
  }
  if (b.data != null && typeof b.data !== "object") return "data must be an object";
  return null;
}
