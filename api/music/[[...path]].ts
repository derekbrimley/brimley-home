import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../../lib/auth";
import { subPath } from "../../lib/route";
import { controlKitchen, fetchShelves, playOnKitchen, type ControlAction } from "../../lib/crate";

// GET  /api/music            shelves: Crate's crates with their current picks
// POST /api/music/play       { uri, source? }   start an album on the kitchen speaker
// POST /api/music/control    { action }          resume | pause | next | previous
export const config = { maxDuration: 15 };

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });
  const path = subPath(req.url, "/api/music").join("/");
  const body = (req.body ?? {}) as Record<string, unknown>;

  try {
    if (req.method === "GET" && path === "") {
      res.setHeader("Cache-Control", "no-store");
      return res.status(200).json({ shelves: await fetchShelves() });
    }
    if (req.method === "POST" && path === "play") {
      const uri = typeof body.uri === "string" ? body.uri : "";
      if (!/^spotify:(album|playlist):[A-Za-z0-9]+$/.test(uri)) return res.status(400).json({ error: "uri must be a spotify album or playlist" });
      const source = typeof body.source === "string" && body.source.trim() ? body.source.trim().slice(0, 100) : "kitchen";
      return res.status(200).json(await playOnKitchen(uri, source));
    }
    if (req.method === "POST" && path === "control") {
      const allowed: ControlAction[] = ["resume", "pause", "next", "previous"];
      const action = body.action as ControlAction;
      if (!allowed.includes(action)) return res.status(400).json({ error: `action must be one of ${allowed.join(", ")}` });
      await controlKitchen(action);
      return res.status(204).end();
    }
  } catch (e) {
    return res.status(502).json({ error: (e as Error).message });
  }
  return res.status(404).json({ error: "Not found" });
}
