import { describe, expect, it } from "vitest";
import { pickDevice, toPlaying, toShelves } from "./crate";

describe("toPlaying", () => {
  it("prefers the album over the track for the title", () => {
    const p = toPlaying({
      playing: { uri: "spotify:track:1", name: "California", artist: "Joni Mitchell", image_url: "t.jpg", duration: 228000, position: 134000, paused: false,
        album: { id: "a", name: "Blue", artist: "Joni Mitchell", image_url: "a.jpg", uri: "spotify:album:a" } },
      device: { id: "d", name: "Kitchen", type: "Speaker", volume_percent: 40 },
    });
    expect(p).toEqual({ target: "kitchen", title: "Blue", subtitle: "Joni Mitchell · California", imageUrl: "a.jpg", isPlaying: true, positionMs: 134000, durationMs: 228000 });
  });
  it("returns null when nothing is playing", () => {
    expect(toPlaying({ playing: null, device: null })).toBeNull();
  });
});

describe("toShelves", () => {
  it("orders by crate position and drops items with no uri", () => {
    const shelves = toShelves({
      crates: [
        { id: "new", items: [{ id: 2, title: "Pink Moon", creator: "Nick Drake", image_url: null, external_uri: "spotify:album:p", media_type: "album" }] },
        { id: "fav", items: [
          { id: 1, title: "Blue", creator: "Joni Mitchell", image_url: "b.jpg", external_uri: "spotify:album:b", media_type: "album" },
          { id: 3, title: "Broken", creator: "?", image_url: null, external_uri: null, media_type: "album" },
        ] },
      ],
      _config: { crates: [{ id: "fav", name: "Favorites", position: 0 }, { id: "new", name: "Something new", position: 1 }] },
    });
    expect(shelves.map((s) => [s.name, s.items.map((i) => i.title)])).toEqual([["Favorites", ["Blue"]], ["Something new", ["Pink Moon"]]]);
  });
});

describe("pickDevice", () => {
  const devices = [
    { id: "w", name: "Web Player (Chrome)", type: "Computer", is_active: true },
    { id: "k", name: "Kitchen Speaker", type: "Speaker", is_active: false },
    { id: "p", name: "Derek's Phone", type: "Smartphone", is_active: false },
  ];
  it("finds the kitchen by name and never picks a web player", () => {
    expect(pickDevice(devices, "kitchen")?.id).toBe("k");
    expect(pickDevice(devices, undefined)).toBeNull();
  });
  it("falls back to the active real device", () => {
    expect(pickDevice([devices[1], { ...devices[2], is_active: true }], "garage")?.id).toBe("p");
  });
});
