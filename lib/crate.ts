// Music comes from Crate. Until Crate has a household token (milestone 3), this
// returns nothing and the Playing card shows "nothing playing".
import type { Playing } from "./types";

export async function fetchKitchenPlaying(): Promise<Playing | null> {
  const base = process.env.CRATE_API_URL;
  const token = process.env.CRATE_TOKEN;
  if (!base || !token) return null;
  const res = await fetch(`${base.replace(/\/$/, "")}/api/spotify/state`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) return null;
  const s = (await res.json()) as {
    is_playing?: boolean;
    progress_ms?: number;
    item?: { name?: string; duration_ms?: number; album?: { name?: string; images?: { url: string }[] }; artists?: { name: string }[] };
  };
  if (!s.item) return null;
  return {
    target: "kitchen",
    title: s.item.album?.name ?? s.item.name ?? "",
    subtitle: [s.item.artists?.map((a) => a.name).join(", "), s.item.name].filter(Boolean).join(" · ") || null,
    imageUrl: s.item.album?.images?.[0]?.url ?? null,
    isPlaying: Boolean(s.is_playing),
    positionMs: s.progress_ms ?? null,
    durationMs: s.item.duration_ms ?? null,
  };
}
