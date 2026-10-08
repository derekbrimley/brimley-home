import { describe, expect, it } from "vitest";
import { normalizeService, parseAmountCents, parseDays, parseSheet, parseShelf } from "./sheet";

describe("parseDays", () => {
  it("reads lists, shorthands and words", () => {
    expect(parseDays("Mon, Wed, Fri")).toEqual([1, 3, 5]);
    expect(parseDays("M/W/F")).toEqual([1, 3, 5]);
    expect(parseDays("tues thurs")).toEqual([2, 4]);
    expect(parseDays("weekdays")).toEqual([1, 2, 3, 4, 5]);
    expect(parseDays("daily")).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(parseDays("")).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(parseDays("Saturday and Sunday")).toEqual([6, 7]);
  });
});

describe("parseShelf / amounts / services", () => {
  it("maps loose words to shelves", () => {
    expect(parseShelf("Movies")).toBe("movies");
    expect(parseShelf("tv")).toBe("shows");
    expect(parseShelf("YouTube")).toBe("videos");
    expect(parseShelf("podcast")).toBe("listen");
    expect(parseShelf("??")).toBeNull();
  });
  it("reads dollars", () => {
    expect(parseAmountCents("$1")).toBe(100);
    expect(parseAmountCents("2.50")).toBe(250);
    expect(parseAmountCents("")).toBe(0);
  });
  it("normalizes service names from sheets and TMDB", () => {
    expect(normalizeService("Disney+")).toBe("disneyplus");
    expect(normalizeService("Disney Plus")).toBe("disneyplus");
    expect(normalizeService("HBO Max")).toBe("max");
    expect(normalizeService("Max")).toBe("max");
    expect(normalizeService("Amazon Prime Video")).toBe("prime");
    expect(normalizeService("Netflix Kids")).toBe("netflix");
  });
});

describe("parseSheet", () => {
  it("turns tab grids into typed rows, skipping headers and blanks", () => {
    const data = parseSheet({
      Watch: [["Title", "Shelf", "Found on", "Link"], ["Bluey", "Shows", "Disney+", "https://x"], ["", ""], ["Kiki's Delivery Service", "movie"]],
      Listen: [["Podcast"], ["Wow in the World"]],
      Jobs: [["Kid", "Job", "Days"], ["Sylvie", "Feed Juniper", "daily"], ["Sylvie", "Piano", "Mon, Wed"]],
      Bounties: [["Job", "Amount", "Status", "Kid"], ["Vacuum the basement", "$1", "", ""], ["Rake", "2", "waiting", "Sylvie"], ["Old", "1", "Paid"]],
      Makes: [["Title", "Who", "Link", "Picture"], ["Cat Game", "Sylvie", "https://game", ""]],
      Services: [["Service"], ["Disney+"], ["Max"], ["Netflix"]],
    }, "2026-10-12T00:00:00Z");
    expect(data.watch).toEqual([
      { row: 2, title: "Bluey", shelf: "shows", foundOn: "Disney+", link: "https://x" },
      { row: 4, title: "Kiki's Delivery Service", shelf: "movies", foundOn: "", link: "" },
    ]);
    expect(data.listen[0]).toEqual({ row: 2, title: "Wow in the World", feedUrl: "" });
    expect(data.jobs[1]).toEqual({ row: 3, kid: "Sylvie", title: "Piano", days: [1, 3] });
    expect(data.bounties.map((b) => b.status)).toEqual(["open", "waiting", "paid"]);
    expect(data.bounties[0].amountCents).toBe(100);
    expect(data.makes[0].who).toBe("Sylvie");
    expect(data.services).toEqual(["disneyplus", "max", "netflix"]);
  });
});
