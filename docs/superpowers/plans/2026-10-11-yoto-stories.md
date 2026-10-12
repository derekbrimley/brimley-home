# Step 6: Stories from the Yoto — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Stories tab lists the family's Yoto cards. A tap starts one on the Yoto player or on the tablet's speaker. The Playing card shows the Yoto with play/pause and stop.

**Architecture:** The backend owns Yoto. `lib/yoto.ts` holds the OAuth refresh-token chain in `kv`, caches the card library, reads the player's REST status for `/api/today`, and sends commands as one short MQTT session each over AWS IoT websockets. The pure parts (token choice, mapping, status resolution, topics) are unit-tested; the network glue is thin. The tablet gets four small API calls, a Stories grid and sheet, a play queue on the existing podcast player, Yoto controls on the Playing card and a new drawing.

**Tech Stack:** TypeScript, vitest, mqtt.js 5.15.1 (new). Kotlin 2.0, Jetpack Compose, kotlinx-serialization, OkHttp 4.12, androidx.media3 1.4.1 (all already in the app), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-11-yoto-stories-design.md`

## Global Constraints

- Gradle needs JDK 17. Every Gradle command in this plan is run as:
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home /Users/derek.brimley/personal-projects/brimley-home/android/gradlew -p /Users/derek.brimley/personal-projects/brimley-home/android <task>`. It's written below as `G <task>`.
- `package-lock.json` is currently out of step with `package.json`, so `npm ci` fails. Task 1 runs `npm install`, which brings it back in step; commit the lockfile with that task.
- Two colors only: `Ink` and `Paper` (`ui/theme`). No gray text, no gradients, no shadows. Fredoka everywhere. Tap targets ≥ 64 dp.
- Nothing on the tablet asks for typing. Yoto login happens once, on a laptop.
- No timers, no volume and no chapter choice for stories.
- `lib/types.ts` and `model/Today.kt` stay mirrors. `Playing` doesn't change shape: the server now really sends `target: "yoto"`. The story shapes (`StoryCard`, `StoryTrack`, `YotoPlayer`) live in `lib/yoto.ts` and are mirrored next to `MusicShelf` in `Today.kt`, the same way Crate's shelves are.
- Paths are relative to the repo root `/Users/derek.brimley/personal-projects/brimley-home`. Kotlin sources live in `android/app/src/main/kotlin/home/brimley/`, JVM tests in `android/app/src/test/kotlin/home/brimley/`.
- Commit after each task with the message given, ending with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Yoto rotates the refresh token.** Every refresh stores the new token before anything else uses it. If two requests refresh at once and ours loses, we re-read `kv` and use the winner's token rather than failing. Changing `YOTO_REFRESH_TOKEN` (a fresh login) starts over instead of being shadowed by a stale stored token. (Task 1 tests `pickAuth` and `toAuth`.)
2. **The Yoto is asleep** when Play is tapped. The backend checks `online` before publishing and answers with a sentence the kid can act on. The command is never sent into the void. (Task 1, `sendToYoto`.)
3. **Status lags by minutes and never says "paused".** Our own command wins while it is newer than the status. A pause survives a later status that still shows the same card. Someone pushing a different card in by hand takes over. (Task 1 tests `resolveNow`, four cases.)
4. **Signed track URLs expire.** They're fetched at tap time, never cached. A dead URL hits the existing `onPlayerError → stop()` path, so the kitchen entry disappears instead of showing silence as playing. (Task 4.)
5. **No Yoto configured** (no env vars): `/api/today` adds no warning, the cron skips Yoto, and the Stories tab shows the backend's error sentence instead of hanging. (Tasks 1 and 5.)

---

### Task 1: `lib/yoto.ts`, its tests, and the kv helpers

**Files:**
- Modify: `package.json` (+ `package-lock.json`), `lib/queries.ts`
- Create: `lib/yoto.ts`
- Test: `lib/yoto.test.ts`

**Interfaces:**
- Produces from `lib/queries.ts`: `getKv<T>(key): Promise<T | null>`, `setKv(key, value): Promise<void>`.
- Produces from `lib/yoto.ts`:
  - Types: `StoryCard`, `StoryTrack`, `YotoPlayer`, `YotoAction`.
  - Functions: `fetchStories()`, `refreshStories()`, `fetchTracks(cardId)`, `fetchYotoPlaying()`, `sendToYoto(cmd)`, `isYotoConfigured()`.
  - Pure helpers, used by the tests: `seedOf`, `pickAuth`, `needsRefresh`, `toAuth`, `toStoryCards`, `pickPlayer`, `toTracks`, `resolveNow`, `toYotoPlaying`, `commandFor`, `nextNow`.
- kv keys: `yoto_auth`, `yoto_library`, `yoto_now`.

- [ ] **Step 1: Add mqtt.** Run `npm install mqtt@5.15.1`. `package.json` gains `"mqtt": "^5.15.1"` under `dependencies`, and `package-lock.json` is rewritten in step.

- [ ] **Step 2: kv helpers.** In `lib/queries.ts`, insert directly above the `// ── catalog ──` line:

```ts
// Small JSON values (Yoto's tokens, library and last command).
export async function getKv<T>(key: string): Promise<T | null> {
  const { data } = await supabaseAdmin().from("kv").select("value").eq("key", key).maybeSingle();
  return (data?.value as T | undefined) ?? null;
}

export async function setKv(key: string, value: unknown): Promise<void> {
  await supabaseAdmin().from("kv").upsert({ key, value, updated_at: new Date().toISOString() }, { onConflict: "key" });
}

```

- [ ] **Step 3: Write the failing test** at `lib/yoto.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { commandFor, needsRefresh, nextNow, pickAuth, pickPlayer, resolveNow, seedOf, toAuth, toStoryCards, toTracks, toYotoPlaying, type StoryCard } from "./yoto";

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

describe("resolveNow / toYotoPlaying", () => {
  const cards: StoryCard[] = [{ id: "c1", title: "The Gruffalo", author: "Julia Donaldson", imageUrl: "g.jpg" }];
  const t0 = Date.parse("2026-10-11T10:00:00Z");
  it("shows the status's card as playing when we sent nothing", () => {
    expect(toYotoPlaying({ activeCard: "c1", isOnline: true, updatedAt: "2026-10-11T10:00:00Z" }, null, cards)).toEqual({
      target: "yoto", title: "The Gruffalo", subtitle: "Julia Donaldson", imageUrl: "g.jpg", isPlaying: true, positionMs: null, durationMs: null,
    });
  });
  it("is nothing when the player is offline or has no card in", () => {
    expect(resolveNow({ activeCard: "c1", isOnline: false }, null)).toBeNull();
    expect(resolveNow({ activeCard: "none", isOnline: true }, null)).toBeNull();
  });
  it("believes a command newer than the status", () => {
    expect(resolveNow({ activeCard: "none", updatedAt: "2026-10-11T10:00:00Z" }, { cardId: "c2", playing: true, at: t0 + 1000 })).toEqual({ cardId: "c2", playing: true });
    expect(resolveNow({ activeCard: "c1", updatedAt: "2026-10-11T10:00:00Z" }, { cardId: null, playing: false, at: t0 + 1000 })).toBeNull();
  });
  it("keeps our pause when a later status still shows the same card", () => {
    expect(resolveNow({ activeCard: "c1", updatedAt: "2026-10-11T10:05:00Z" }, { cardId: "c1", playing: false, at: t0 })).toEqual({ cardId: "c1", playing: false });
  });
  it("follows the status when someone put in another card since", () => {
    expect(resolveNow({ activeCard: "c3", updatedAt: "2026-10-11T10:05:00Z" }, { cardId: "c1", playing: false, at: t0 })).toEqual({ cardId: "c3", playing: true });
  });
  it("names an unknown card plainly", () => {
    expect(toYotoPlaying({ activeCard: "zz" }, null, cards)?.title).toBe("A Yoto card");
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
```

- [ ] **Step 4: Run it, expect FAIL.** `npm test -- lib/yoto.test.ts` fails with "Failed to resolve import ./yoto".

- [ ] **Step 5: Implement** `lib/yoto.ts`:

```ts
// Stories come from Yoto: the family's card library and the player's status
// over REST, commands over MQTT (AWS IoT, websockets, one short session per
// command). Yoto rotates refresh tokens, so the current one lives in kv, and
// the access token is cached beside it so refreshes stay rare.
import { createHash, randomUUID } from "crypto";
import { getKv, setKv } from "./queries";
import type { Playing } from "./types";

const API = "https://api.yotoplay.com";
const TOKEN_URL = "https://login.yotoplay.com/oauth/token";
const BROKER = "wss://aqrphjqbp3u2z-ats.iot.eu-west-2.amazonaws.com";
const AUTHORIZER = "PublicJWTAuthorizer";
const LIBRARY_STALE_MS = 6 * 60 * 60 * 1000;
const REFRESH_MARGIN_MS = 5 * 60 * 1000;

export interface StoryCard {
  id: string;
  title: string;
  author: string | null;
  imageUrl: string | null;
}

export interface StoryTrack {
  title: string;
  chapter: string | null;
  url: string;              // signed; good for a few hours, so fetched on tap
  durationSec: number | null;
}

export interface YotoPlayer { id: string; name: string; online: boolean }

// ── auth ─────────────────────────────────────────────────────────────────────

export interface YotoAuth {
  refreshToken: string;
  accessToken: string | null;
  expiresAt: number;        // ms epoch
  seed: string;             // which YOTO_REFRESH_TOKEN this chain started from
}

export function seedOf(envToken: string): string {
  return createHash("sha256").update(envToken).digest("hex").slice(0, 16);
}

// The stored (rotated) token wins until YOTO_REFRESH_TOKEN changes, which
// means someone logged in again.
export function pickAuth(stored: YotoAuth | null, envToken: string): YotoAuth {
  const seed = seedOf(envToken);
  if (stored && stored.seed === seed) return stored;
  return { refreshToken: envToken, accessToken: null, expiresAt: 0, seed };
}

export function needsRefresh(auth: YotoAuth, now: number): boolean {
  return !auth.accessToken || auth.expiresAt - now < REFRESH_MARGIN_MS;
}

interface TokenBody { access_token?: string; refresh_token?: string; expires_in?: number; error?: string }

export function toAuth(body: TokenBody, prev: YotoAuth, now: number): YotoAuth {
  return {
    refreshToken: body.refresh_token ?? prev.refreshToken,
    accessToken: body.access_token ?? null,
    expiresAt: now + (body.expires_in ?? 3600) * 1000,
    seed: prev.seed,
  };
}

function configured(): { clientId: string; refreshToken: string } | null {
  const clientId = process.env.YOTO_CLIENT_ID;
  const refreshToken = process.env.YOTO_REFRESH_TOKEN;
  if (!clientId || !refreshToken) return null;
  return { clientId, refreshToken };
}

export function isYotoConfigured(): boolean {
  return configured() !== null;
}

async function timedFetch(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(t);
  }
}

async function refresh(clientId: string, auth: YotoAuth): Promise<YotoAuth> {
  const res = await timedFetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "refresh_token", client_id: clientId, refresh_token: auth.refreshToken, audience: API }),
  }, 8000);
  const body = (await res.json().catch(() => ({}))) as TokenBody;
  if (!res.ok || !body.access_token) {
    throw new Error(body.error === "invalid_grant" ? "Yoto login expired: run scripts/yoto-login.mjs again" : `Yoto login ${res.status}`);
  }
  return toAuth(body, auth, Date.now());
}

async function accessToken(): Promise<string> {
  const c = configured();
  if (!c) throw new Error("YOTO_CLIENT_ID / YOTO_REFRESH_TOKEN not set");
  let auth = pickAuth(await getKv<YotoAuth>("yoto_auth"), c.refreshToken);
  if (!needsRefresh(auth, Date.now())) return auth.accessToken!;
  try {
    auth = await refresh(c.clientId, auth);
  } catch (e) {
    // Another request may have rotated the token a moment ago: use what it stored.
    const again = pickAuth(await getKv<YotoAuth>("yoto_auth"), c.refreshToken);
    if (again.refreshToken === auth.refreshToken) throw e;
    if (!needsRefresh(again, Date.now())) return again.accessToken!;
    auth = await refresh(c.clientId, again);
  }
  await setKv("yoto_auth", auth);
  return auth.accessToken!;
}

async function yoto<T>(path: string, token: string, timeoutMs = 8000): Promise<T> {
  const res = await timedFetch(`${API}${path}`, { headers: { Authorization: `Bearer ${token}` } }, timeoutMs);
  if (!res.ok) throw new Error(`Yoto ${res.status} on ${path.split("?")[0]}`);
  return (await res.json()) as T;
}

// ── library and player ───────────────────────────────────────────────────────

export interface RawLibrary {
  cards?: { cardId?: string; card?: { title?: string; metadata?: { author?: string; cover?: { imageL?: string } } } }[];
}

export function toStoryCards(raw: RawLibrary): StoryCard[] {
  const seen = new Map<string, StoryCard>();
  for (const item of raw.cards ?? []) {
    const id = item.cardId;
    const title = item.card?.title?.trim();
    if (!id || !title || seen.has(id)) continue;
    seen.set(id, { id, title, author: item.card?.metadata?.author?.trim() || null, imageUrl: item.card?.metadata?.cover?.imageL ?? null });
  }
  return [...seen.values()].sort((a, b) => a.title.localeCompare(b.title));
}

export interface RawDevices { devices?: { deviceId?: string; name?: string; online?: boolean }[] }

// YOTO_DEVICE_ID picks a player when the family has more than one; otherwise
// the first one that is online, else the first one.
export function pickPlayer(raw: RawDevices, wantedId: string | undefined): YotoPlayer | null {
  const all = (raw.devices ?? [])
    .filter((d) => d.deviceId)
    .map((d) => ({ id: d.deviceId!, name: d.name?.trim() || "Yoto", online: d.online === true }));
  if (wantedId) return all.find((d) => d.id === wantedId) ?? null;
  return all.find((d) => d.online) ?? all[0] ?? null;
}

interface CachedLibrary { fetchedAt: string; cards: StoryCard[]; playerId: string | null }

async function library(token: string, force = false): Promise<CachedLibrary> {
  const cached = await getKv<CachedLibrary>("yoto_library");
  if (!force && cached && Date.now() - Date.parse(cached.fetchedAt) < LIBRARY_STALE_MS) return cached;
  try {
    const [lib, devices] = await Promise.all([yoto<RawLibrary>("/card/family/library", token), yoto<RawDevices>("/device-v2/devices/mine", token)]);
    const fresh = { fetchedAt: new Date().toISOString(), cards: toStoryCards(lib), playerId: pickPlayer(devices, process.env.YOTO_DEVICE_ID)?.id ?? null };
    await setKv("yoto_library", fresh);
    return fresh;
  } catch (e) {
    if (cached) return cached;
    throw e;
  }
}

// GET /api/yoto: the cards, and whether the player is awake right now.
export async function fetchStories(): Promise<{ cards: StoryCard[]; player: YotoPlayer | null }> {
  const token = await accessToken();
  const [lib, devices] = await Promise.all([library(token), yoto<RawDevices>("/device-v2/devices/mine", token)]);
  return { cards: lib.cards, player: pickPlayer(devices, process.env.YOTO_DEVICE_ID) };
}

// Daily cron: re-read the library so new cards appear the next morning.
export async function refreshStories(): Promise<number> {
  return (await library(await accessToken(), true)).cards.length;
}

// ── tracks, for playing a card on the tablet ─────────────────────────────────

export interface RawContent {
  card?: { content?: { chapters?: { title?: string; tracks?: { title?: string; trackUrl?: string; duration?: number }[] }[] } };
}

// Only signed http(s) URLs play; "yoto:#…" placeholders mean the card wasn't
// fetched as playable and are skipped.
export function toTracks(raw: RawContent): StoryTrack[] {
  const out: StoryTrack[] = [];
  for (const ch of raw.card?.content?.chapters ?? []) {
    for (const tr of ch.tracks ?? []) {
      if (!tr.trackUrl || !/^https?:\/\//.test(tr.trackUrl)) continue;
      out.push({ title: tr.title?.trim() || ch.title?.trim() || "", chapter: ch.title?.trim() || null, url: tr.trackUrl, durationSec: typeof tr.duration === "number" ? tr.duration : null });
    }
  }
  return out;
}

export async function fetchTracks(cardId: string): Promise<StoryTrack[]> {
  const token = await accessToken();
  return toTracks(await yoto<RawContent>(`/content/${encodeURIComponent(cardId)}?playable=true&signingType=s3`, token, 10000));
}

// ── what the player is doing ─────────────────────────────────────────────────

// Written by our own commands. The REST status only refreshes every few
// minutes and never says paused, so a command we just sent beats it.
export interface YotoNow { cardId: string | null; playing: boolean; at: number }

export interface RawStatus { activeCard?: string; isOnline?: boolean; updatedAt?: string }

// What the player is on, from the newer of its status and our last command.
export function resolveNow(status: RawStatus, ours: YotoNow | null): { cardId: string; playing: boolean } | null {
  if (status.isOnline === false) return null;
  const updated = Date.parse(status.updatedAt ?? "") || 0;
  const shadowCard = status.activeCard && status.activeCard !== "none" ? status.activeCard : null;
  const now = ours && ours.at >= updated
    ? { cardId: ours.cardId, playing: ours.playing }
    : { cardId: shadowCard, playing: ours && ours.cardId === shadowCard ? ours.playing : true };
  return now.cardId ? { cardId: now.cardId, playing: now.playing } : null;
}

export function toYotoPlaying(status: RawStatus, ours: YotoNow | null, cards: StoryCard[]): Playing | null {
  const now = resolveNow(status, ours);
  if (!now) return null;
  const card = cards.find((c) => c.id === now.cardId);
  return { target: "yoto", title: card?.title ?? "A Yoto card", subtitle: card?.author ?? null, imageUrl: card?.imageUrl ?? null, isPlaying: now.playing, positionMs: null, durationMs: null };
}

export async function fetchYotoPlaying(): Promise<Playing | null> {
  if (!configured()) return null;
  const token = await accessToken();
  const lib = await library(token);
  if (!lib.playerId) return null;
  const [status, ours] = await Promise.all([
    yoto<RawStatus>(`/device-v2/${encodeURIComponent(lib.playerId)}/status`, token, 6000),
    getKv<YotoNow>("yoto_now"),
  ]);
  return toYotoPlaying(status, ours, lib.cards);
}

// ── commands ─────────────────────────────────────────────────────────────────

export type YotoAction = "pause" | "resume" | "stop";
export type YotoCommand = { action: "play"; cardId: string } | { action: YotoAction };

export function commandFor(playerId: string, cmd: YotoCommand): { topic: string; payload: string } {
  const base = `device/${playerId}/command/card`;
  if (cmd.action === "play") return { topic: `${base}/start`, payload: JSON.stringify({ uri: `https://yoto.io/${cmd.cardId}` }) };
  return { topic: `${base}/${cmd.action}`, payload: "" };
}

export function nextNow(cmd: YotoCommand, current: string | null, now: number): YotoNow {
  switch (cmd.action) {
    case "play": return { cardId: cmd.cardId, playing: true, at: now };
    case "pause": return { cardId: current, playing: false, at: now };
    case "resume": return { cardId: current, playing: true, at: now };
    case "stop": return { cardId: null, playing: false, at: now };
  }
}

// Loaded on demand so /api/today's cold start doesn't pay for the MQTT client.
async function publish(playerId: string, token: string, topic: string, payload: string): Promise<void> {
  const { connectAsync } = await import("mqtt");
  const client = await connectAsync(BROKER, {
    clientId: `BRIMLEY${playerId}${randomUUID()}`.replace(/[^A-Za-z0-9]/g, ""),
    username: `${playerId}?x-amz-customauthorizer-name=${AUTHORIZER}`,
    password: token,
    reconnectPeriod: 0,
    connectTimeout: 8000,
  });
  try {
    await client.publishAsync(topic, payload, { qos: 1 });
  } finally {
    await client.endAsync();
  }
}

export async function sendToYoto(cmd: YotoCommand): Promise<{ player: string }> {
  const token = await accessToken();
  const player = pickPlayer(await yoto<RawDevices>("/device-v2/devices/mine", token), process.env.YOTO_DEVICE_ID);
  if (!player) throw new Error("There's no Yoto player on this account");
  if (!player.online) throw new Error("The Yoto is asleep. Press its button, then try again");
  let current: string | null = null;
  if (cmd.action === "pause" || cmd.action === "resume") {
    const [status, ours] = await Promise.all([
      yoto<RawStatus>(`/device-v2/${encodeURIComponent(player.id)}/status`, token, 6000).catch(() => ({}) as RawStatus),
      getKv<YotoNow>("yoto_now"),
    ]);
    current = resolveNow(status, ours)?.cardId ?? null;
  }
  const { topic, payload } = commandFor(player.id, cmd);
  await publish(player.id, token, topic, payload);
  await setKv("yoto_now", nextNow(cmd, current, Date.now()));
  return { player: player.name };
}
```

- [ ] **Step 6: Run, expect PASS.** `npm test -- lib/yoto.test.ts` gives 20 passed. Then `npm test` (all pass) and `npm run typecheck` (no output).

- [ ] **Step 7: Commit.** `git add package.json package-lock.json lib/queries.ts lib/yoto.ts lib/yoto.test.ts` with message `Add the Yoto client: token chain in kv, library, status, MQTT commands`.

---

### Task 2: `/api/yoto`, `/api/today`, the cron, login script, env

**Files:**
- Create: `api/yoto/[[...path]].ts`, `scripts/yoto-login.mjs`
- Modify: `lib/today.ts`, `api/cron/sync.ts`, `vercel.json`, `.env.example`, `.vercelignore`

**Interfaces:**
- Consumes the Task 1 exports.
- Produces these HTTP routes, which the tablet uses in Task 3. Errors come back as `{ error }` with status 502.
  - `GET /api/yoto` → `{ cards: StoryCard[], player: YotoPlayer | null }`
  - `GET /api/yoto/cards/:id` → `{ tracks: StoryTrack[] }`
  - `POST /api/yoto/play {cardId}` → `{ player }`
  - `POST /api/yoto/control {action}` → 204

- [ ] **Step 1: The route** at `api/yoto/[[...path]].ts`:

```ts
import type { VercelRequest, VercelResponse } from "@vercel/node";
import { authenticate } from "../../lib/auth";
import { subPath } from "../../lib/route";
import { fetchStories, fetchTracks, sendToYoto, type YotoAction } from "../../lib/yoto";

// GET  /api/yoto                 { cards, player }  the family's Yoto cards; is the player awake
// GET  /api/yoto/cards/:id       { tracks }         signed audio URLs, to play a card on the tablet
// POST /api/yoto/play            { cardId }         start a card on the Yoto player
// POST /api/yoto/control         { action }         pause | resume | stop
export const config = { maxDuration: 20 };
const CARD_ID = /^[A-Za-z0-9_-]{1,40}$/;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!authenticate(req.headers.authorization)) return res.status(401).json({ error: "Unauthorized" });
  const path = subPath(req.url, "/api/yoto");
  const body = (req.body ?? {}) as Record<string, unknown>;
  res.setHeader("Cache-Control", "no-store");

  try {
    if (req.method === "GET" && path.length === 0) return res.status(200).json(await fetchStories());
    if (req.method === "GET" && path.length === 2 && path[0] === "cards") {
      if (!CARD_ID.test(path[1])) return res.status(400).json({ error: "Bad card id" });
      return res.status(200).json({ tracks: await fetchTracks(path[1]) });
    }
    if (req.method === "POST" && path.join("/") === "play") {
      const cardId = typeof body.cardId === "string" ? body.cardId : "";
      if (!CARD_ID.test(cardId)) return res.status(400).json({ error: "cardId is required" });
      return res.status(200).json(await sendToYoto({ action: "play", cardId }));
    }
    if (req.method === "POST" && path.join("/") === "control") {
      const allowed: YotoAction[] = ["pause", "resume", "stop"];
      const action = body.action as YotoAction;
      if (!allowed.includes(action)) return res.status(400).json({ error: `action must be one of ${allowed.join(", ")}` });
      await sendToYoto({ action });
      return res.status(204).end();
    }
  } catch (e) {
    return res.status(502).json({ error: (e as Error).message });
  }
  return res.status(404).json({ error: "Not found" });
}
```

- [ ] **Step 2: Bare-path rewrite.** In `vercel.json`, change the rewrite source to `"/api/:group(cards|music|yoto)"`.

- [ ] **Step 3: `/api/today`.** In `lib/today.ts`:
  - Add `import { fetchYotoPlaying } from "./yoto";` after the `./weather` import.
  - Rename the destructured `kitchen` tuple to `[weather, eventsRaw, completions, cardRows, catalogRows, kitchen, yotoPlaying]`.
  - Add `settle("yoto", fetchYotoPlaying(), warnings),` after the `crate` line.
  - Replace `playing: kitchen ? [kitchen] : [],` with `playing: [kitchen, yotoPlaying].filter((p) => p !== null),`.

- [ ] **Step 4: Cron.** In `api/cron/sync.ts`:
  - Add `import { isYotoConfigured, refreshStories } from "../../lib/yoto";`.
  - Before `const summary = {`, add:

```ts
  // New Yoto cards show up the next morning without waiting out the cache.
  let stories: number | null = null;
  if (isYotoConfigured()) {
    try { stories = await refreshStories(); } catch (e) { errors.push(`yoto: ${(e as Error).message}`); }
  }
```

  - Add `stories,` to the summary after `visible`.

- [ ] **Step 5: Login script** at `scripts/yoto-login.mjs`:

```js
// One-time Yoto login, run on a laptop: node scripts/yoto-login.mjs <client id>
// Opens Yoto's sign-in page, catches the redirect on localhost and prints the
// refresh token for YOTO_REFRESH_TOKEN. The Yoto app (yoto.dev → My apps) needs
// http://localhost:8765/callback as an allowed callback URL.
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { exec } from "node:child_process";

const clientId = process.argv[2] ?? process.env.YOTO_CLIENT_ID;
if (!clientId) {
  console.error("usage: node scripts/yoto-login.mjs <yoto client id>");
  process.exit(1);
}
const redirectUri = "http://localhost:8765/callback";
const verifier = randomBytes(32).toString("base64url");
const challenge = createHash("sha256").update(verifier).digest("base64url");
const state = randomBytes(12).toString("hex");

const authorize = new URL("https://login.yotoplay.com/authorize");
authorize.search = new URLSearchParams({
  response_type: "code",
  client_id: clientId,
  redirect_uri: redirectUri,
  scope: "openid profile offline_access family:library:view family:devices:view family:devices:control",
  audience: "https://api.yotoplay.com",
  code_challenge: challenge,
  code_challenge_method: "S256",
  state,
}).toString();

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", redirectUri);
  if (url.pathname !== "/callback") return res.writeHead(404).end();
  if (url.searchParams.get("state") !== state) return res.writeHead(400).end("State mismatch; run the script again.");
  const code = url.searchParams.get("code");
  if (!code) return res.writeHead(400).end(`Yoto said: ${url.searchParams.get("error_description") ?? url.searchParams.get("error")}`);
  const tokenRes = await fetch("https://login.yotoplay.com/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "authorization_code", client_id: clientId, code, redirect_uri: redirectUri, code_verifier: verifier }),
  });
  const body = await tokenRes.json();
  if (!body.refresh_token) {
    res.writeHead(500).end("No refresh token came back; see the terminal.");
    console.error(body);
  } else {
    res.writeHead(200, { "Content-Type": "text/plain" }).end("Signed in. You can close this tab.");
    console.log(`\nYOTO_CLIENT_ID=${clientId}\nYOTO_REFRESH_TOKEN=${body.refresh_token}\n\nPut both in Vercel's environment variables, then redeploy.`);
  }
  server.close();
});

server.listen(8765, () => {
  console.log(`Opening ${authorize}`);
  const open = process.platform === "darwin" ? "open" : process.platform === "win32" ? "start" : "xdg-open";
  exec(`${open} "${authorize}"`);
});
```

- [ ] **Step 6: Env and deploys.**
  - Add `scripts/` to `.vercelignore`.
  - Append to `.env.example`:

```bash
# Yoto (stories). Create an app at yoto.dev (My apps) with the callback
# http://localhost:8765/callback, then run: node scripts/yoto-login.mjs <client id>
YOTO_CLIENT_ID=
YOTO_REFRESH_TOKEN=
# Only if the family has more than one player (deviceId from /api/yoto)
YOTO_DEVICE_ID=
```

- [ ] **Step 7: Verify.** `npm test` (68 pass), `npm run typecheck` (no output), `node --check scripts/yoto-login.mjs`.

- [ ] **Step 8: Commit.** `git add api/yoto lib/today.ts api/cron/sync.ts vercel.json .env.example .vercelignore scripts` with message `Add /api/yoto, the Yoto entry on /api/today, the daily library refresh and the login script`.

- [ ] **Step 9: HARDWARE CHECKPOINT (Derek).**
  1. Do spec §3 setup steps 1–3, then deploy.
  2. Run `curl -H "Authorization: Bearer $TABLET_TOKEN" https://<host>/api/yoto`. Expected: the cards and `player.online`.
  3. Run `curl -X POST -H "Authorization: Bearer $TABLET_TOKEN" -H 'Content-Type: application/json' -d '{"cardId":"<an id from the list>"}' https://<host>/api/yoto/play`. Expected: the Yoto starts the card.
  4. Run `GET /api/today`. Expected: a `"yoto"` entry in `playing`.

  If something fails:
  - Login fails at Yoto's page (callback refused): stop. The fallback is the device-code flow, which is a plan change.
  - `play` returns 502 with a connection or authorization error: stop and report the text. The likely fix is the `family:devices:control` scope or a different broker host (spec, Risks).

---

### Task 3: Tablet model, API and repository

**Files:**
- Modify: `model/Today.kt`, `data/HomeApi.kt`, `data/TodayRepository.kt`, `data/SampleToday.kt`

**Interfaces:**
- Produces in `model/Today.kt`: `StoryCard(id, title, author?, imageUrl?)`, `YotoPlayer(id, name, online)`, `Stories(cards, player?)`, `StoryTrack(title, chapter?, url, durationSec?)`.
- Produces in `TodayRepository`:
  - `suspend fun stories(): Result<Stories>`
  - `suspend fun storyTracks(cardId: String): Result<List<StoryTrack>>`
  - `suspend fun playOnYoto(cardId: String): Result<Unit>`
  - `fun controlYoto(action: String)`

- [ ] **Step 1: Model.** At the end of `model/Today.kt`, add:

```kotlin
// Stories from GET /api/yoto (the family's Yoto cards) and /api/yoto/cards/{id}.
// Mirrors StoryCard / YotoPlayer / StoryTrack in lib/yoto.ts.
@Serializable
data class StoryCard(val id: String, val title: String, val author: String? = null, val imageUrl: String? = null)

@Serializable
data class YotoPlayer(val id: String, val name: String, val online: Boolean = false)

@Serializable
data class Stories(val cards: List<StoryCard> = emptyList(), val player: YotoPlayer? = null)

@Serializable
data class StoryTrack(val title: String, val chapter: String? = null, val url: String, val durationSec: Int? = null)
```

Then change the comment above `Playing` to `// target: "kitchen" | "tv" | "yoto" from the server; "podcast" (anything playing on the tablet) is added on the tablet (ui/MergePlaying.kt).`

- [ ] **Step 2: API.** In `data/HomeApi.kt`, add after `controlMusic`:

```kotlin
    suspend fun stories(): Stories = withContext(Dispatchers.IO) {
        client.newCall(request("/api/yoto").get().build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(errorMessage(body) ?: "yoto ${res.code}")
            json.decodeFromString(Stories.serializer(), body)
        }
    }

    suspend fun storyTracks(cardId: String): List<StoryTrack> = withContext(Dispatchers.IO) {
        client.newCall(request("/api/yoto/cards/$cardId").get().build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(errorMessage(body) ?: "yoto ${res.code}")
            json.decodeFromString(TracksResponse.serializer(), body).tracks
        }
    }

    suspend fun playOnYoto(cardId: String) = withContext(Dispatchers.IO) {
        post("/api/yoto/play", buildJsonObject { put("cardId", cardId) }.toString())
    }

    suspend fun controlYoto(action: String) = withContext(Dispatchers.IO) {
        post("/api/yoto/control", buildJsonObject { put("action", action) }.toString())
    }
```

Then:
  - Add `@Serializable private data class TracksResponse(val tracks: List<StoryTrack>)` next to `ShelvesResponse`.
  - Add the imports `home.brimley.model.Stories` and `home.brimley.model.StoryTrack`.

- [ ] **Step 3: Repository.** In `data/TodayRepository.kt`, add after `controlMusic`:

```kotlin
    suspend fun stories(): Result<Stories> =
        if (!api.isConfigured) Result.success(SampleToday.stories()) else runCatching { api.stories() }

    suspend fun storyTracks(cardId: String): Result<List<StoryTrack>> = runCatching { api.storyTracks(cardId) }

    // The backend records the command, so the refresh shows the Yoto at once.
    suspend fun playOnYoto(cardId: String): Result<Unit> = runCatching { api.playOnYoto(cardId); refresh() }

    fun controlYoto(action: String) {
        val today = _state.value.today ?: return
        // Optimistic, like the kitchen: the button flips (or the entry goes) under the finger.
        val updated = when (action) {
            "stop" -> today.playing.filter { it.target != "yoto" }
            else -> today.playing.map { if (it.target == "yoto") it.copy(isPlaying = action == "resume") else it }
        }
        _state.update { it.copy(today = today.copy(playing = updated)) }
        scope.launch {
            runCatching { api.controlYoto(action) }
            refresh()
        }
    }
```

Add the imports `home.brimley.model.Stories` and `home.brimley.model.StoryTrack`.

- [ ] **Step 4: Sample data.** In `data/SampleToday.kt`, add after `shelves()`:

```kotlin
    fun stories(): Stories = Stories(
        cards = listOf(
            StoryCard("s1", "The Gruffalo", "Julia Donaldson"),
            StoryCard("s2", "Winnie-the-Pooh", "A. A. Milne"),
            StoryCard("s3", "Animal Songs"),
        ),
        player = YotoPlayer("sample", "Yoto", online = true),
    )
```

Add the imports `home.brimley.model.Stories`, `home.brimley.model.StoryCard` and `home.brimley.model.YotoPlayer`.

- [ ] **Step 5: Build.** `G testDebugUnitTest assembleDebug` gives BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.** `git add android` with message `Tablet: Yoto stories in the model, API and repository`.

---

### Task 4: The story queue on the tablet's player, and the Yoto drawing

**Files:**
- Create: `audio/StoryQueue.kt`
- Modify: `audio/PodcastPlayer.kt`, `ui/Illustrations.kt`
- Test: `audio/StoryQueueTest.kt`

**Interfaces:**
- Produces: `data class StoryQueue(now: PodcastNow, urls: List<String>)`, `fun storyQueue(card: StoryCard, tracks: List<StoryTrack>): StoryQueue?`, `PodcastPlayer.playQueue(queue: StoryQueue)` and `@Composable fun YotoIllustration(modifier, ink, paper)`.

- [ ] **Step 1: Write the failing test** at `StoryQueueTest.kt`:

```kotlin
package home.brimley.audio

import home.brimley.model.StoryCard
import home.brimley.model.StoryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryQueueTest {
    private val card = StoryCard("c1", "The Gruffalo", "Julia Donaldson", "g.jpg")

    @Test fun playsTracksInOrderUnderTheCardsName() {
        val q = storyQueue(card, listOf(StoryTrack("Intro", "Chapter 1", "https://a"), StoryTrack("Two", "Chapter 2", "https://b")))!!
        assertEquals(listOf("https://a", "https://b"), q.urls)
        assertEquals(PodcastNow("The Gruffalo", "Julia Donaldson", "g.jpg", true), q.now)
    }

    @Test fun noPlayableTracksMeansNothingToPlay() {
        assertNull(storyQueue(card, emptyList()))
        assertNull(storyQueue(card, listOf(StoryTrack("x", null, " "))))
    }

    @Test fun aCardWithNoAuthorSaysYotoCard() {
        assertEquals("Yoto card", storyQueue(card.copy(author = null), listOf(StoryTrack("x", null, "https://a")))!!.now.showTitle)
    }
}
```

- [ ] **Step 2: Run, expect FAIL.** `G testDebugUnitTest --tests 'home.brimley.audio.StoryQueueTest'` fails with an unresolved reference to `storyQueue`.

- [ ] **Step 3: Implement** `audio/StoryQueue.kt`:

```kotlin
package home.brimley.audio

import home.brimley.model.StoryCard
import home.brimley.model.StoryTrack

// A Yoto card played on the tablet: its tracks in order, shown on the Playing
// card like a podcast episode (the card's title, the author under it).
data class StoryQueue(val now: PodcastNow, val urls: List<String>)

fun storyQueue(card: StoryCard, tracks: List<StoryTrack>): StoryQueue? {
    val urls = tracks.map { it.url }.filter { it.isNotBlank() }
    if (urls.isEmpty()) return null
    return StoryQueue(PodcastNow(episodeTitle = card.title, showTitle = card.author ?: "Yoto card", imageUrl = card.imageUrl, isPlaying = true), urls)
}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: `PodcastPlayer.playQueue`.** Add after `play(...)` in `audio/PodcastPlayer.kt`. `STATE_ENDED` already fires once, after the last item, so the existing `stop()` path clears the entry.

```kotlin
    // A Yoto card's tracks, one after another.
    fun playQueue(queue: StoryQueue) {
        current = queue.now
        player.setMediaItems(queue.urls.map { MediaItem.fromUri(it) })
        player.prepare()
        player.play()
        publish()
    }
```

Then change the class comment's first line to `// One player for podcast episodes and Yoto cards played here, out of whatever speaker the tablet is on.`

- [ ] **Step 6: The drawing.** Add to `ui/Illustrations.kt` after `TvIllustration`:

```kotlin
// The Yoto player: a rounded box with two knobs on top, a card standing in
// its slot, and a pixel smile on the screen.
@Composable
fun YotoIllustration(modifier: Modifier = Modifier, ink: Color = Ink, paper: Color = Paper) {
    Canvas(modifier) {
        inBox {
            val r = androidx.compose.ui.geometry.CornerRadius(3f)
            drawRoundRect(ink, topLeft = Offset(16f, 14f), size = Size(14f, 14f), cornerRadius = r)
            drawRoundRect(ink, topLeft = Offset(70f, 14f), size = Size(14f, 14f), cornerRadius = r)
            drawRoundRect(ink, topLeft = Offset(38f, 4f), size = Size(24f, 26f), cornerRadius = r)
            drawRect(paper, topLeft = Offset(42f, 9f), size = Size(16f, 3f))
            drawRoundRect(ink, topLeft = Offset(10f, 24f), size = Size(80f, 68f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f))
            drawRect(paper, topLeft = Offset(34f, 29f), size = Size(32f, 3f))
            drawRoundRect(paper, topLeft = Offset(28f, 42f), size = Size(44f, 34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            listOf(38f to 48f, 58f to 48f, 34f to 60f, 38f to 64f, 42f to 64f, 46f to 64f, 50f to 64f, 54f to 64f, 58f to 64f, 62f to 60f)
                .forEach { (x, y) -> drawRect(ink, Offset(x, y), Size(4f, 4f)) }
        }
    }
}
```

- [ ] **Step 7: Build.** `G testDebugUnitTest assembleDebug` gives BUILD SUCCESSFUL.

- [ ] **Step 8: Commit.** `git add android` with message `Play a Yoto card's tracks on the tablet; add the Yoto drawing`.

---

### Task 5: The Stories tab, the story sheet, and Yoto on the Playing card

**Files:**
- Modify: `ui/PlayScreen.kt`, `ui/cards/PlayingCard.kt`, `ui/BrimleyApp.kt`

**Interfaces:**
- Consumes everything from Tasks 3 and 4. `onControl("yoto", "pause" | "resume" | "stop")` dispatches to `repository.controlYoto`.

- [ ] **Step 1: Stories state in `PlayScreen`.** Add next to the music state:

```kotlin
    var stories by remember { mutableStateOf<Stories?>(null) }
    var storiesError by remember { mutableStateOf<String?>(null) }
    var story by remember { mutableStateOf<StoryCard?>(null) }
    var storyStatus by remember { mutableStateOf<String?>(null) }
    var storyBusy by remember { mutableStateOf(false) }
```

Then, inside the existing `LaunchedEffect(tab)`, add after the music `if`:

```kotlin
        if (tab == PlayTab.Stories && stories == null) {
            repository.stories().onSuccess { stories = it }.onFailure { storiesError = it.message ?: "Couldn't reach Yoto" }
        }
```

- [ ] **Step 2: The tab body and sheet.** Replace `PlayTab.Stories -> ComingSoon("Yoto cards arrive in milestone 6.")` with:

```kotlin
                PlayTab.Stories -> StoryShelf(stories, storiesError, onPick = { story = it; storyStatus = null })
```

Then, after the `pending?.let { … }` block (still inside the `Box`), add:

```kotlin
            story?.let { card ->
                StorySheet(
                    card, player = stories?.player, status = storyStatus, busy = storyBusy,
                    onCancel = { story = null },
                    onYoto = {
                        storyBusy = true
                        storyStatus = "Starting on the Yoto…"
                        scope.launch {
                            repository.playOnYoto(card.id)
                                .onSuccess { story = null; onHome() }
                                .onFailure { storyStatus = it.message ?: "Couldn't reach the Yoto" }
                            storyBusy = false
                        }
                    },
                    onHere = {
                        storyBusy = true
                        storyStatus = "Getting it ready…"
                        scope.launch {
                            repository.storyTracks(card.id)
                                .onSuccess { tracks ->
                                    val queue = storyQueue(card, tracks)
                                    if (queue == null) storyStatus = "This card can't play here" else { podcasts.playQueue(queue); story = null; onHome() }
                                }
                                .onFailure { storyStatus = it.message ?: "Couldn't reach Yoto" }
                            storyBusy = false
                        }
                    },
                )
            }
```

Change the comment above `PlayScreen` to `// One picker, three tabs. Music goes to Spotify on the tablet, Watch to the TV, Listen plays here, Stories to the Yoto or here.`

- [ ] **Step 3: The composables.** Add to `PlayScreen.kt` after `EpisodeSheet`:

```kotlin
@Composable
private fun StoryShelf(stories: Stories?, error: String?, onPick: (StoryCard) -> Unit) {
    when {
        stories == null && error == null -> ComingSoon("Looking for the Yoto cards…")
        stories == null -> ComingSoon(error ?: "")
        stories.cards.isEmpty() -> ComingSoon("No Yoto cards yet. Cards added in the Yoto app show up here.")
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(stories.cards, key = { it.id }) { StoryCover(it, onClick = { onPick(it) }) }
        }
    }
}

@Composable
private fun StoryCover(card: StoryCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(PaperBright)
                .border(BorderStroke(4.dp, Ink), shape),
        ) {
            if (card.imageUrl != null) {
                AsyncImage(model = card.imageUrl, contentDescription = card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                BookIllustration(Modifier.fillMaxSize().padding(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(card.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        card.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

// Where a story plays. The Yoto tile stays tappable when the player looks
// asleep: the kid may have just pressed its button, and the backend checks.
@Composable
private fun StorySheet(card: StoryCard, player: YotoPlayer?, status: String?, busy: Boolean, onCancel: () -> Unit, onYoto: () -> Unit, onHere: () -> Unit) {
    Sheet(onCancel, width = 640.dp) {
        Text(card.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        card.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp)) }
        Text(status ?: "Where should it play?", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val note = when { player == null -> "No Yoto found"; !player.online -> "Asleep: press its button"; else -> player.name }
            WhereTile("On the Yoto", note, enabled = player != null && !busy, onClick = onYoto, modifier = Modifier.weight(1f)) { YotoIllustration(it) }
            WhereTile("Here in the kitchen", "Plays on this speaker", enabled = !busy, onClick = onHere, modifier = Modifier.weight(1f)) { RecordIllustration(it) }
        }
        Spacer(Modifier.height(14.dp))
        InkButton("Not now", onClick = onCancel, filled = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WhereTile(label: String, note: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, picture: @Composable (Modifier) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 180.dp)
            .clip(shape)
            .border(BorderStroke(Rules.thin, Ink), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        picture(Modifier.size(84.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(note, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}
```

Add these imports:
  - `androidx.compose.foundation.layout.size`
  - `androidx.compose.foundation.lazy.grid.GridCells`
  - `androidx.compose.foundation.lazy.grid.LazyVerticalGrid`
  - `androidx.compose.foundation.lazy.grid.items as gridItems`. The alias is needed because the lazy-row `items` is already imported.
  - `home.brimley.audio.storyQueue`
  - `home.brimley.model.Stories`
  - `home.brimley.model.StoryCard`
  - `home.brimley.model.YotoPlayer`

- [ ] **Step 4: Playing card.** In `ui/cards/PlayingCard.kt`:
  - In `where`, replace the Yoto line with `playing.firstOrNull { it.target == "yoto" }?.let { if (it.isPlaying) "Yoto playing" else "Yoto paused" } ?: "Yoto quiet",`.
  - In `Controls`, add this branch:

```kotlin
            "yoto" -> {
                RoundButton(filled = true, onClick = { onControl("yoto", if (p.isPlaying) "pause" else "resume") }) { if (p.isPlaying) PauseIcon(it) else PlayIcon(it) }
                InkButton("Stop", onClick = { onControl("yoto", "stop") }, filled = false, modifier = Modifier.height(64.dp))
            }
```

  - In `NowPlaying`, add `} else if (p.target == "yoto") { YotoIllustration(Modifier.size(coverSize))` before the record fallback.
  - Remove the `if (p.target != "yoto")` guard around the controls, so they always show: keep the `Spacer` and the `Controls(p, onControl)` call.
  - Import `home.brimley.ui.YotoIllustration`.

- [ ] **Step 5: Dispatch.** In `ui/BrimleyApp.kt`'s `onControl`, add `"yoto" -> repository.controlYoto(action)`.

- [ ] **Step 6: Build and test.** `G testDebugUnitTest assembleDebug` gives BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 7: Commit.** `git add android` with message `Stories tab: Yoto cards play on the Yoto or here; Yoto controls on the Playing card`.

- [ ] **Step 8: HARDWARE CHECK (Derek).** Install the debug APK, then:
  1. Open Stories. Expected: the covers.
  2. Tap one and choose **On the Yoto** with the player asleep. Expected: "The Yoto is asleep…".
  3. Press the Yoto's button and tap again. Expected: it plays, and the Playing card shows it with the cover.
  4. Tap pause, play, then Stop.
  5. Tap a card and choose **Here in the kitchen**. Expected: it plays through the tablet's speaker, with −30/+30 on the kitchen entry.
  6. Push a card into the Yoto by hand. Expected: it appears on the Playing card within about 5 minutes.

---

### Task 6: Docs

**Files:**
- Modify: `README.md`, `docs/PROPOSAL.md`, `CLAUDE.md`

- [ ] **Step 1: README.** Add a section after "Music and podcasts on the tablet":

```markdown
### Stories (Yoto)

The backend talks to Yoto's cloud: the family's card library over REST, and
the player over MQTT. The tablet holds no Yoto login. Set up once:

1. At yoto.dev, sign in, then My apps → create an app with the callback
   `http://localhost:8765/callback`.
2. On a laptop: `node scripts/yoto-login.mjs <client id>`, then sign in with the
   family Yoto account.
3. Put the printed `YOTO_CLIENT_ID` and `YOTO_REFRESH_TOKEN` in Vercel and redeploy.
   If the family has more than one player, also set `YOTO_DEVICE_ID`.

Yoto replaces the refresh token on every use. The backend keeps the current one
in the `kv` table, so the env var is only the starting point. Running the login
again (and updating the env var) starts over. Stories → a cover → **On the
Yoto** or **Here in the kitchen**. A card pushed into the Yoto by hand shows on
the Playing card within a few minutes.
```

In "Where things stand", move Yoto to done, leaving `Not yet: "Our makes" (milestone 6).`

- [ ] **Step 2: PROPOSAL.** In `### Yoto`, replace the paragraph with:

> The backend uses Yoto's developer API: OAuth (a one-time browser login whose rotating refresh token lives in `kv`), the family library, the player's REST status, and MQTT commands sent as one short session each. Stories lists the cards we own; tapping one asks where to play: **the Yoto player** or **the tablet's own speaker** (signed track URLs, played with ExoPlayer). The TV is not a target for stories.

In "Build status", replace `- Not started: Yoto and Our makes (milestone 6).` with:

```markdown
- Stories (milestone 6): Yoto cards start on the Yoto player or the tablet; the Playing card shows the Yoto.
- Not started: Our makes.
```

- [ ] **Step 3: CLAUDE.md.** In "Data flow", change the source list to `…, catalog_items, Crate, and Yoto.` and add the sentence: `Yoto commands go out over MQTT from api/yoto, one short session each; its rotating refresh token lives in kv.yoto_auth.`

- [ ] **Step 4: Verify.** `npm test`, `npm run typecheck` and `G testDebugUnitTest assembleDebug` all pass.

- [ ] **Step 5: Commit.** `git add README.md docs/PROPOSAL.md CLAUDE.md` with message `Document Yoto setup and stories; mark the Yoto half of milestone 6 done`.
