import { describe, expect, it } from "vitest";
import type { CatalogRow } from "./catalog";
import { isDue } from "./today";

const HOUR = 60 * 60 * 1000;
const now = Date.parse("2026-10-11T12:00:00Z");
const row = (service: string | null, ageMs: number): CatalogRow => ({
  source_key: "watch:x:movies", shelf: "movies", title: "X", service, link: null, poster_url: null,
  year: null, runtime_minutes: null, resolved_at: new Date(now - ageMs).toISOString(), sheet_row: 2,
});

describe("isDue", () => {
  it("resolves new rows", () => expect(isDue(undefined, now)).toBe(true));
  it("keeps a visible title for a day", () => {
    expect(isDue(row("netflix", 2 * HOUR), now)).toBe(false);
    expect(isDue(row("netflix", 25 * HOUR), now)).toBe(true);
  });
  it("retries a hidden title after an hour", () => {
    expect(isDue(row(null, 30 * 60 * 1000), now)).toBe(false);
    expect(isDue(row(null, 2 * HOUR), now)).toBe(true);
  });
});
