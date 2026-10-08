import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../lib/auth";
import { insertCard } from "../lib/queries";

// POST /api/notes  { text } or { png: "<base64>" }  — becomes the Note card.
// Drawings are small monochrome PNGs from the stylus canvas; they fit in a row.
const MAX_PNG_BYTES = 400 * 1024;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") return res.status(405).end();
  const who = authenticate(req.headers.authorization);
  if (!who) return res.status(401).json({ error: "Unauthorized" });
  const b = (req.body ?? {}) as Record<string, unknown>;

  if (typeof b.png === "string") {
    const bytes = Buffer.byteLength(b.png, "base64");
    if (bytes === 0 || bytes > MAX_PNG_BYTES) return res.status(400).json({ error: `png must be 1..${MAX_PNG_BYTES} bytes` });
    const card = await insertCard({ kind: "drawing", title: "Note", body: null, data: { png: b.png }, show_from: null, show_until: null, priority: 5, source: who });
    return res.status(201).json({ card: { ...card, data: { png: "(omitted)" } } });
  }
  if (typeof b.text === "string" && b.text.trim()) {
    const card = await insertCard({ kind: "note", title: "Note", body: b.text.trim().slice(0, 200), data: null, show_from: null, show_until: null, priority: 5, source: who });
    return res.status(201).json({ card });
  }
  return res.status(400).json({ error: "text or png is required" });
}
