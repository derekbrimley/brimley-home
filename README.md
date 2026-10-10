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
| `GET /api/cron/sync` | Daily: re-read the sheet and re-resolve every title |

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
```

Without those the app runs on sample data (the Monday from the mockups), which is
handy for looking at the layout on the device.

On the DC-1: install the APK, open it once, and when Android asks, make it the Home
app so it comes back after a reboot. For a true kiosk (Home and Back do nothing),
set it as device owner over adb before adding any Google account to the tablet:

```
adb shell dpm set-device-owner home.brimley/.DeviceAdmin
```

Build status: the backend typechecks and its tests pass; `./gradlew assembleDebug`
builds a debug APK. It has not yet been run on a DC-1.

## Where things stand

See `docs/PROPOSAL.md` for the plan and the milestones. Done: the app shell with the
Woodcut look, the home screen, the stylus note, the Watch picker, the backend for
calendar, weather, jobs, bounties, cards and the sheet-driven catalog, music
through Crate (shelves, play on the kitchen speaker, transport), and the Monday
lunch menu from Zo. Not yet: sending a
title to the TV (milestone 5), Yoto and "Our makes" (milestone 6).

Music setup: in Crate, open the profile menu, Household Tokens, create one named
"Kitchen tablet", and put it in `CRATE_TOKEN`. Set `CRATE_API_URL` to Crate's address
and `KITCHEN_DEVICE_NAME` to part of the kitchen speaker's name in Spotify.
