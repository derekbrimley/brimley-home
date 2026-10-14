import { describe, expect, it } from "vitest";
import { buildSeasons, type JWEpisode, type TmdbStills } from "./episodes";

const offer = (url: string, technicalName = "disneyplus", monetizationType = "FLATRATE") => ({ monetizationType, standardWebURL: url, package: { technicalName } });
const ep = (season: number | null, episode: number | null, url: string | null, title: string | null = null): JWEpisode =>
  ({ season, episode, title, offers: url ? [offer(url)] : [] });

describe("buildSeasons", () => {
  it("groups episodes by season in order, each with its own link", () => {
    const seasons = buildSeasons([ep(2, 1, "https://d/s2e1", "Dance Mode"), ep(1, 2, "https://d/s1e2"), ep(1, 1, "https://d/s1e1", "Magic Xylophone")], "disneyplus");
    expect(seasons).toEqual([
      { number: 1, episodes: [
        { number: 1, title: "Magic Xylophone", link: "https://d/s1e1", stillUrl: null },
        { number: 2, title: "Episode 2", link: "https://d/s1e2", stillUrl: null },
      ] },
      { number: 2, episodes: [{ number: 1, title: "Dance Mode", link: "https://d/s2e1", stillUrl: null }] },
    ]);
  });

  it("drops links several episodes share: that's the show's page, not an episode", () => {
    const seasons = buildSeasons([ep(1, 1, "https://p/show"), ep(1, 2, "https://p/show"), ep(2, 1, "https://p/s2e1")], "disneyplus");
    expect(seasons.map((s) => s.number)).toEqual([2]);
  });

  it("skips specials, episodes off our service and missing numbers", () => {
    const other = { season: 1, episode: 3, title: null, offers: [offer("https://n/x", "netflix")] };
    const rent = { season: 1, episode: 4, title: null, offers: [offer("https://d/rent", "disneyplus", "BUY")] };
    const seasons = buildSeasons([ep(0, 1, "https://d/special"), ep(1, null, "https://d/odd"), ep(1, 1, "https://d/s1e1"), other, rent], "disneyplus");
    expect(seasons).toEqual([{ number: 1, episodes: [{ number: 1, title: "Episode 1", link: "https://d/s1e1", stillUrl: null }] }]);
  });

  it("takes TMDB's still, and its name when JustWatch has none", () => {
    const stills: TmdbStills = new Map([[1, new Map([[1, { name: "Magic Xylophone", still: "/abc.jpg" }]])]]);
    const [s] = buildSeasons([ep(1, 1, "https://d/s1e1")], "disneyplus", stills);
    expect(s.episodes[0]).toEqual({ number: 1, title: "Magic Xylophone", link: "https://d/s1e1", stillUrl: "https://image.tmdb.org/t/p/w300/abc.jpg" });
  });

  it("keeps the first copy of a duplicated episode", () => {
    const [s] = buildSeasons([ep(1, 1, "https://d/a", "First"), ep(1, 1, "https://d/b", "Again")], "disneyplus");
    expect(s.episodes).toEqual([{ number: 1, title: "First", link: "https://d/a", stillUrl: null }]);
  });
});
