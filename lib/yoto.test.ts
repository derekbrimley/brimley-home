import { describe, expect, it } from "vitest";
import { commandFor, needsRefresh, nextNow, parseEvents, pickAuth, pickPlayer, resolveYoto, trackPaths, seedOf, toAuth, toStoryCards, toTracks, toYotoPlaying, type StoryCard } from "./yoto";

describe("pickAuth", () => {
  const stored = { refreshToken: "rotated-2", accessToken: "a", expiresAt: 5_000_000, seed: seedOf("first") };
  it("keeps the rotated token while YOTO_REFRESH_TOKEN is unchanged", () => {
    expect(pickAuth(stored, "first")).toBe(stored);
  });
  it("starts over from the env token after a fresh login", () => {
    expect(pickAuth(stored, "second")).toEqual({ refreshToken: "second", accessToken: null, expiresAt: 0, seed: seedOf("second") });
  });
  it("starts from the env token when nothing is stored", () => {
    expect(pickAuth(null, "first").refreshToken).toBe("first");
  });
});

describe("needsRefresh / toAuth", () => {
  const prev = { refreshToken: "r1", accessToken: null, expiresAt: 0, seed: "s" };
  it("refreshes with no access token, or within five minutes of expiry", () => {
    expect(needsRefresh(prev, 0)).toBe(true);
    expect(needsRefresh({ ...prev, accessToken: "a", expiresAt: 10 * 60_000 }, 0)).toBe(false);
    expect(needsRefresh({ ...prev, accessToken: "a", expiresAt: 4 * 60_000 }, 0)).toBe(true);
  });
  it("keeps a rotated refresh token, or the old one when none comes back", () => {
    expect(toAuth({ access_token: "a", refresh_token: "r2", expires_in: 86400 }, prev, 1000)).toEqual({ refreshToken: "r2", accessToken: "a", expiresAt: 1000 + 86_400_000, seed: "s" });
    expect(toAuth({ access_token: "a", expires_in: 60 }, prev, 0).refreshToken).toBe("r1");
  });
});

describe("toStoryCards", () => {
  it("takes title, author and cover, drops blanks and duplicates, sorts by title", () => {
    expect(toStoryCards({ cards: [
      { cardId: "b1", card: { title: "The Gruffalo", metadata: { author: "Julia Donaldson", cover: { imageL: "g.jpg" } } } },
      { cardId: "a1", card: { title: " Animal Songs ", metadata: {} } },
      { cardId: "b1", card: { title: "The Gruffalo" } },
      { cardId: "x", card: { title: "" } },
      { card: { title: "No id" } },
    ] })).toEqual([
      { id: "a1", title: "Animal Songs", author: null, imageUrl: null },
      { id: "b1", title: "The Gruffalo", author: "Julia Donaldson", imageUrl: "g.jpg" },
    ]);
  });
  it("is empty for an empty library", () => {
    expect(toStoryCards({})).toEqual([]);
  });
});

describe("pickPlayer", () => {
  const raw = { devices: [
    { deviceId: "y1", name: "Upstairs", online: false },
    { deviceId: "y2", name: "Sylvie's Yoto", online: true },
  ] };
  it("prefers an online player", () => {
    expect(pickPlayer(raw, undefined)).toEqual({ id: "y2", name: "Sylvie's Yoto", online: true });
  });
  it("honors YOTO_DEVICE_ID even when that player is asleep", () => {
    expect(pickPlayer(raw, "y1")?.online).toBe(false);
  });
  it("falls back to the first player, and to null with none", () => {
    expect(pickPlayer({ devices: [{ deviceId: "y1", online: false }] }, undefined)?.name).toBe("Yoto");
    expect(pickPlayer({}, undefined)).toBeNull();
  });
});

describe("trackPaths", () => {
  it("tries /content first, then /card", () => {
    expect(trackPaths("5JhmI")).toEqual(["/content/5JhmI?playable=true&signingType=s3", "/card/5JhmI?playable=true&signingType=s3", "/card/5JhmI"]);
  });
});

describe("toTracks", () => {
  it("lists signed tracks in chapter order and skips yoto:# placeholders", () => {
    expect(toTracks({ card: { content: { chapters: [
      { title: "Chapter 1", tracks: [{ title: "Intro", trackUrl: "https://secure-media.yotoplay.com/a?sig=1", duration: 30 }] },
      { title: "Chapter 2", tracks: [{ trackUrl: "https://secure-media.yotoplay.com/b?sig=1" }, { title: "x", trackUrl: "yoto:#abc" }] },
    ] } } })).toEqual([
      { title: "Intro", chapter: "Chapter 1", url: "https://secure-media.yotoplay.com/a?sig=1", durationSec: 30 },
      { title: "Chapter 2", chapter: "Chapter 2", url: "https://secure-media.yotoplay.com/b?sig=1", durationSec: null },
    ]);
  });
});

describe("parseEvents", () => {
  it("reads the card, the playback state and the chapter", () => {
    expect(parseEvents('{"cardId":"c1","playbackStatus":"playing","chapterTitle":"Chapter 2","volume":8}')).toEqual({ cardId: "c1", playbackStatus: "playing", chapterTitle: "Chapter 2" });
  });
  it("ignores replies that say nothing about playback, and junk", () => {
    expect(parseEvents('{"volume":8}')).toBeNull();
    expect(parseEvents("not json")).toBeNull();
  });
});

describe("resolveYoto / toYotoPlaying", () => {
  const cards: StoryCard[] = [{ id: "c1", title: "The Gruffalo", author: "Julia Donaldson", imageUrl: "g.jpg" }];
  const t0 = 1_000_000_000;
  const live = (cardId: string | null, playbackStatus: string | null, chapterTitle: string | null = null) => ({ cardId, playbackStatus, chapterTitle });
  it("shows what the player says it is playing, with the chapter under the title", () => {
    expect(toYotoPlaying(live("c1", "playing", "Chapter 2"), null, cards, t0)).toEqual({
      target: "yoto", title: "The Gruffalo", subtitle: "Chapter 2", imageUrl: "g.jpg", isPlaying: true, positionMs: null, durationMs: null,
    });
  });
  it("shows a pause, and nothing once stopped or with no card", () => {
    expect(resolveYoto(live("c1", "paused"), null, t0)?.playing).toBe(false);
    expect(resolveYoto(live("c1", "stopped"), null, t0)).toBeNull();
    expect(resolveYoto(live("none", "playing"), null, t0)).toBeNull();
  });
  it("believes a command sent moments ago over a reply that may predate it", () => {
    expect(resolveYoto(live(null, "stopped"), { cardId: "c2", playing: true, at: t0 - 5_000 }, t0)).toEqual({ cardId: "c2", playing: true, chapter: null });
    expect(resolveYoto(live("c1", "playing"), { cardId: null, playing: false, at: t0 - 5_000 }, t0)).toBeNull();
  });
  it("follows the player once the command has settled (a card pushed in by hand)", () => {
    expect(resolveYoto(live("c3", "playing"), { cardId: "c1", playing: false, at: t0 - 60_000 }, t0)).toEqual({ cardId: "c3", playing: true, chapter: null });
  });
  it("falls back to our last command when the player doesn't answer, for a few hours", () => {
    expect(resolveYoto(null, { cardId: "c1", playing: true, at: t0 - 60_000 }, t0)?.cardId).toBe("c1");
    expect(resolveYoto(null, { cardId: "c1", playing: true, at: t0 - 4 * 3_600_000 }, t0)).toBeNull();
    expect(resolveYoto(null, null, t0)).toBeNull();
  });
  it("names an unknown card plainly", () => {
    expect(toYotoPlaying(live("zz", "playing"), null, cards, t0)?.title).toBe("A Yoto card");
  });
});

describe("commandFor / nextNow", () => {
  it("starts a card by its yoto.io uri", () => {
    expect(commandFor("y2", { action: "play", cardId: "c1" })).toEqual({ topic: "device/y2/command/card/start", payload: '{"uri":"https://yoto.io/c1"}' });
  });
  it("pauses, resumes and stops with empty payloads", () => {
    expect(commandFor("y2", { action: "pause" })).toEqual({ topic: "device/y2/command/card/pause", payload: "" });
    expect(commandFor("y2", { action: "stop" }).topic).toBe("device/y2/command/card/stop");
  });
  it("records what each command leaves the player doing", () => {
    expect(nextNow({ action: "play", cardId: "c1" }, null, 5)).toEqual({ cardId: "c1", playing: true, at: 5 });
    expect(nextNow({ action: "pause" }, "c1", 6)).toEqual({ cardId: "c1", playing: false, at: 6 });
    expect(nextNow({ action: "stop" }, "c1", 7)).toEqual({ cardId: null, playing: false, at: 7 });
  });
});

