import { describe, expect, it } from "vitest";
import { lunchCard, parseLunchWeek, type LunchWeek } from "./lunch";
import { zonedMidnight } from "./time";

const week = {
  week_of: "2026-10-12",
  days: [
    { date: "2026-10-13", menu: "Soft Tacos (chicken, taco meat, or pork)" },
    { date: "2026-10-12", menu: " Breakfast for Lunch " },
    { date: "2026-10-16", menu: "Cheese or Pepperoni Pizza Rippers" },
  ],
};

describe("parseLunchWeek", () => {
  it("labels days from their dates and sorts them", () => {
    expect(parseLunchWeek(week)).toEqual({
      weekOf: "2026-10-12",
      days: [
        { day: "Mon", date: "2026-10-12", menu: "Breakfast for Lunch" },
        { day: "Tue", date: "2026-10-13", menu: "Soft Tacos (chicken, taco meat, or pork)" },
        { day: "Fri", date: "2026-10-16", menu: "Cheese or Pepperoni Pizza Rippers" },
      ],
    });
  });

  it("rejects anything that isn't one school week", () => {
    expect(parseLunchWeek(null)).toEqual({ error: expect.stringMatching(/object/) });
    expect(parseLunchWeek({ ...week, week_of: "2026-10-13" })).toEqual({ error: expect.stringMatching(/Monday/) });
    expect(parseLunchWeek({ ...week, week_of: "10/12" })).toEqual({ error: expect.stringMatching(/week_of/) });
    expect(parseLunchWeek({ ...week, days: [] })).toEqual({ error: expect.stringMatching(/days/) });
    expect(parseLunchWeek({ ...week, days: [{ date: "2026-10-17", menu: "x" }] })).toEqual({ error: expect.stringMatching(/days\[0\]\.date/) });
    expect(parseLunchWeek({ ...week, days: [{ date: "2026-02-30", menu: "x" }] })).toEqual({ error: expect.stringMatching(/days\[0\]\.date/) });
    expect(parseLunchWeek({ ...week, days: [{ date: "2026-10-12", menu: " " }] })).toEqual({ error: expect.stringMatching(/menu/) });
    expect(parseLunchWeek({ ...week, days: [{ date: "2026-10-12", menu: "a" }, { date: "2026-10-12", menu: "b" }] })).toEqual({ error: expect.stringMatching(/repeated/) });
  });
});

describe("lunchCard", () => {
  it("shows only on that Monday, in the home timezone", () => {
    const card = lunchCard(parseLunchWeek(week) as LunchWeek, "America/Denver");
    expect(card.kind).toBe("lunch_menu");
    expect(card.show_from).toBe("2026-10-12T06:00:00.000Z");
    expect(card.show_until).toBe("2026-10-13T05:59:59.000Z");
  });
});

describe("zonedMidnight", () => {
  it("handles both sides of daylight saving", () => {
    expect(zonedMidnight("2026-01-05", "America/Denver").toISOString()).toBe("2026-01-05T07:00:00.000Z");
    expect(zonedMidnight("2026-11-02", "America/Denver").toISOString()).toBe("2026-11-02T07:00:00.000Z");
    expect(zonedMidnight("2026-11-01", "America/Denver").toISOString()).toBe("2026-11-01T06:00:00.000Z");
  });
});
