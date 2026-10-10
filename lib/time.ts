// Date helpers that respect the household timezone without a date library.

export function localDateString(d: Date, tz: string): string {
  // en-CA gives YYYY-MM-DD
  return new Intl.DateTimeFormat("en-CA", { timeZone: tz, year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}

export function localWeekday(d: Date, tz: string): number {
  // 1 = Monday ... 7 = Sunday
  const name = new Intl.DateTimeFormat("en-US", { timeZone: tz, weekday: "short" }).format(d);
  return ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(name) + 1;
}

export function addDays(ymd: string, days: number): string {
  const [y, m, d] = ymd.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + days));
  return dt.toISOString().slice(0, 10);
}

// Monday of the week containing ymd (weekday 1..7)
export function weekStart(ymd: string, weekday: number): string {
  return addDays(ymd, -(weekday - 1));
}

// The instant of 00:00 on ymd in tz.
export function zonedMidnight(ymd: string, tz: string): Date {
  const [y, m, d] = ymd.split("-").map(Number);
  const utc = Date.UTC(y, m - 1, d);
  // tz's offset at a given instant, in ms (local wall clock minus UTC)
  const offset = (t: number) => {
    const p = Object.fromEntries(
      new Intl.DateTimeFormat("en-US", {
        timeZone: tz, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit", second: "2-digit",
      }).formatToParts(new Date(t)).map((x) => [x.type, Number(x.value)])
    );
    return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - t;
  };
  const guess = utc - offset(utc);
  return new Date(utc - offset(guess));
}

export function localHour(d: Date, tz: string): number {
  const h = new Intl.DateTimeFormat("en-US", { timeZone: tz, hour: "numeric", hour12: false }).format(d);
  return Number(h) % 24;
}
