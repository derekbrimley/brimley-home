import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../lib/auth";
import { buildToday } from "../lib/today";

// GET /api/today — everything the home screen shows, assembled server-side.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "GET") return res.status(405).end();
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });
  const today = await buildToday();
  res.setHeader("Cache-Control", "no-store");
  return res.status(200).json(today);
}
