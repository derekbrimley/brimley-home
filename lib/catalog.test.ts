import { describe, expect, it } from "vitest";
import { bestTmdbResult, pickOffer, pickService, toCatalogItems, youtubeId, type CatalogRow } from "./catalog";

describe("youtubeId", () => {
  it("reads ids from the usual link shapes", () => {
    expect(youtubeId("https://www.youtube.com/watch?v=hFZFjoX2cGg&t=10")).toBe("hFZFjoX2cGg");
    expect(youtubeId("https://youtu.be/hFZFjoX2cGg")).toBe("hFZFjoX2cGg");
    expect(youtubeId("https://www.youtube.com/shorts/hFZFjoX2cGg")).toBe("hFZFjoX2cGg");
    expect(youtubeId("hFZFjoX2cGg")).toBe("hFZFjoX2cGg");
    expect(youtubeId("Backyard squirrel maze")).toBeNull();
  });
});

describe("pickService", () => {
  it("prefers the sheet's order among services that carry the title", () => {
    expect(pickService(["Netflix", "Disney Plus"], ["disneyplus", "max", "netflix"])).toBe("disneyplus");
    expect(pickService(["Hulu"], ["disneyplus", "max"])).toBeNull();
  });
});

describe("pickOffer", () => {
  it("returns the subscription offer's page for the chosen service", () => {
    const offers = [
      { monetizationType: "BUY", standardWebURL: "https://buy", package: { technicalName: "amazon" } },
      { monetizationType: "FLATRATE", standardWebURL: "https://play.max.com/movie/abc", package: { technicalName: "max" } },
      { monetizationType: "FLATRATE", standardWebURL: "https://www.netflix.com/title/1", package: { technicalName: "netflix" } },
    ];
    expect(pickOffer(offers, "max")).toBe("https://play.max.com/movie/abc");
    expect(pickOffer(offers, "disneyplus")).toBeNull();
  });
});

describe("bestTmdbResult", () => {
  it("prefers an exact title on the wanted shelf", () => {
    const results = [
      { id: 1, media_type: "tv" as const, name: "Bluey", popularity: 50 },
      { id: 2, media_type: "movie" as const, title: "Bluey: The Movie", popularity: 90 },
      { id: 3, media_type: "person" as const, name: "Bluey Someone" },
    ];
    expect(bestTmdbResult(results, "shows", "Bluey")?.id).toBe(1);
    expect(bestTmdbResult(results, "movies", "Bluey")?.id).toBe(2);
  });
});

describe("toCatalogItems", () => {
  it("hides titles that resolved to nothing or to an unpaid service", () => {
    const rows: CatalogRow[] = [
      { source_key: "a", shelf: "shows", title: "Bluey", service: "disneyplus", link: "l", poster_url: null, year: null, runtime_minutes: null, resolved_at: "x", sheet_row: 3 },
      { source_key: "b", shelf: "shows", title: "Puffin Rock", service: null, link: null, poster_url: null, year: null, runtime_minutes: null, resolved_at: "x", sheet_row: 2 },
      { source_key: "c", shelf: "shows", title: "Hulu thing", service: "hulu", link: null, poster_url: null, year: null, runtime_minutes: null, resolved_at: "x", sheet_row: 4 },
      { source_key: "d", shelf: "videos", title: "Squirrels", service: "youtube", link: "y", poster_url: null, year: null, runtime_minutes: null, resolved_at: "x", sheet_row: 5 },
    ];
    const items = toCatalogItems(rows, ["disneyplus"]);
    expect(items.map((i) => [i.title, i.serviceLabel])).toEqual([["Bluey", "Disney+"], ["Squirrels", "YouTube"]]);
  });
});
