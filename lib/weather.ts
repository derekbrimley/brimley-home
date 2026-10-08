import type { Weather } from "./types";

// WMO weather codes -> icon + words. https://open-meteo.com/en/docs
export function describeCode(code: number): { icon: Weather["icon"]; words: string } {
  if (code === 0) return { icon: "sun", words: "sunny" };
  if (code <= 2) return { icon: "sun", words: "mostly sunny" };
  if (code === 3) return { icon: "cloud", words: "cloudy" };
  if (code <= 48) return { icon: "fog", words: "foggy" };
  if (code <= 57) return { icon: "rain", words: "drizzly" };
  if (code <= 67) return { icon: "rain", words: "rainy" };
  if (code <= 77) return { icon: "snow", words: "snowy" };
  if (code <= 82) return { icon: "rain", words: "showers" };
  if (code <= 86) return { icon: "snow", words: "snow showers" };
  return { icon: "storm", words: "stormy" };
}

// What a kid needs to know before the door.
export function kidLine(tempF: number, highF: number | null, icon: Weather["icon"]): string {
  const ref = highF ?? tempF;
  if (icon === "rain") return "raincoat and boots";
  if (icon === "snow") return "snow gear";
  if (icon === "storm") return "stay inside weather";
  if (ref < 35) return "big coat, hat and gloves";
  if (ref < 50) return "coat weather";
  if (ref < 62) return "jacket weather";
  if (ref < 75) return "no jacket";
  if (ref < 88) return "shorts weather";
  return "very hot, bring water";
}

export function buildWeather(current: { temperature_2m: number; weather_code: number }, daily: { temperature_2m_max: number[]; weather_code: number[] } | null): Weather {
  const tempF = Math.round(current.temperature_2m);
  const highF = daily?.temperature_2m_max?.[0] != null ? Math.round(daily.temperature_2m_max[0]) : null;
  const now = describeCode(current.weather_code);
  const later = daily?.weather_code?.[0] != null ? describeCode(daily.weather_code[0]) : null;
  let summary = now.words;
  if (later && later.words !== now.words) summary = `${now.words}, ${later.words} later`;
  return { tempF, highF, summary, kidLine: kidLine(tempF, highF, later?.icon ?? now.icon), icon: now.icon };
}

export async function fetchWeather(latlon: string, tz: string): Promise<Weather> {
  const [lat, lon] = latlon.split(",").map((s) => s.trim());
  const url =
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
    `&current=temperature_2m,weather_code&daily=temperature_2m_max,weather_code&temperature_unit=fahrenheit&forecast_days=1&timezone=${encodeURIComponent(tz)}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Open-Meteo ${res.status}`);
  const data = (await res.json()) as { current: { temperature_2m: number; weather_code: number }; daily?: { temperature_2m_max: number[]; weather_code: number[] } };
  return buildWeather(data.current, data.daily ?? null);
}
