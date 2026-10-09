// The segments after a catch-all's prefix: subPath("/api/jobs/bounty/claim", "/api/jobs")
// is ["bounty", "claim"]. Read from the URL because Vercel doesn't fill
// req.query.path for [[...path]].ts outside Next. "index" is dropped: vercel.json
// rewrites the bare /api/cards and /api/music to it.
export function subPath(url: string | undefined, prefix: string): string[] {
  const pathname = new URL(url ?? "/", "http://local").pathname;
  if (pathname !== prefix && !pathname.startsWith(prefix + "/")) return [];
  return pathname
    .slice(prefix.length)
    .split("/")
    .filter((s) => s && s !== "index")
    .map(decodeURIComponent);
}
