# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

A kitchen family dashboard for a Daylight DC-1 (10.5" grayscale reflective screen,
Android 13). Three parts in one repo:

- `android/` — the tablet app. Kotlin, Jetpack Compose, single activity, kiosk-style.
- `api/` + `lib/` — Vercel serverless functions and shared TypeScript logic, same shape
  as the Crate project (`derekbrimley/crate`): each file in `api/` exports a default
  `handler(req, res)`; related routes share one catch-all file (`[[...path]].ts`) to
  stay under the Hobby plan's 12-function limit.
- `supabase/` — Postgres schema. Only the service role touches it (RLS on, no policies).

## Commands

```bash
npm test             # vitest over lib/**/*.test.ts (pure logic; no network, no env)
npm run typecheck    # tsc over api/ and lib/
npm run dev          # vercel dev
cd android && ./gradlew assembleDebug   # needs the Android SDK
```

## Design rules (do not drift)

- **Two colors only.** Ink `#141413` on paper `#E8E7E2`. State is shown by fill, weight
  and outline, never by hue. No gradients, no shadows, no gray text for "secondary".
- **Fredoka** for everything you read or tap, **Caveat** only for the handwritten note.
  Thick rules (6 dp card borders, 3 dp inner rules), rounded corners (28 dp cards).
- Illustrations are solid black silhouettes with white cut-outs, drawn in
  `ui/Illustrations.kt`. New features get a drawing, not just a label.
- Nothing on the tablet asks for typing. Parents edit the Google Sheet or post cards.
- The home screen is four cards: Today and Jobs (small, left), Playing and Note (big,
  right), a black band on top, a day-card footer. Resist adding a fifth card.
- Tap targets ≥ 64 dp; body text ≥ 20 sp. The screen is read from across a counter.

## Data flow

`GET /api/today` is the only read the tablet makes. `lib/today.ts` assembles it from the
sheet cache (`kv` table, refreshed when older than 10 minutes), Google Calendar, Open-Meteo,
`job_completions`, `cards`, `catalog_items`, and Crate. Every source is wrapped so one
failure only adds a line to `warnings`.

The Google Sheet is the master list (`supabase/sheet-template.md`). `lib/catalog.ts`
resolves Watch rows with TMDB (availability) and JustWatch's GraphQL endpoint (title
link), a few per request and all of them in the daily cron. A title that is not on a
paid service is simply not returned; nothing is ever flagged for a human.

`lib/types.ts` and `android/.../model/Today.kt` are mirrors. Change both together.

## Environment

See `.env.example`. Google access is a service account (JWT signed in `lib/google.ts`,
no googleapis dependency); share the calendar and the sheet with its `client_email`.
