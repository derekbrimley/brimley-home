// The school lunch menu Zo posts each week. Zo sends dates and menus; the card
// it becomes is shown on that Monday only (the footer is free the rest of the week).
import { addDays, localWeekday, zonedMidnight } from "./time";

export interface LunchDay {
  day: string;   // "Mon"
  date: string;  // YYYY-MM-DD
  menu: string;
}

export interface LunchWeek {
  weekOf: string; // the Monday, YYYY-MM-DD
  days: LunchDay[];
}

const YMD = /^\d{4}-\d{2}-\d{2}$/;
const DAY_NAMES = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function isDate(s: unknown): s is string {
  return typeof s === "string" && YMD.test(s) && addDays(s, 0) === s;
}

// weekday of a calendar date, 1 = Monday ... 7 = Sunday
function weekdayOf(ymd: string): number {
  return localWeekday(new Date(`${ymd}T12:00:00Z`), "UTC");
}

// Validate Zo's payload: { week_of, days: [{ date, menu }] }. Returns the week or an error.
export function parseLunchWeek(body: unknown): LunchWeek | { error: string } {
  if (!body || typeof body !== "object") return { error: "body must be a JSON object" };
  const b = body as Record<string, unknown>;
  if (!isDate(b.week_of)) return { error: "week_of must be a YYYY-MM-DD date" };
  if (weekdayOf(b.week_of) !== 1) return { error: "week_of must be a Monday" };
  if (!Array.isArray(b.days) || b.days.length === 0) return { error: "days must be a non-empty array" };
  if (b.days.length > 5) return { error: "days has more than five entries" };

  const weekOf = b.week_of;
  const friday = addDays(weekOf, 4);
  const days: LunchDay[] = [];
  for (const [i, d] of b.days.entries()) {
    const o = (d ?? {}) as Record<string, unknown>;
    if (!isDate(o.date) || o.date < weekOf || o.date > friday) return { error: `days[${i}].date must be a weekday in the week of ${weekOf}` };
    if (typeof o.menu !== "string" || !o.menu.trim()) return { error: `days[${i}].menu is required` };
    if (days.some((x) => x.date === o.date)) return { error: `days[${i}].date is repeated` };
    days.push({ day: DAY_NAMES[weekdayOf(o.date) - 1], date: o.date, menu: o.menu.trim() });
  }
  days.sort((a, b) => a.date.localeCompare(b.date));
  return { weekOf, days };
}

// The card row for a week: shown from that Monday's midnight to the next, in the home timezone.
export function lunchCard(week: LunchWeek, tz: string) {
  return {
    kind: "lunch_menu",
    title: "School lunch",
    body: null,
    data: { week_of: week.weekOf, days: week.days },
    show_from: zonedMidnight(week.weekOf, tz).toISOString(),
    show_until: new Date(zonedMidnight(addDays(week.weekOf, 1), tz).getTime() - 1000).toISOString(),
    priority: 0,
  };
}
