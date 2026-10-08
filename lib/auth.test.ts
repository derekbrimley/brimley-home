import { describe, expect, it } from "vitest";
import { authenticate, parseTokens } from "./auth";

describe("auth", () => {
  const tokens = parseTokens("tablet:abc123,zo:def456, bad, :x, y:");
  it("parses name:secret pairs and ignores junk", () => {
    expect([...tokens.keys()]).toEqual(["tablet", "zo"]);
  });
  it("returns the token name for a valid bearer", () => {
    expect(authenticate("Bearer abc123", tokens)).toBe("tablet");
    expect(authenticate("Bearer def456", tokens)).toBe("zo");
  });
  it("rejects missing, malformed and wrong tokens", () => {
    expect(authenticate(undefined, tokens)).toBeNull();
    expect(authenticate("abc123", tokens)).toBeNull();
    expect(authenticate("Bearer nope", tokens)).toBeNull();
    expect(authenticate("Bearer ", tokens)).toBeNull();
  });
});
