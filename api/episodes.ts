import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../lib/auth";
import { showEpisodes } from "../lib/episodes";
import { getCatalogRows } from "../lib/queries";

// GET /api/episodes?id=<catalog id> — a show's seasons, each episode with its own
// link on the show's service. Empty when the service has no per-episode links;
// the tablet then opens the show's page as before.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "GET") return res.status(405).end();
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });
  const id = Array.isArray(req.query.id) ? req.query.id[0] : req.query.id;
  if (!id) return res.status(400).json({ error: "id is required" });

  const row = (await getCatalogRows()).find((r) => r.source_key === id);
  if (!row || row.shelf !== "shows" || !row.service) return res.status(404).json({ error: "That show isn't on the shelf" });
  try {
    return res.status(200).json(await showEpisodes(row));
  } catch (e) {
    const why = (e as Error).message;
    console.error(`episodes ${id}: ${why}`);
    return res.status(502).json({ error: `Couldn't find the episodes (${why})` });
  }
}
