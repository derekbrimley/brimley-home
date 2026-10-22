// Home movies come from the personal site (derekbrimley/personal-site), which
// lists its R2 bucket at /movies.json. HOME_MOVIES_URL is that address with the
// family key (`?k=...`), so every link in the list carries it too: the tablet
// hands `video` straight to the TV's Cast player, and it redirects to a freshly
// signed file. A folder in the bucket is one kid; loose files are "More videos".
import type { HomeMovieShelf } from "./types";

export interface SiteMovie {
  slug: string;
  title: string;
  collection: string | null;
  video: string;
  poster: string | null;
  updated: string;
}

const ORDINALS = [
  "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
  "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth",
  "nineteenth", "twentieth",
];

// The first number in a title ("3rd Birthday", "First Birthday", "Christmas 2024"),
// the same order the site uses inside a collection.
export function orderFromTitle(title: string): number {
  const m = title.toLowerCase().match(new RegExp(`\\b(\\d+|${ORDINALS.join("|")})`));
  if (!m) return Number.POSITIVE_INFINITY;
  return /^\d/.test(m[1]) ? Number(m[1]) : ORDINALS.indexOf(m[1]);
}

// One shelf per kid, most movies first (as on the site), then loose videos newest first.
export function toShelves(movies: SiteMovie[]): HomeMovieShelf[] {
  const byKid = new Map<string, SiteMovie[]>();
  const loose: SiteMovie[] = [];
  for (const m of movies) {
    if (m.collection) byKid.set(m.collection, [...(byKid.get(m.collection) ?? []), m]);
    else loose.push(m);
  }
  const item = (m: SiteMovie) => ({ id: m.slug, title: m.title, videoUrl: m.video, posterUrl: m.poster });
  const kids = [...byKid]
    .map(([name, list]) => ({
      name,
      movies: list
        .sort((a, b) => orderFromTitle(a.title) - orderFromTitle(b.title) || a.updated.localeCompare(b.updated))
        .map(item),
    }))
    .sort((a, b) => b.movies.length - a.movies.length || a.name.localeCompare(b.name));
  const more = loose.sort((a, b) => b.updated.localeCompare(a.updated)).map(item);
  return [...kids, ...(more.length ? [{ name: kids.length ? "More videos" : "Home movies", movies: more }] : [])];
}

export async function fetchHomeMovies(timeoutMs = 8000): Promise<HomeMovieShelf[]> {
  const url = process.env.HOME_MOVIES_URL;
  if (!url) return [];
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new Error(`movies.json ${res.status}`);
    return toShelves((await res.json()) as SiteMovie[]);
  } finally {
    clearTimeout(t);
  }
}
