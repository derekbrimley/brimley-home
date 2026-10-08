import { describe, expect, it } from "vitest";
import { buildWeather, kidLine } from "./weather";

describe("kidLine", () => {
  it("uses the high when there is one", () => {
    expect(kidLine(48, 61, "cloud")).toBe("jacket weather");
    expect(kidLine(48, null, "cloud")).toBe("coat weather");
  });
  it("lets precipitation win", () => {
    expect(kidLine(70, 75, "rain")).toBe("raincoat and boots");
    expect(kidLine(28, 30, "snow")).toBe("snow gear");
  });
});

describe("buildWeather", () => {
  it("mentions a change later in the day", () => {
    const w = buildWeather({ temperature_2m: 47.6, weather_code: 3 }, { temperature_2m_max: [61.2], weather_code: [1] });
    expect(w).toEqual({ tempF: 48, highF: 61, summary: "cloudy, mostly sunny later", kidLine: "jacket weather", icon: "cloud" });
  });
});
