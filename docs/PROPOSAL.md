# Brimley Home — proposal

A family dashboard for the kitchen, running on a Daylight DC-1. This document is the
plan behind `docs/design-directions.html`. Nothing here is built yet; it exists so the
build has a shape to argue with.

## What it is for

1. **Kitchen music.** Start and control what is playing on the kitchen Spotify device,
   choosing only from the albums and playlists already curated in Crate.
2. **The day.** The family calendar and the day's tasks, at a glance, from across the
   room. "Sylvie school 9–3", "piano at 4", "feed the cat".
3. **Day cards.** Things that only matter on certain days: the school lunch menu on
   Monday mornings (posted by the existing Zo automation), a birthday, a snow day.
4. **The TV.** A remote for the living-room Chromecast that only offers what we have
   chosen: specific shows, movies, YouTube videos, podcast episodes, grouped by the
   service they live on. Items on a service we no longer pay for disappear.
5. **Stories.** Start a Yoto card on the Yoto player.
6. Optional, each behind one button: a bounty board for paid chores, a shelf of
   family projects, a stylus "draw a note" card.

Rule for the whole thing: **nothing on the tablet ever asks for typing.** Every edit
happens from a phone. The tablet shows state and has a handful of big actions.

## Hardware constraints

| | |
|---|---|
| Device | Daylight DC-1, Android 13 (Sol:OS), 10.5" 1600×1200, 4:3 |
| Panel | Reflective grayscale LCD, 60 fps. Black is crisp, grays are fine, hue carries nothing. |
| Light | Warm amber backlight at night. |
| Input | Touch and Wacom-style stylus. |
| Mount | Counter stand or wall, powered, screen always on. |

Design consequences: pure black on paper, state shown by fill and outline rather than
color, large type (body ≥ 30 px on the device), tap targets ≥ 64 dp, no gradients
or shadows that rely on gray ramps, a night mode that is just a clock.

## Architecture

```
┌──────────────────────┐        HTTPS         ┌────────────────────────────┐
│  DC-1 tablet app     │ ───────────────────▶ │  brimley-home backend      │
│  Kotlin + Compose    │                      │  Vercel functions          │
│  kiosk / lock task   │ ◀─────────────────── │  Supabase Postgres         │
└──────┬───────────────┘   GET /api/today     └──────┬──────────┬──────────┘
       │ LAN                                         │          │
       │ Android TV remote protocol                  │          │ Crate API (play/state/control)
       ▼                                             ▼          ▼
┌──────────────┐                        ┌──────────────┐  ┌──────────────┐
│ Chromecast   │                        │ Google       │  │ Crate        │
│ (Google TV)  │                        │ Calendar API │  │ (Spotify)    │
└──────────────┘                        └──────────────┘  └──────────────┘
                                                 ▲
                                                 │ POST /api/cards (bearer token)
                                        ┌──────────────┐
                                        │ Zo automation│  (lunch menu, Mondays)
                                        └──────────────┘
```

### Tablet app

- **Kotlin, Jetpack Compose, minSdk 33.** Sideloaded APK (or Play internal testing if
  the DC-1 has Play). One activity, a `HomeScreen` and three full-screen pickers.
- **Kiosk:** `startLockTask()` with the app set as device owner via `adb dpm`, so the
  home and back buttons do nothing. `FLAG_KEEP_SCREEN_ON`. A `NightClock` composable
  replaces the dashboard between bedtime and morning.
- **Themes are data.** Each design direction is a `HomeTheme` object (type scale,
  stroke widths, corner radii, illustration set). Switching directions later is a
  one-line change, so the visual choice does not block the build.
- **Refresh:** poll `GET /api/today` every 60 s and on wake; poll Crate's
  `GET /api/spotify/state` every 5 s while something is playing (same cadence as
  `usePlayer` in Crate). No push needed.
- **Offline:** last good `/api/today` is cached on disk; the home screen never goes
  blank. Actions that fail show a one-line "Couldn't reach the TV" and retry once.

### Backend (`api/` on Vercel, same shape as Crate)

Keep to Crate's conventions: one catch-all file per area, `lib/` for shared code,
Supabase service role on the server, no in-memory caches.

| Route | Purpose |
|---|---|
| `GET /api/today` | Everything the home screen shows, assembled server-side: date, weather line, calendar events (next 24 h), tasks for today, active cards, now-playing summary, TV catalog shelves, Yoto status. One request, one cache. |
| `GET/POST/PATCH /api/tasks` | One-off tasks and recurring chores. Fields: title, assignee, due date or recurrence (`MON,WED,FRI`), amount (for bounties), done_at, approved_at. |
| `GET/POST/DELETE /api/cards` | Day cards. Fields: title, body (markdown or structured), kind (`lunch_menu`, `note`, `countdown`, `bounty`, `drawing`), show_from, show_until, priority. `POST` accepts a bearer token minted for Zo. |
| `GET/POST/PATCH /api/catalog` | TV catalog items. Fields: title, kind (`show`, `movie`, `video`, `podcast`), service (`disneyplus`, `max`, `netflix`, `youtube`, `pocketcasts`, …), deep_link, poster_url, tmdb_id, last_verified_at, available. |
| `GET/PATCH /api/subscriptions` | Which services are currently paid for. Items on an inactive service are filtered out of `/api/today`. |
| `PUT /api/tv` | Proxy for TV commands when the tablet can't reach the Chromecast directly (optional; see TV section). |
| `GET /api/lunch` | Optional pull endpoint if Zo prefers to be polled rather than to POST. |

Weather: one call to Open-Meteo (no key) cached for 30 minutes, reduced to a single
line in kid terms ("48°, jacket weather").

### Calendar

Google Calendar API, read-only, against the shared family calendar. Two options:

1. **Service account** with the family calendar shared to it. No tokens to refresh,
   no user flow. Preferred.
2. One OAuth grant from Derek's Google account, stored like Crate stores Spotify tokens.

Events are normalized to `{start, end, title, who, all_day}`. "Who" comes from a
small mapping of calendar colors or title prefixes ("Sylvie: …") to family members,
set in config.

### Music (Crate)

Crate stays the source of truth and already has everything the dashboard needs:

- `GET /api/picks/dashboard` for the crates and their current picks
- `PUT /api/spotify/play` with `source: "kitchen"` to start an album on the kitchen
  device (the pick is recorded server-side, so listening history keeps working)
- `GET /api/spotify/state` and `PUT /api/spotify/control` for the remote

One small change to Crate: a **household token**. A row in a new `household_tokens`
table, hashed, tied to a user, with scopes `read:library play control`. `lib/auth.ts`
accepts it as an alternative to a Supabase JWT and returns the same `users` row, and
the album/crate edit routes reject it. The kitchen tablet then holds a credential that
can only read and play. Until that exists, the tablet can hold a Supabase session
for Derek's account; the UI simply never exposes editing.

The dashboard's "Pick an album" page is a thin client of Crate's dashboard: the same
crates, the same picks, bigger covers.

### TV (the part that depends on hardware)

**If the Chromecast is a "Chromecast with Google TV"** (the one with a remote): it
speaks the Android TV remote protocol v2 on the home network. The tablet app can
implement it directly in Kotlin (pairing with a PIN once, then a TLS connection with
protobuf messages; the Python `androidtvremote2` library is a complete reference):

- launch a deep link: `https://www.youtube.com/watch?v=…`, `https://www.disneyplus.com/video/…`,
  `https://play.max.com/video/watch/…`, a Pocket Casts episode link, or a package name
- key presses: play/pause, back, home, volume, d-pad

That gives a per-title remote for every service in the catalog. Deep-link formats
are not officially documented and have broken briefly after Google updates; ADB over
network is the fallback (`am start -a android.intent.action.VIEW -d <link>`).

**If it is an older Chromecast dongle** (no Google TV): only Cast receivers work.
YouTube has a receiver (launch by video id), and podcasts can be streamed straight
from the show's RSS feed through the default media receiver without Pocket Casts at
all. Disney+ and Max cannot be launched to a specific title. In that case, the
curated catalog covers YouTube and podcasts, and "Disney+" / "Max" become one button
each that opens the app's home.

**Keeping the catalog honest:** a weekly job looks up each movie or show's streaming
availability through TMDB's watch-provider data (JustWatch-sourced, free API key),
compares it to `subscriptions`, and marks items `available=false` with a note when
they have moved. The admin page shows a "needs attention" list. YouTube and podcast
items are checked by fetching the URL.

### Yoto

Yoto has an official developer API (yoto.dev) with MQTT control of players: list
cards, start a card on a player, pause, volume, and live status. "Stories" lists the
cards we own and starts one on the kitchen or bedroom player. If the intent was Yoto
content *on the TV*, that is a different path and needs discussion.

### Admin

A phone-sized web app (React + Vite, same as Crate) at the same Vercel project, behind
Supabase auth: add catalog items (paste a link, it fills in title and poster), add
tasks and recurring chores, post a card, approve a bounty, toggle subscriptions,
see what the lunch automation last posted. Nothing in it needs to be pretty.

### Zo integration

Zo's site builder can publish HTTP routes and run scheduled automations. The lunch
automation gains one step: `POST https://home.brimley.../api/cards` with
`Authorization: Bearer <token>` and a body like

```json
{ "kind": "lunch_menu", "title": "School lunch this week",
  "show_from": "2026-10-12T06:00", "show_until": "2026-10-12T12:00",
  "data": { "days": [ { "day": "Mon", "menu": "Cheese pizza" }, … ] } }
```

Sylvie's choices (`PATCH /api/cards/:id` with `data.choices`) are stored on the card,
and the Monday card can optionally be re-posted back to Zo or emailed.

## Database (Supabase)

```
family_members   id, name, short_name, avatar_kind, color_key, birthday
tasks            id, title, assignee_id, due_on, recurrence, amount_cents, done_at, done_by, approved_at
cards            id, kind, title, body, data jsonb, show_from, show_until, priority, source, created_at
catalog_items    id, kind, title, service, deep_link, poster_url, tmdb_id, available, last_verified_at, notes
subscriptions    service (pk), active, note
projects         id, title, maker_id, cover_url, url, made_on
ledger           id, member_id, amount_cents, reason, task_id, created_at
api_tokens       id, name, token_hash, scopes[], created_at, last_used_at
```

RLS on, service role from the API, as in Crate.

## Build order

1. Tablet app shell with one theme, kiosk mode, night clock, fake `/api/today`.
2. Backend `/api/today` with calendar and tasks. Admin page for tasks.
3. Music: Crate household token, "Pick an album" page, now-playing tile and remote.
4. Cards, Zo lunch integration, Sylvie's lunch choices.
5. TV: remote protocol spike against the actual Chromecast, then catalog + admin + availability job.
6. Yoto, then the optional buttons (bounties, projects, draw a note).

## Open questions

- Which Chromecast model?
- Does the DC-1 have Google Play services? (Affects Cast SDK and calendar sign-in; not required.)
- Is the family calendar one shared Google calendar, or several?
- Can Zo POST to a URL on a schedule, or should the backend poll it?
- Does "Yoto" mean the Yoto player, or Yoto content on the TV?
