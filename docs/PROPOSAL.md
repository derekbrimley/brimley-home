# Brimley Home — proposal

A family dashboard for the kitchen, running on a Daylight DC-1. This document is the
plan behind `docs/design-directions.html` (five directions) and
`docs/woodcut-round-2.html` (the chosen direction, Woodcut, refined). Nothing here is
built yet; it exists so the build has a shape to argue with.

## Decisions so far

- **Visual direction: Woodcut shapes, Fredoka type** (`docs/round-3.html`). Four cards
  of different sizes: Today (largest, top left), Playing (top right, one card for
  music, TV and stories), Jobs (bottom left, with the bounty row when one is posted),
  Note (bottom right, with "Draw a note" and "Our makes"). Black band on top, read-only
  day-card footer. Rounded corners, thick rules, solid-silhouette illustrations.
- **One picker.** "Play something" has three tabs: Music (Crate shelves), Watch (Shows,
  Movies, Videos, Listen), Stories (Yoto cards). The tab decides where it plays.
- **Bounty board and Our makes are in.** Bounties appear only while posted; makes live
  behind a button.
- **Hardware:** the family owns both a dongle Chromecast and a Chromecast with Google
  TV; recommendation is to put the Google TV one back (see TV). The DC-1 has the Play
  Store and Google services. One shared Google calendar. A Yoto player plus the Yoto app.
- **Zo:** will POST the lunch menu from a background script with `curl`, not from an AI
  automation.
- **All jobs done:** the Jobs tile inverts, a stamp lands, the week's star fills, a burst
  of woodcut stars crosses the screen. Weekly stars feed a Saturday "golden week" banner.

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

- **Kotlin, Jetpack Compose, minSdk 33.** Distributed through Play's internal testing
  track (the DC-1 has the Play Store), with a sideloaded APK as the fallback. One
  activity, a `HomeScreen`, three full-screen pickers (album, show, story) and the
  stylus note canvas.
- **Kiosk:** `startLockTask()` with the app set as device owner via `adb dpm`, so the
  home and back buttons do nothing. `FLAG_KEEP_SCREEN_ON`. A `NightClock` composable
  replaces the dashboard between bedtime and morning.
- **Woodcut theme.** Alfa Slab One for display, Young Serif for text, Caveat for the
  note tile, pure black on paper, 5–8 px rules, solid-silhouette illustrations as
  vector drawables. Kept as one `HomeTheme` object so a later restyle is contained.
- **Jobs and the all-done moment.** Checking the last job inverts the tile, lands an
  "ALL DONE" stamp, fills the day's star and runs a two-second burst of star shapes
  (Compose animation, ~20 shapes, no gray). Optional sound. The tile stays inverted
  until midnight. Five stars in a week turns the band into a "golden week" banner on
  Saturday.
- **Note tile.** The stylus canvas saves a monochrome bitmap to the backend
  (`cards` with kind `drawing`); the tile shows the latest. Parents can also post a
  text note from the admin page.
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

Google Calendar API, read-only, against the one shared family calendar, through a
**service account** the calendar is shared with. No OAuth, no token refresh.

Events are normalized to `{start, end, title, who, all_day}`. "Who" comes from a
title prefix ("Sylvie: piano") or a per-person keyword list in config; events with
no match are everyone's. The Today tile shows at most five lines; the rest collapse
into "and N more", which opens the full day.

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

### TV

**Recommendation: use the Chromecast with Google TV.** The reasons it was retired (the
extra remote, the kids seeing the home screen) go away once the tablet is the remote:
the physical remote stays in a drawer, and the dashboard deep-links straight into a
title so the Google TV home screen is never the path. Google TV's "Apps only mode"
hides recommendations if the home screen does appear; a kids profile limits what the
apps offer. Known gap: when an episode ends, the service's app is on screen with its
own rows. The Playing card has a "TV off" action, and a "stop after this" toggle at
start time sends it automatically when the runtime is up.

With the Google TV box the tablet uses the Android TV remote protocol (below, option
A). The rest of this section is kept for the dongle case.

The dongle Chromecast only runs Cast receiver apps. The tablet
has Play services, so it uses the official **Google Cast SDK for Android** as a
sender. That covers:

- **YouTube:** the YouTube Cast receiver, queued the way Home Assistant's YouTube
  controller does it (receiver app id plus the lounge/queue call). Curated videos
  only; the catalog holds video ids.
- **Podcasts:** the episode's audio URL from the show's RSS feed, loaded into the
  Cast default media receiver. No Pocket Casts account needed. The same episode can
  play on the tablet's own speaker instead.
- **Remote:** play, pause, seek, volume and stop for anything the tablet started.

**Disney+, Max and Netflix cannot be started on a specific title** from anything but
their own apps; their receivers only accept playback from an authenticated sender.
Two ways around it, to be chosen:

- **Option A, swap the dongle** for a Google TV Streamer (or Chromecast with Google
  TV). Those speak the Android TV remote protocol v2 on the home network: pair once
  with a PIN, then a TLS connection with protobuf messages (the Python
  `androidtvremote2` library is a complete reference). The tablet can then launch a
  deep link on any installed app (`https://www.disneyplus.com/video/…`,
  `https://play.max.com/video/watch/…`, `https://www.youtube.com/watch?v=…`) and send
  play/pause, volume, back and home. Deep-link formats are undocumented and have
  broken briefly after Google updates; ADB over network is the fallback. This is the
  only path where "tap Kiki, it plays" works end to end for the paid services.
- **Option B, keep the dongle.** Install Disney+, Max and Netflix on the DC-1.
  Tapping a Shows or Movies poster opens that app on the tablet at the title (their
  Android apps accept the web links as intents), and the kid taps the app's own Cast
  button once. Two taps, the service's app is briefly on screen, curation is softer.
  YouTube and podcasts still go straight to the TV.

The catalog schema is the same either way: `kind`, `service`, `deep_link` (or
`youtube_id` / `rss_url` + episode guid), `tmdb_id`.

**Keeping the catalog honest:** a weekly job looks up each movie or show's streaming
availability through TMDB's watch-provider data (JustWatch-sourced, free API key),
compares it to `subscriptions`, and marks items `available=false` with a note when
they have moved. The admin page shows a "needs attention" list. YouTube and podcast
items are checked by fetching the URL.

### Yoto

Yoto has an official developer API (yoto.dev): OAuth client credentials, the
library of purchased cards with chapters and audio stream URLs, and MQTT control of
players (play a card, pause, resume, volume, live status). "Stories" lists the cards
we own; tapping one asks where to play: **the Yoto player** (MQTT command) or **the
tablet's own speaker** (stream the card's chapters with ExoPlayer). The Yoto app does
not appear to cast to a Chromecast, so the TV is not a target for stories.

### Admin ("Add something")

A phone-sized web app (React + Vite, same as Crate) at the same Vercel project, Google
sign-in restricted to two allow-listed accounts. One box accepts:

- a Share link from a streaming app (Disney+, Max, Netflix, Prime, YouTube): service
  read from the domain, title and poster from TMDB or YouTube, item marked playable;
- a typed title: TMDB search gives poster, shelf and current streaming providers; the
  item is "not playable yet" until a link is pasted;
- a podcast name or RSS URL: Apple's podcast search, episodes read live from the feed.

The page also manages jobs and bounties (post, approve), text notes to the Note card,
makes (title, picture, link), the paid-services list, and a "needs attention" list
(titles that moved service, links that stopped resolving). A shared Google Sheet
could replace the catalog part with zero UI, at the cost of previews and error
reporting; the page is the recommendation.

### Zo integration

Zo recommended a background script rather than an AI automation for a fixed POST, so
the lunch job becomes: the existing automation writes the week's menu to a JSON file,
and a small looping script POSTs it Monday at 6:00 am (the shape of Zo's existing
`finance-sync` job). The backend keeps the last payload; if a Monday POST fails, the
card still shows last week's menu with a "may be out of date" line. The request:

```sh
curl -X POST https://home.brimley.../api/cards \
  -H "Authorization: Bearer $BRIMLEY_HOME_TOKEN" \
  -H "Content-Type: application/json" \
  --data @/path/to/lunch-menu.json
```

with a body like

```json
{ "kind": "lunch_menu", "title": "School lunch this week",
  "show_from": "2026-10-12T06:00", "show_until": "2026-10-12T12:00",
  "data": { "days": [ { "day": "Mon", "menu": "Cheese pizza" }, … ] } }
```

The card is read-only on the tablet; the footer shows the five days with today lifted
out. Sylvie reads it and decides; nothing is recorded.

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

## Build status (October 2026)

- Backend: `/api/today`, jobs, bounties, cards, notes, daily sync, sheet parsing,
  calendar, weather, catalog resolution. Typechecks; 30 unit tests pass.
- Tablet: app shell, Woodcut/Fredoka theme, home screen (four cards, band, footer),
  all-done stamp and star burst, Watch picker with confirmation, stylus note screen,
  night clock, boot receiver, kiosk flags. Builds a debug APK; not yet run on the
  DC-1.
- Music (milestone 3): Crate has household tokens (read + play, created from its
  profile menu). The dashboard's `/api/music` proxies Crate's crates and starts
  albums on the kitchen speaker; the Playing card has pause, next and previous.
- Not started: sending a title to the TV over the Android TV remote protocol
  (milestone 5), Yoto and Our makes (milestone 6).

## Build order

1. Tablet app shell with one theme, kiosk mode, night clock, fake `/api/today`.
2. Backend `/api/today` with calendar and tasks. Admin page for tasks.
3. Music: Crate household token, "Pick an album" page, now-playing tile and remote.
4. Cards, Zo lunch integration, Sylvie's lunch choices.
5. Watch: YouTube and podcasts to the dongle via the Cast SDK; Shows and Movies via
   option A or B; catalog admin and the weekly availability job.
6. Stories (Yoto) and the stylus note tile. Then, if wanted, bounties and projects.

## Open questions

- Confirm the Google TV box goes back on the TV.
- Round 3 layout sign-off, then the build starts with the app shell.
- One Jobs card with names, or one card per child?
