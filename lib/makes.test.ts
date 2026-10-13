import { describe, expect, it } from "vitest";
import { normalizeImageUrl, toMakes } from "./makes";

const ID = "1AbCdEfGhIjKlMnOpQrStUv_-9";
const THUMB = `https://drive.google.com/thumbnail?id=${ID}&sz=w800`;

describe("normalizeImageUrl", () => {
  it("rewrites the Drive share-link forms to a thumbnail", () => {
    expect(normalizeImageUrl(`https://drive.google.com/file/d/${ID}/view?usp=sharing`)).toBe(THUMB);
    expect(normalizeImageUrl(`https://drive.google.com/open?id=${ID}`)).toBe(THUMB);
    expect(normalizeImageUrl(`https://drive.google.com/uc?export=view&id=${ID}`)).toBe(THUMB);
    expect(normalizeImageUrl(`https://docs.google.com/uc?id=${ID}`)).toBe(THUMB);
  });
  it("passes other URLs through", () => {
    expect(normalizeImageUrl("https://example.com/lego.jpg")).toBe("https://example.com/lego.jpg");
    expect(normalizeImageUrl("https://drive.google.com/drive/folders")).toBe("https://drive.google.com/drive/folders");
  });
  it("is null for blanks and junk", () => {
    expect(normalizeImageUrl("")).toBeNull();
    expect(normalizeImageUrl("  ")).toBeNull();
    expect(normalizeImageUrl(undefined)).toBeNull();
    expect(normalizeImageUrl("not a url")).toBeNull();
  });
});

describe("toMakes", () => {
  it("maps sheet rows, blanks to null", () => {
    expect(toMakes([
      { row: 2, title: "Cat Game", who: "Sylvie", link: "https://scratch.mit.edu/projects/1", imageUrl: `https://drive.google.com/file/d/${ID}/view` },
      { row: 3, title: "Lego boat", who: "", link: "", imageUrl: "" },
    ])).toEqual([
      { title: "Cat Game", who: "Sylvie", link: "https://scratch.mit.edu/projects/1", imageUrl: THUMB },
      { title: "Lego boat", who: null, link: null, imageUrl: null },
    ]);
  });
});
