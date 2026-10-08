import type { JobRow, BountyRow } from "./sheet";
import type { JobsBlock, TodayJob } from "./types";
import { addDays, localWeekday, weekStart } from "./time";

export function jobId(kid: string, title: string): string {
  const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  return `${slug(kid)}|${slug(title)}`;
}

export function jobsDueOn(rows: JobRow[], weekday: number): JobRow[] {
  return rows.filter((r) => r.days.includes(weekday));
}

// completions: set of "<date>|<jobId>"
export function buildJobsBlocks(
  rows: JobRow[],
  bounties: BountyRow[],
  completions: Set<string>,
  date: string,
  tz: string,
  now: Date
): JobsBlock[] {
  const weekday = localWeekday(now, tz);
  const kids = [...new Set(rows.map((r) => r.kid))];
  return kids.map((kid) => {
    const due = jobsDueOn(rows.filter((r) => r.kid === kid), weekday);
    const jobs: TodayJob[] = due.map((r) => {
      const id = jobId(kid, r.title);
      return { id, kid, title: r.title, done: completions.has(`${date}|${id}`) };
    });
    const doneCount = jobs.filter((j) => j.done).length;
    const allDone = jobs.length > 0 && doneCount === jobs.length;

    // Stars: each day Mon..today where every job due that day was completed.
    let stars = 0;
    const monday = weekStart(date, weekday);
    for (let i = 0; i < weekday; i++) {
      const d = addDays(monday, i);
      const wd = i + 1;
      const dueThatDay = jobsDueOn(rows.filter((r) => r.kid === kid), wd);
      if (dueThatDay.length === 0) continue;
      const all = dueThatDay.every((r) => completions.has(`${d}|${jobId(kid, r.title)}`));
      if (all) stars++;
    }

    const bounty = bounties.find((b) => b.status !== "paid" && (!b.kid || b.kid.toLowerCase() === kid.toLowerCase())) ?? null;
    return {
      kid,
      jobs,
      doneCount,
      allDone,
      starsThisWeek: stars,
      bounty: bounty ? { row: bounty.row, title: bounty.title, amountCents: bounty.amountCents, status: bounty.status, kid: bounty.kid } : null,
    };
  });
}
