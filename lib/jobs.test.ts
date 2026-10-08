import { describe, expect, it } from "vitest";
import { buildJobsBlocks, jobId } from "./jobs";
import type { BountyRow, JobRow } from "./sheet";

const tz = "America/Denver";
// Wednesday 2026-10-14, 7:42 local
const now = new Date("2026-10-14T13:42:00Z");
const rows: JobRow[] = [
  { row: 2, kid: "Sylvie", title: "Feed Juniper", days: [1, 2, 3, 4, 5, 6, 7] },
  { row: 3, kid: "Sylvie", title: "Piano", days: [1, 3, 5] },
  { row: 4, kid: "Sylvie", title: "Library books", days: [2] },
];
const bounties: BountyRow[] = [{ row: 2, title: "Vacuum the basement", amountCents: 100, status: "open", kid: null }];

describe("buildJobsBlocks", () => {
  it("lists only today's jobs, counts done, computes stars for the week so far", () => {
    const done = new Set([
      `2026-10-12|${jobId("Sylvie", "Feed Juniper")}`, `2026-10-12|${jobId("Sylvie", "Piano")}`, // Mon: all done
      `2026-10-13|${jobId("Sylvie", "Feed Juniper")}`,                                            // Tue: library books missing
      `2026-10-14|${jobId("Sylvie", "Piano")}`,                                                   // Wed: cat not fed yet
    ]);
    const [block] = buildJobsBlocks(rows, bounties, done, "2026-10-14", tz, now);
    expect(block.kid).toBe("Sylvie");
    expect(block.jobs.map((j) => [j.title, j.done])).toEqual([["Feed Juniper", false], ["Piano", true]]);
    expect(block.doneCount).toBe(1);
    expect(block.allDone).toBe(false);
    expect(block.starsThisWeek).toBe(1);
    expect(block.bounty?.title).toBe("Vacuum the basement");
  });
  it("marks all done and awards today's star", () => {
    const done = new Set([`2026-10-14|${jobId("Sylvie", "Feed Juniper")}`, `2026-10-14|${jobId("Sylvie", "Piano")}`]);
    const [block] = buildJobsBlocks(rows, [], done, "2026-10-14", tz, now);
    expect(block.allDone).toBe(true);
    expect(block.starsThisWeek).toBe(1);
    expect(block.bounty).toBeNull();
  });
  it("hides paid bounties and matches a kid-specific one", () => {
    const b: BountyRow[] = [
      { row: 2, title: "Old", amountCents: 100, status: "paid", kid: null },
      { row: 3, title: "Rake", amountCents: 200, status: "waiting", kid: "sylvie" },
    ];
    const [block] = buildJobsBlocks(rows, b, new Set(), "2026-10-14", tz, now);
    expect(block.bounty?.title).toBe("Rake");
    expect(block.bounty?.status).toBe("waiting");
  });
});
