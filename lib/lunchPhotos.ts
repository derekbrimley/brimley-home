// Photos for the lunch menu, from the district's menu page (Granite's elementary lunch
// calendar). The page is WordPress: every dish on the month's calendar has a picture
// whose alt text is the dish's name. Zo sends menu text that is close to those names
// but not the same ("Hot Dog or Chili Dog with Tater Tots"), so a day gets the picture
// whose name covers most of the words in its menu, and none when nothing is close.
import type { LunchDay } from "./lunch";

export const DEFAULT_LUNCH_MENU_URL =
  "https://www.graniteschools.org/nutritionservices/school-menus/elementary-lunch-menu/";

export interface MenuPhoto {
  url: string;
  words: Set<string>; // from the alt text and the file name
}

const STOP = new Set([
  "a", "an", "and", "or", "with", "w", "the", "of", "on", "in", "elem", "elementary",
  "images", "image", "for", "website", "wg", "png", "jpg", "jpeg", "webp", "screenshot",
]);

// Words that name the dish: lowercase, no filler, plurals folded ("tots" = "tot").
export function dishWords(text: string): Set<string> {
  const out = new Set<string>();
  for (const raw of text.toLowerCase().split(/[^a-z]+/)) {
    if (raw.length < 2 || STOP.has(raw)) continue;
    out.add(raw.length > 3 && raw.endsWith("s") && !raw.endsWith("ss") ? raw.slice(0, -1) : raw);
  }
  return out;
}

function attr(tag: string, name: string): string | null {
  const m = tag.match(new RegExp(`\\s${name}\\s*=\\s*("([^"]*)"|'([^']*)')`, "i"));
  return m ? (m[2] ?? m[3] ?? "") : null;
}

function decode(s: string): string {
  return s
    .replace(/&amp;/g, "&").replace(/&#0?38;/g, "&").replace(/&quot;/g, '"')
    .replace(/&#0?39;|&#8217;|&rsquo;/g, "'").replace(/&nbsp;/g, " ");
}

// The dish pictures on the page. Each meal's pop-up has a big "meal-hero-image" (the
// plated dish); the rest are 48 px thumbnails of its parts ("Hot Dog Bun", "Doritos"),
// which are only used if the page has no hero images at all. Thumbnails are taken at
// full size (WordPress names them -150x150).
export function menuPhotos(html: string, pageUrl: string): MenuPhoto[] {
  const tags = html.match(/<img\b[^>]*>/gi) ?? [];
  const heroes = tags.filter((t) => /\bmeal-hero-image\b/i.test(attr(t, "class") ?? ""));
  const seen = new Map<string, MenuPhoto>();
  for (const tag of heroes.length ? heroes : tags) {
    const src = attr(tag, "data-lazy-src") ?? attr(tag, "data-src") ?? attr(tag, "src");
    if (!src || src.startsWith("data:")) continue;
    let url: URL;
    try {
      url = new URL(decode(src), pageUrl);
    } catch {
      continue;
    }
    if (url.protocol !== "https:" || !/\.(png|jpe?g|webp)$/i.test(url.pathname)) continue;
    url.pathname = url.pathname.replace(/-150x150(\.\w+)$/, "$1");
    const file = decodeURIComponent(url.pathname.split("/").pop() ?? "").replace(/\.\w+$/, "").replace(/-\d+$/, "");
    const words = dishWords(`${decode(attr(tag, "alt") ?? "")} ${file}`);
    if (words.size === 0) continue;
    const prev = seen.get(url.href);
    if (prev) words.forEach((w) => prev.words.add(w));
    else seen.set(url.href, { url: url.href, words });
  }
  return [...seen.values()];
}

// The menu without its extras: "(celebration meal, + scratch-made dessert)", "+ dessert".
function mainDish(menu: string): string {
  return menu.replace(/\([^)]*\)/g, " ").split("+")[0];
}

// The picture whose name covers the most of this menu's words; ties go to the
// picture with the fewest other words. At least 60% of the menu's words must match.
export function matchPhoto(menu: string, photos: MenuPhoto[]): string | undefined {
  const want = dishWords(mainDish(menu));
  if (want.size === 0) return undefined;
  let best: { url: string; score: number; extra: number } | undefined;
  for (const p of photos) {
    let hit = 0;
    want.forEach((w) => { if (p.words.has(w)) hit++; });
    const score = hit / want.size;
    const extra = p.words.size - hit;
    if (score < 0.6) continue;
    if (!best || score > best.score || (score === best.score && extra < best.extra)) best = { url: p.url, score, extra };
  }
  return best?.url;
}

// Fill in a photo for each day that doesn't already have one. Never throws: a page
// that is down or has changed just leaves the days without photos.
export async function addLunchPhotos(
  days: LunchDay[],
  pageUrl: string = process.env.LUNCH_MENU_URL || DEFAULT_LUNCH_MENU_URL,
  fetchImpl: typeof fetch = fetch,
): Promise<LunchDay[]> {
  if (pageUrl === "off" || days.every((d) => d.photo)) return days;
  try {
    const res = await fetchImpl(pageUrl, { signal: AbortSignal.timeout(6000), headers: { "User-Agent": "brimley-home" } });
    if (!res.ok) return days;
    const photos = menuPhotos(await res.text(), pageUrl);
    return days.map((d) => {
      if (d.photo) return d;
      const photo = matchPhoto(d.menu, photos);
      return photo ? { ...d, photo } : d;
    });
  } catch {
    return days;
  }
}
