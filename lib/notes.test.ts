import { describe, expect, it } from "vitest";
import { noteLimit, toFridgeNotes } from "./notes";

describe("noteLimit", () => {
  it("defaults to 12 when missing or junk", () => {
    expect(noteLimit(undefined)).toBe(12);
    expect(noteLimit("")).toBe(12);
    expect(noteLimit("abc")).toBe(12);
  });
  it("clamps to 1..24", () => {
    expect(noteLimit("0")).toBe(1);
    expect(noteLimit("100")).toBe(24);
    expect(noteLimit("6")).toBe(6);
    expect(noteLimit(["5", "9"])).toBe(5);
  });
});

describe("toFridgeNotes", () => {
  const at = "2026-10-12T15:00:00Z";
  it("maps drawings and text notes, newest order kept", () => {
    expect(toFridgeNotes([
      { id: 9, kind: "drawing", body: null, data: { png: "iVBOR" }, created_at: at },
      { id: 7, kind: "note", body: "Pizza night!", data: null, created_at: at },
    ])).toEqual([
      { id: 9, kind: "drawing", body: null, png: "iVBOR", createdAt: at },
      { id: 7, kind: "note", body: "Pizza night!", png: null, createdAt: at },
    ]);
  });
  it("drops a drawing with no png, a blank note and other kinds", () => {
    expect(toFridgeNotes([
      { id: 3, kind: "drawing", body: null, data: null, created_at: at },
      { id: 2, kind: "note", body: "  ", data: null, created_at: at },
      { id: 1, kind: "birthday", body: "x", data: null, created_at: at },
    ])).toEqual([]);
  });
});
