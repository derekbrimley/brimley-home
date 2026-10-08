import { describe, expect, it } from "vitest";
import { activeCards, validateCardInput } from "./cards";

const base = { body: null, data: null, source: "zo" };
describe("activeCards", () => {
  it("keeps cards inside their window, highest priority first", () => {
    const rows = [
      { id: 1, kind: "lunch_menu", title: "Lunch", show_from: "2026-10-12T06:00:00Z", show_until: "2026-10-12T18:00:00Z", priority: 1, ...base },
      { id: 2, kind: "note", title: "Note", show_from: null, show_until: null, priority: 5, ...base },
      { id: 3, kind: "birthday", title: "Old", show_from: "2026-10-01T00:00:00Z", show_until: "2026-10-02T00:00:00Z", priority: 9, ...base },
      { id: 4, kind: "countdown", title: "Future", show_from: "2026-11-01T00:00:00Z", show_until: null, priority: 9, ...base },
    ];
    expect(activeCards(rows, "2026-10-12T13:00:00Z").map((c) => c.id)).toEqual([2, 1]);
  });
});

describe("validateCardInput", () => {
  it("requires kind and title and valid dates", () => {
    expect(validateCardInput(null)).toMatch(/object/);
    expect(validateCardInput({ title: "x" })).toMatch(/kind/);
    expect(validateCardInput({ kind: "note", title: "x", show_from: "yesterday" })).toMatch(/show_from/);
    expect(validateCardInput({ kind: "note", title: "x", show_from: "2026-10-12T06:00:00Z", data: { a: 1 } })).toBeNull();
  });
});
