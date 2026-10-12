# brimley-home

Family dashboard for the kitchen, built for a Daylight DC-1. A native Android app
(Kotlin + Compose) on the tablet, a small Vercel + Supabase backend, a Google Sheet
that parents edit, and Crate for music.

## Layout

```
android/      the tablet app (Gradle project; open in Android Studio)
api/          Vercel serverless functions (TypeScript)
lib/          backend logic, with unit tests (`npm test`)
supabase/     schema.sql and the master-sheet template
docs/         design rounds (open the .html files) and PROPOSAL.md
```

## Backend

```bash
cp .env.example .env.local   # fill in Supabase, Google service account, sheet id, TMDB key
npm install
npm test                      # vitest, pure logic only
npm run typecheck
npm run dev                   # vercel dev
```

Run `supabase/schema.sql` in the Supabase SQL editor, create the master sheet from
`supabase/sheet-template.md`, and share both the sheet and the family calendar with the
service account's email.

Routes (all take `Authorization: Bearer <token>` from `HOME_API_TOKENS`):

| Route | What |
|---|---|
| `GET /api/today` | Everything the home screen shows, assembled server-side |
| `POST /api/jobs/done` | `{ jobId, done }` tick or untick a job for today |
| `POST /api/jobs/bounty/claim` | `{ row }` "I did it" on a bounty; writes `waiting` into the sheet |
| `GET/POST /api/cards`, `DELETE /api/cards/:id` | Day cards |
| `POST /api/cards/lunch` | `{ week_of, days }` the week's school lunch menu, from Zo |
| `POST /api/notes` | `{ png }` or `{ text }` from the stylus note screen |
| `GET /api/music` | Crate's crates with their current picks, as shelves |
| `POST /api/music/play` | `{ uri }` start an album on the kitchen speaker through Crate |
| `POST /api/music/control` | `{ action }` resume, pause, next, previous |
| `GET /api/yoto` | The family's Yoto cards, and the player (`players` lists them all) |
| `GET /api/yoto/tracks?cardId=` | Signed track URLs, to play a card on the tablet |
| `POST /api/yoto/play` | `{ cardId }` start a card on the Yoto player |
| `POST /api/yoto/control` | `{ action }` pause, resume, stop |
| `GET /api/cron/sync` | Daily: re-read the sheet, re-resolve every title, re-read the Yoto library |

The lunch menu on Zo: the automation that reads the district's menu writes `lunch.json`,
and a background script posts it on Monday morning. The card shows only on that Monday
(midnight to midnight in `HOME_TZ`); a resend for the same week replaces it, and a bad
payload is a 400 with the reason, and nothing on the tablet.

```sh
curl -fsS -X POST "$HOME_URL/api/cards/lunch" \
  -H "Authorization: Bearer $ZO_TOKEN" -H "Content-Type: application/json" \
  --data @lunch.json
```

```json
{ "week_of": "2026-10-12",
  "days": [ { "date": "2026-10-12", "menu": "Breakfast for Lunch" },
            { "date": "2026-10-13", "menu": "Soft Tacos (chicken, taco meat, or pork)" } ] }
```

`week_of` is the Monday; each `date` is a weekday in that week (1 to 5 of them, so
holiday weeks are fine); `menu` is the text shown on that day's tile.

## Tablet app

Open `android/` in Android Studio (or run `./gradlew assembleDebug` with the Android
SDK installed). Put the backend address and the tablet token in
`android/local.properties`:

```
home.apiBase=https://home.yourdomain.com
home.apiToken=<the "tablet" secret from HOME_API_TOKENS>
home.spotifyClientId=<the Spotify developer app's client ID>   (optional)
```

Without those the app runs on sample data (the Monday from the mockups), which is
handy for looking at the layout on the device.

On the DC-1: install the APK, open it once, and when Android asks, make it the Home
app so it comes back after a reboot. For a true kiosk (Home and Back do nothing),
set it as device owner over adb before adding any Google account to the tablet:

```
adb shell dpm set-device-owner home.brimley/.DeviceAdmin
```

### The TV

The tablet talks to the Chromecast with Google TV directly over Wi-Fi (the
Android TV remote protocol, the same one the Google TV phone app uses). First
time: tap a poster on Watch → Play, or "TV not set up" on the Playing card. A
six-character code appears on the TV; tap it in. That's the only pairing; it
repeats only if the TV is reset. Debug builds have a "TV test" button on the
Watch tab that sends each title's link to the TV.

Links open the app on the title's page, so for Max and Prime Video the tablet
then presses OK for you: once for Max's "who's watching" (only when Max wasn't
already open) and once for Play. The waits are in `tv/AutoPlay.kt`; lengthen
them if a press lands before the page has loaded.

### Music and podcasts on the tablet

Albums play through Spotify on the DC-1 (`KITCHEN_DEVICE_NAME=DC-1` in Vercel),
so they come out of the tablet or the Bluetooth speaker it's paired with. The
Spotify app drops off Spotify's device list when idle; with
`home.spotifyClientId` set (a Spotify developer app with package `home.brimley`
and the APK's signing SHA1, redirect `brimley-home://spotify-callback`), the
dashboard wakes it first. Podcasts on the Listen shelf play on the tablet too.

### Stories (Yoto)

The backend talks to Yoto's cloud: the family's card library over REST, and
the player over MQTT. The tablet holds no Yoto login. Set up once:

1. At yoto.dev, sign in, then My apps → create an app with the callback
   `http://localhost:8765/callback` and the scopes `family:library:view`,
   `family:devices:view` and `family:devices:control`.
2. From the repo root on a laptop: `node scripts/yoto-login.mjs <client id>`, then
   sign in with the family Yoto account.
3. Put the printed `YOTO_CLIENT_ID` and `YOTO_REFRESH_TOKEN` in Vercel and redeploy.
   With more than one player, set `YOTO_DEVICE_ID` to an `id` from `players` in
   `GET /api/yoto`; otherwise the first online player is used.

Yoto replaces the refresh token on every use. The backend keeps the current one
in the `kv` table, so the env var is only the starting point. Running the login
again (and updating the env var) starts over. On the tablet: Stories → a cover →
**On the Yoto** or **Here in the kitchen**. A card pushed into the Yoto by hand
shows on the Playing card within a few minutes.

Build status: the backend typechecks and its tests pass; `./gradlew testDebugUnitTest
assembleDebug` (JDK 17) runs the tablet's unit tests and builds a debug APK.

## Where things stand

See `docs/PROPOSAL.md` for the plan and the milestones. Done: the app shell with the
Woodcut look, the home screen, the stylus note, the Watch picker, the backend for
calendar, weather, jobs, bounties, cards and the sheet-driven catalog, music
through Crate (shelves, play on the tablet, transport), the Monday lunch menu from
Zo, titles on the TV, podcasts on the tablet, and Yoto stories (on the Yoto or the
tablet). Not yet: "Our makes" (milestone 6).

Music setup: in Crate, open the profile menu, Household Tokens, create one named
"Kitchen tablet", and put it in `CRATE_TOKEN`. Set `CRATE_API_URL` to Crate's address
and `KITCHEN_DEVICE_NAME` to part of the tablet's name in Spotify (`DC-1`).
