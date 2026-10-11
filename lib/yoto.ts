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
  if (!res.ok) throw new Error(`Yoto ${res.status}`);
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
