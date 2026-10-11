import type { VercelRequest, VercelResponse } from "@vercel/node";
import { freshSheet, syncCatalog } from "../../lib/today";
import { isYotoConfigured, refreshStories } from "../../lib/yoto";

// Daily (vercel.json): re-read the sheet and re-resolve every title, so a show
// that left a service disappears and one that came back reappears. Also safe
// to run by hand from Vercel's Cron Jobs page; the summary goes to the logs.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  const secret = process.env.CRON_SECRET;
  if (secret && req.headers.authorization !== `Bearer ${secret}`) return res.status(401).end();
  const sheet = await freshSheet(true);
  const errors: string[] = [];
  const rows = await syncCatalog(sheet, { limit: Number.MAX_SAFE_INTEGER, force: true, errors });
  // New Yoto cards show up the next morning without waiting out the cache.
  let stories: number | null = null;
  if (isYotoConfigured()) {
    try { stories = await refreshStories(); } catch (e) { errors.push(`yoto: ${(e as Error).message}`); }
  }
  const summary = {
    ok: errors.length === 0,
    services: sheet.services,
    titles: rows.map((r) => ({ title: r.title, shelf: r.shelf, service: r.service, link: r.link })),
    visible: rows.filter((r) => r.service).length,
    stories,
    errors,
  };
  console.log(JSON.stringify(summary));
  return res.status(200).json(summary);
}
