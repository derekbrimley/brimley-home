import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../../lib/auth";
import { subPath } from "../../lib/route";
import { activeCards, validateCardInput } from "../../lib/cards";
import { deleteCard, getCardRows, insertCard } from "../../lib/queries";

// GET    /api/cards          active cards
// POST   /api/cards          add a card (Zo posts the lunch menu here)
// DELETE /api/cards/:id      remove a card
export default async function handler(req: VercelRequest, res: VercelResponse) {
  const who = authenticate(req.headers.authorization);
  if (!who) return res.status(401).json({ error: "Unauthorized" });

  const path = subPath(req.url, "/api/cards");

  if (req.method === "GET" && path.length === 0) {
    return res.status(200).json({ cards: activeCards(await getCardRows(), new Date().toISOString()) });
  }

  if (req.method === "POST" && path.length === 0) {
    const err = validateCardInput(req.body);
    if (err) return res.status(400).json({ error: err });
    const b = req.body as Record<string, unknown>;
    const card = await insertCard({
      kind: (b.kind as string).trim(),
      title: (b.title as string).trim(),
      body: typeof b.body === "string" ? b.body : null,
      data: (b.data as Record<string, unknown> | undefined) ?? null,
      show_from: (b.show_from as string | undefined) ?? null,
      show_until: (b.show_until as string | undefined) ?? null,
      priority: typeof b.priority === "number" ? b.priority : 0,
      source: who,
    });
    return res.status(201).json({ card });
  }

  if (req.method === "DELETE" && path.length === 1) {
    const id = Number(path[0]);
    if (!Number.isInteger(id)) return res.status(400).json({ error: "bad id" });
    await deleteCard(id);
    return res.status(204).end();
  }

  return res.status(404).json({ error: "Not found" });
}
