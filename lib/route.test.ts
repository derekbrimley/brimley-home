import { describe, expect, it } from "vitest";
import { subPath } from "./route";

describe("subPath", () => {
  it("returns the segments after the prefix", () => {
    expect(subPath("/api/music/play", "/api/music")).toEqual(["play"]);
    expect(subPath("/api/jobs/bounty/claim", "/api/jobs")).toEqual(["bounty", "claim"]);
    expect(subPath("/api/cards/12?x=1", "/api/cards")).toEqual(["12"]);
  });
  it("is empty for the bare route and its index rewrite", () => {
    expect(subPath("/api/music", "/api/music")).toEqual([]);
    expect(subPath("/api/music/", "/api/music")).toEqual([]);
    expect(subPath("/api/music/index", "/api/music")).toEqual([]);
    expect(subPath("/api/cards?shelves=8", "/api/cards")).toEqual([]);
  });
  it("ignores URLs outside the prefix", () => {
    expect(subPath("/api/musicals/play", "/api/music")).toEqual([]);
    expect(subPath(undefined, "/api/music")).toEqual([]);
  });
});
