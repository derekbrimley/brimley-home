import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../lib/auth";
import { fetchPlaying } from "../lib/today";

// GET /api/playing — just today.playing (kitchen and Yoto), for the tablet's
// 15-second refresh while something is playing. Cheaper than /api/today.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "GET") return res.status(405).end();
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });
  const warnings: string[] = [];
  const playing = await fetchPlaying(warnings);
  res.setHeader("Cache-Control", "no-store");
  return res.status(200).json({ playing, warnings });
}
