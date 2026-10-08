import type { VercelRequest, VercelResponse } from "@vercel/node";
import { freshSheet, syncCatalog } from "../../lib/today";

// Daily (vercel.json): re-read the sheet and re-resolve every title, so a show
// that left a service disappears and one that came back reappears.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  const secret = process.env.CRON_SECRET;
  if (secret && req.headers.authorization !== `Bearer ${secret}`) return res.status(401).end();
  const sheet = await freshSheet(true);
  const rows = await syncCatalog(sheet, Number.MAX_SAFE_INTEGER);
  return res.status(200).json({ ok: true, titles: rows.length, visible: rows.filter((r) => r.service).length });
}
