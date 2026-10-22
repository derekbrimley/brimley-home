import { describe, expect, it } from "vitest";
import { orderFromTitle, toShelves, type SiteMovie } from "./homeMovies";

const movie = (slug: string, title: string, collection: string | null, updated: string): SiteMovie => ({
  slug, title, collection, updated, video: `https://site/movies/${slug}/video?k=x`, poster: null,
});

describe("orderFromTitle", () => {
  it("reads digits and words", () => {
    expect(orderFromTitle("3rd Birthday")).toBe(3);
    expect(orderFromTitle("First Birthday")).toBe(1);
    expect(orderFromTitle("Beach Day")).toBe(Number.POSITIVE_INFINITY);
  });
});

describe("toShelves", () => {
  it("makes a row per kid in birthday order, the biggest first, then loose videos newest first", () => {
    const shelves = toShelves([
      movie("sylvie-2nd", "Sylvie 2nd Birthday", "Sylvie", "2026-05-01"),
      movie("holland-3rd", "Holland 3rd Birthday", "Holland", "2026-03-01"),
      movie("christmas-2024", "Christmas 2024", null, "2025-01-02"),
      movie("holland-first", "Holland First Birthday", "Holland", "2026-04-01"),
      movie("beach", "Beach Day", null, "2026-07-04"),
    ]);
    expect(shelves.map((s) => s.name)).toEqual(["Holland", "Sylvie", "More videos"]);
    expect(shelves[0].movies.map((m) => m.id)).toEqual(["holland-first", "holland-3rd"]);
    expect(shelves[2].movies.map((m) => m.id)).toEqual(["beach", "christmas-2024"]);
    expect(shelves[0].movies[0]).toEqual({ id: "holland-first", title: "Holland First Birthday", videoUrl: "https://site/movies/holland-first/video?k=x", posterUrl: null });
  });

  it("calls loose videos Home movies when there are no kid folders", () => {
    expect(toShelves([movie("beach", "Beach Day", null, "2026-07-04")]).map((s) => s.name)).toEqual(["Home movies"]);
    expect(toShelves([])).toEqual([]);
  });
});
