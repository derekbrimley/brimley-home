import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../../lib/auth";
import { getSheetCache, recordBountyClaim, setCompletion } from "../../lib/queries";
import { writeBountyStatus } from "../../lib/sheet";
import { localDateString } from "../../lib/time";

// POST /api/jobs/done           { jobId, done, date? }   tick or untick a job
// POST /api/jobs/bounty/claim   { row }                  "I did it" on a bounty
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") return res.status(405).end();
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });

  const path = ([] as string[]).concat((req.query.path as string | string[] | undefined) ?? []);
  const body = (req.body ?? {}) as Record<string, unknown>;
  const tz = process.env.HOME_TZ ?? "America/Denver";

  if (path.join("/") === "done") {
    const jobId = typeof body.jobId === "string" ? body.jobId : "";
    if (!jobId.includes("|")) return res.status(400).json({ error: "jobId is required" });
    const date = typeof body.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.date) ? body.date : localDateString(new Date(), tz);
    await setCompletion(date, jobId, body.done !== false);
    return res.status(200).json({ ok: true, date, jobId, done: body.done !== false });
  }

  if (path.join("/") === "bounty/claim") {
    const row = Number(body.row);
    if (!Number.isInteger(row) || row < 2) return res.status(400).json({ error: "row is required" });
    const sheet = await getSheetCache();
    const bounty = sheet?.bounties.find((b) => b.row === row);
    if (!bounty) return res.status(404).json({ error: "no such bounty" });
    if (bounty.status !== "open") return res.status(409).json({ error: `bounty is ${bounty.status}` });
    await recordBountyClaim(row, bounty.title, bounty.kid, bounty.amountCents);
    await writeBountyStatus(row, "waiting");
    return res.status(200).json({ ok: true, status: "waiting" });
  }

  return res.status(404).json({ error: "Not found" });
}
