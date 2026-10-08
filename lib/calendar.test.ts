import { describe, expect, it } from "vitest";
import { normalizeEvents, parseFamily, whoFromTitle } from "./calendar";

const family = parseFamily("Sylvie:sylvie,Dad:dad,derek,Mom:mom");

describe("parseFamily", () => {
  it("keeps extra keywords with the previous member", () => {
    expect(family).toEqual([
      { name: "Sylvie", keywords: ["sylvie"] },
      { name: "Dad", keywords: ["dad", "derek"] },
      { name: "Mom", keywords: ["mom"] },
    ]);
  });
});

describe("whoFromTitle", () => {
  it("reads a name prefix and strips it", () => {
    expect(whoFromTitle("Sylvie: piano lesson", family)).toEqual({ who: "Sylvie", title: "piano lesson" });
  });
  it("reads a keyword anywhere and strips a parenthesized one", () => {
    expect(whoFromTitle("Dentist (Derek)", family)).toEqual({ who: "Dad", title: "Dentist" });
    expect(whoFromTitle("Mom book club", family).who).toBe("Mom");
  });
  it("leaves everyone's events alone", () => {
    expect(whoFromTitle("Dinner at Grandma's", family)).toEqual({ who: null, title: "Dinner at Grandma's" });
  });
  it("does not match inside words", () => {
    expect(whoFromTitle("Momentum talk", family).who).toBeNull();
  });
});

describe("normalizeEvents", () => {
  it("drops cancelled, flags all-day, sorts all-day first then by time", () => {
    const out = normalizeEvents(
      [
        { summary: "Piano (Sylvie)", start: { dateTime: "2026-10-12T16:00:00-06:00" }, end: { dateTime: "2026-10-12T16:45:00-06:00" } },
        { summary: "Cancelled thing", status: "cancelled", start: { dateTime: "2026-10-12T10:00:00-06:00" }, end: { dateTime: "2026-10-12T11:00:00-06:00" } },
        { summary: "Recycling", start: { date: "2026-10-12" }, end: { date: "2026-10-13" } },
        { summary: "School", start: { dateTime: "2026-10-12T09:00:00-06:00" }, end: { dateTime: "2026-10-12T15:00:00-06:00" } },
      ],
      family
    );
    expect(out.map((e) => e.title)).toEqual(["Recycling", "School", "Piano"]);
    expect(out[0].allDay).toBe(true);
    expect(out[2].who).toBe("Sylvie");
  });
});
