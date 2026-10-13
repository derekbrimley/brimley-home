import type { MakeRow } from "./sheet";
import type { Make } from "./types";

// Parents paste Google Drive share links into the Makes tab. Those open Drive's
// viewer, not an image, so rewrite them to Drive's thumbnail URL (the file must
// be shared "anyone with the link"). Anything else passes through.
const DRIVE_HOSTS = new Set(["drive.google.com", "docs.google.com"]);
const DRIVE_ID = /^[A-Za-z0-9_-]{10,}$/;

export function normalizeImageUrl(raw: string | null | undefined): string | null {
  const s = (raw ?? "").trim();
  if (!s) return null;
  let url: URL;
  try {
    url = new URL(s);
  } catch {
    return null;
  }
  if (!DRIVE_HOSTS.has(url.hostname)) return s;
  const fromPath = url.pathname.match(/\/file\/d\/([^/]+)/)?.[1];
  const id = fromPath ?? url.searchParams.get("id");
  if (!id || !DRIVE_ID.test(id)) return s;
  return `https://drive.google.com/thumbnail?id=${id}&sz=w800`;
}

export function toMakes(rows: MakeRow[]): Make[] {
  return rows.map((m) => ({
    title: m.title,
    who: m.who || null,
    link: m.link?.trim() || null,
    imageUrl: normalizeImageUrl(m.imageUrl),
  }));
}
