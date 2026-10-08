// Bearer-token auth. HOME_API_TOKENS is "name:secret,name:secret". Returns the
// token's name ("tablet", "zo") or null.
import { timingSafeEqual } from "crypto";

export function parseTokens(env: string | undefined): Map<string, string> {
  const map = new Map<string, string>();
  for (const pair of (env ?? "").split(",")) {
    const i = pair.indexOf(":");
    if (i <= 0) continue;
    const name = pair.slice(0, i).trim();
    const secret = pair.slice(i + 1).trim();
    if (name && secret) map.set(name, secret);
  }
  return map;
}

function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  if (ab.length !== bb.length) return false;
  return timingSafeEqual(ab, bb);
}

export function authenticate(
  authHeader: string | undefined,
  tokens: Map<string, string> = parseTokens(process.env.HOME_API_TOKENS)
): string | null {
  if (!authHeader?.startsWith("Bearer ")) return null;
  const presented = authHeader.slice(7).trim();
  if (!presented) return null;
  for (const [name, secret] of tokens) {
    if (safeEqual(presented, secret)) return name;
  }
  return null;
}
