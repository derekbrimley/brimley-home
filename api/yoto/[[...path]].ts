import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../../lib/auth";
import { subPath } from "../../lib/route";
import { fetchStories, fetchTracks, sendToYoto, type YotoAction } from "../../lib/yoto";

// GET  /api/yoto                 { cards, player }  the family's Yoto cards; is the player awake
// GET  /api/yoto/tracks?cardId=  { tracks, tried }  signed audio URLs, to play a card on the tablet
//                                (Vercel only routes one segment here; /api/yoto/cards/:id is rewritten to this)
// POST /api/yoto/play            { cardId }         start a card on the Yoto player
// POST /api/yoto/control         { action }         pause | resume | stop
export const config = { maxDuration: 20 };
const CARD_ID = /^[A-Za-z0-9_-]{1,40}$/;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });
  const path = subPath(req.url, "/api/yoto");
  const body = (req.body ?? {}) as Record<string, unknown>;
  res.setHeader("Cache-Control", "no-store");

  try {
    if (req.method === "GET" && path.length === 0) return res.status(200).json(await fetchStories());
    if (req.method === "GET" && path.join("/") === "tracks") {
      const cardId = new URL(req.url ?? "/", "http://local").searchParams.get("cardId") ?? "";
      if (!CARD_ID.test(cardId)) return res.status(400).json({ error: "Bad card id" });
      return res.status(200).json(await fetchTracks(cardId));
    }
    if (req.method === "POST" && path.join("/") === "play") {
      const cardId = typeof body.cardId === "string" ? body.cardId : "";
      if (!CARD_ID.test(cardId)) return res.status(400).json({ error: "cardId is required" });
      return res.status(200).json(await sendToYoto({ action: "play", cardId }));
    }
    if (req.method === "POST" && path.join("/") === "control") {
      const allowed: YotoAction[] = ["pause", "resume", "stop"];
      const action = body.action as YotoAction;
      if (!allowed.includes(action)) return res.status(400).json({ error: `action must be one of ${allowed.join(", ")}` });
      await sendToYoto({ action });
      return res.status(204).end();
    }
  } catch (e) {
    return res.status(502).json({ error: (e as Error).message });
  }
  return res.status(404).json({ error: "Not found" });
}
