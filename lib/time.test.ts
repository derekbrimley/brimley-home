import { describe, expect, it } from "vitest";
import { addDays, localDateString, localWeekday, weekStart } from "./time";
import { isoAtLocal } from "./today";

describe("time", () => {
  const tz = "America/Denver";
  it("reads local date and weekday across midnight UTC", () => {
    const d = new Date("2026-10-13T03:30:00Z"); // Monday evening in Denver
    expect(localDateString(d, tz)).toBe("2026-10-12");
    expect(localWeekday(d, tz)).toBe(1);
  });
  it("adds days and finds the week start", () => {
    expect(addDays("2026-10-31", 1)).toBe("2026-11-01");
    expect(weekStart("2026-10-14", 3)).toBe("2026-10-12");
  });
  it("builds RFC3339 at local midnight with the zone's offset", () => {
    expect(isoAtLocal("2026-10-12", "00:00", tz)).toBe("2026-10-12T00:00:00-06:00");
    expect(isoAtLocal("2026-12-12", "00:00", tz)).toBe("2026-12-12T00:00:00-07:00");
  });
});
