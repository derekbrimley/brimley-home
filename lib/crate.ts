// Music comes from Crate, through a household token (read + play). Shapes here
// mirror Crate's api/spotify/[[...path]].ts and api/crates/index.ts (?shelves=n).
import type { Playing } from "./types";

export interface CrateShelf {
  id: string;
  name: string;
  items: CrateAlbum[];
}

export interface CrateAlbum {
  id: number;
  title: string;
  creator: string;
  imageUrl: string | null;
  uri: string;           // spotify:album:… or spotify:playlist:…
  mediaType: string;
}

interface CrateState {
  playing: {
    uri: string; name: string; artist: string; image_url: string | null;
    duration: number; position: number; paused: boolean;
    album: { id: string; name: string; artist: string; image_url: string | null; uri: string } | null;
  } | null;
  device: { id: string; name: string; type: string; volume_percent: number | null } | null;
}

interface CrateDevice { id: string; name: string; type: string; is_active: boolean }

interface CrateItem { id: number; title: string; creator: string; image_url: string | null; external_uri: string | null; media_type: string }
interface CrateShelvesResponse { shelves?: { id: string; name: string; items: CrateItem[] }[] }

function configured(): { base: string; token: string } | null {
  const base = process.env.CRATE_API_URL;
  const token = process.env.CRATE_TOKEN;
  if (!base || !token) return null;
  return { base: base.replace(/\/$/, ""), token };
}

async function crate<T>(path: string, init: RequestInit = {}, timeoutMs = 9000): Promise<T> {
  const c = configured();
  if (!c) throw new Error("CRATE_API_URL / CRATE_TOKEN not set");
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(`${c.base}/api${path}`, {
      ...init,
      headers: { Authorization: `Bearer ${c.token}`, "Content-Type": "application/json", ...(init.headers ?? {}) },
      signal: ctrl.signal,
    });
    if (!res.ok) {
      let msg = `Crate ${res.status}`;
      try { msg = ((await res.json()) as { error?: string }).error ?? msg; } catch { /* keep */ }
      throw new Error(msg);
    }
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  } finally {
    clearTimeout(t);
  }
}

export function toPlaying(state: CrateState): Playing | null {
  const p = state.playing;
  if (!p) return null;
  return {
    target: "kitchen",
    title: p.album?.name ?? p.name,
    subtitle: [p.album ? p.album.artist : p.artist, p.name].filter(Boolean).join(" · ") || null,
    imageUrl: p.album?.image_url ?? p.image_url,
    isPlaying: !p.paused,
    positionMs: p.position,
    durationMs: p.duration,
  };
}

export async function fetchKitchenPlaying(): Promise<Playing | null> {
  if (!configured()) return null;
  const state = await crate<CrateState>("/spotify/state", {}, 6000);
  return toPlaying(state);
}

// Shelves come ranked from Crate (`?shelves=n`): each crate's pool in the same
// weighted-random order its own page shows, cut to n. Items without a Spotify
// uri can't be started from here and are dropped.
export const SHELF_SIZE = 8;

export function toShelves(resp: CrateShelvesResponse): CrateShelf[] {
  return (resp.shelves ?? []).map((s) => ({
    id: s.id,
    name: s.name,
    items: s.items
      .filter((i) => i.external_uri)
      .map((i) => ({ id: i.id, title: i.title, creator: i.creator, imageUrl: i.image_url, uri: i.external_uri!, mediaType: i.media_type })),
  }));
}

export async function fetchShelves(): Promise<CrateShelf[]> {
  return toShelves(await crate<CrateShelvesResponse>(`/crates?shelves=${SHELF_SIZE}`));
}

// The kitchen speaker is found by name (KITCHEN_DEVICE_NAME, substring match,
// case-insensitive). Falls back to whatever device is active.
export function pickDevice(devices: CrateDevice[], wantedName: string | undefined): CrateDevice | null {
  const real = devices.filter((d) => !/web player/i.test(d.name));
  if (wantedName) {
    const w = wantedName.toLowerCase();
    const hit = real.find((d) => d.name.toLowerCase().includes(w));
    if (hit) return hit;
  }
  return real.find((d) => d.is_active) ?? (real.length === 1 ? real[0] : null);
}

export async function playOnKitchen(uri: string, source = "kitchen"): Promise<{ device: string }> {
  const { devices } = await crate<{ devices: CrateDevice[] }>("/spotify/devices");
  const device = pickDevice(devices, process.env.KITCHEN_DEVICE_NAME);
  if (!device) throw new Error("The kitchen speaker isn't on");
  await crate<void>("/spotify/play", { method: "PUT", body: JSON.stringify({ spotify_uri: uri, device_id: device.id, source }) }, 12000);
  return { device: device.name };
}

export type ControlAction = "resume" | "pause" | "next" | "previous";

export async function controlKitchen(action: ControlAction): Promise<void> {
  await crate<void>("/spotify/control", { method: "PUT", body: JSON.stringify({ action }) }, 8000);
}
