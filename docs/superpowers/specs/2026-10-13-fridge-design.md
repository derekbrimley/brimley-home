# Step 7: The Fridge (older notes and Our makes)

Date: 2026-10-13. The other half of milestone 6 in `docs/PROPOSAL.md` ("Our makes").
The Yoto half is `2026-10-11-yoto-stories-design.md`.

## Goal

A kid taps the Note card and the Fridge opens: today's note big, the notes drawn
before it, and the things the family has made (a game, a story, a Lego build) with
a picture, a title and who made it. Tapping an old note shows it large. Tapping a
make that has a link opens it on the tablet, and a big button always leads back.
Parents add makes in the sheet's Makes tab. Nothing on the tablet asks for typing.

## What is already there

- The sheet's `Makes` tab (Title · Who · Link · Picture URL) is parsed by
  `lib/sheet.ts`. `/api/today` already sends `makes: Make[]`, and `Make` is mirrored
  in `lib/types.ts` and `model/Today.kt`. The tablet never shows them.
- Every note and drawing is a row in `cards` (`kind` `note` or `drawing`, no
  `show_until`), posted by `POST /api/notes`. `NoteCard` shows the newest one.
  Tapping the card or its round pencil opens `NoteScreen`.

## A problem to fix first

Notes never expire, so `activeCards` returns **every** note ever drawn. Each one is
a base64 PNG of up to 400 KB, and `/api/today` sends all of them on every
60-second poll. The tablet also writes them all to its disk cache. On top of that,
`getCardRows()` reads only the newest 100 rows of any kind, so once there are about
100 notes, an older birthday or countdown card (posted early with a future
`show_from`) drops off the dashboard without any warning.

The Fridge depends on keeping old notes, so this has to be fixed as part of it:

- `/api/today` sends only the **newest** note or drawing, plus the other kinds as
  before. `getCardRows()` is split into two queries: non-note kinds, limit 100, and
  the newest `note`/`drawing`, limit 1. `activeCards` itself doesn't change.
- Older notes are fetched only when the Fridge opens (below).

## Decisions

- **The Note card opens the Fridge, and the round pencil still draws.** Today a tap
  anywhere on the card draws. After this change, a tap on the card opens the Fridge,
  and a tap on the 72 dp pencil goes straight to `NoteScreen` as before. The Fridge
  has its own "Draw a note" button too. This is the one change the family will
  notice. It's needed because the Fridge has to hang off the Note card to keep the
  home screen at four cards.
- **No new Vercel function.** `api/notes.ts` gains a `GET`. The project stays at 7 of
  12 functions, and there are no new two-segment routes (Vercel never delivered
  those into catch-alls; see commit `21587d4`).
- **The Fridge holds the last 12 notes.** They're fetched once each time the Fridge
  opens, with their PNGs. Stylus drawings are half-resolution monochrome PNGs,
  usually tens of KB each, so 12 is a reasonable one-off download. They're never
  polled and never cached to disk.
- **Old notes are read-only.** The tablet can't take a note down or put an old one
  back up. A parent removes one with `DELETE /api/cards/:id`, as today. Rows older
  than the newest 12 just stop showing; nothing is deleted.
- **Makes stay in the sheet, in sheet order.** Parents put the newest at the top if
  they want it first. The `projects` table from the proposal's schema isn't built:
  the sheet already is the master list, and a table would need an admin UI to fill
  it.
- **A make's link opens in an in-app WebView**, kept in kiosk. The WebView stays on
  the link's own host, so a game can load its pages but a stray link to another
  site does nothing. A make with no link only shows its picture. The alternative,
  showing links nowhere on the tablet, is listed under open questions.
- **Picture URLs from Google Drive are fixed up.** Parents will paste Drive share
  links (`drive.google.com/file/d/<id>/view`, `open?id=<id>`), which don't serve an
  image. `lib/makes.ts` rewrites them to `https://drive.google.com/thumbnail?id=<id>&sz=w800`.
  Any other URL is passed through. The Drive file must be shared "anyone with the
  link".

## 1. Backend

| Route | Does |
|---|---|
| `GET /api/notes?limit=12` | `{ notes: FridgeNote[] }`, newest first. `limit` is clamped to 1..24. |
| `POST /api/notes` | Unchanged. |
| `GET /api/today` | `cards` holds at most one `note`/`drawing` (the newest). `makes[].imageUrl` goes through `normalizeImageUrl`. |

- `FridgeNote = { id, kind: "note" | "drawing", body: string | null, png: string | null, createdAt }`.
  It goes in `lib/types.ts`, mirrored in `Today.kt`. Built by a pure
  `toFridgeNotes(rows)` in `lib/notes.ts`, which drops a drawing with no PNG.
- `lib/queries.ts`:
  - `getTodayCardRows()` replaces `getCardRows()` in `buildToday`: non-note kinds
    (`.not("kind", "in", "(note,drawing)")`, limit 100) plus the newest note, in a
    single `Promise.all`.
  - `getNoteRows(limit)` returns note and drawing rows, newest first, with
    `created_at`. `GET /api/cards` uses the same split as `/api/today`.
- `lib/makes.ts`: `normalizeImageUrl(url)` and `toMakes(rows)`. The inline map in
  `today.ts` moves here.
- **No schema change.** `cards.created_at` already exists. An index
  `cards_kind_id (kind, id desc)` is added to `schema.sql` so the newest-note queries
  stay cheap.

## 2. What the family sees

**Home.** The Note card looks the same. Its title row gains a small "Fridge →" pill
on the right (`CardTitle`'s `trailing`), so the new tap target can be found. A tap
anywhere except the pencil opens the Fridge.

**The Fridge** (`ui/FridgeScreen.kt`) is a full screen with the black band like
`NoteScreen`:

- Band: "← Home", the fridge drawing, the title "The fridge", and "Draw a note" on
  the right.
- **Notes** row: a horizontal strip of square tiles (220 dp), newest first. The
  first tile has the thick 6 dp border and a "Up now" pill, and the rest have the
  3 dp rule. Drawings use the same ink-only crop as the Note card, and text notes
  are in Caveat. Each tile shows when it was drawn underneath ("Today", "Yesterday",
  "Mon", or "Sep 30"). Tapping a tile opens it large.
- **Our makes** grid: tiles (240 × 300 dp) with the picture cropped square (or the
  fridge drawing when there's none), the title, and "by Sylvie" when Who is set. A
  tile with a link gets a small "Open" pill.
- While the notes load, the strip shows today's note from `/api/today` straight
  away, with "Getting the older ones…" beside it. If loading fails, it says "Couldn't
  reach the older notes", and the makes still show (they come from `/api/today`).
- Empty makes: "Nothing on the fridge yet. Add things you made to the Makes tab of
  the family sheet." Empty notes can't happen, because the Fridge opens from the
  Note card.

**One note, large.** A full-screen overlay with the drawing filling the paper, the
date in the band and "← Back to the fridge". There's no swiping; the strip is the
navigation.

**A make, open** (`ui/MakeScreen.kt`). The black band with "← Back to the fridge"
and the make's title, then a WebView filling the rest. JavaScript is on, there's no
address bar, and `shouldOverrideUrlLoading` blocks other hosts. When the page fails
to load, the screen shows "This one didn't open. Ask a grown-up to check the link."
over the fridge drawing.

**The drawing:** `FridgeIllustration`, a tall rounded fridge with two doors, a
handle cut out in white, and a sheet of paper held by a round magnet on the top
door. It's used in the band, for makes with no picture, and in the empty and error
states.

**Tablet plumbing:**

- `HomeApi.notes(limit)` and `TodayRepository.fridgeNotes()` return a `Result`.
- `Screen.Fridge` and `Screen.Make(make)` are added in `BrimleyApp`. `HomeScreen`
  gets `onOpenFridge` next to `onDrawNote`, and NoteScreen's "Never mind" goes back
  to wherever it was opened from.
- `DrawingImage`/`inkOnly` move from `NoteCard.kt` to `ui/Drawing.kt`. Decoding
  moves off the main thread (`produceState` on `Dispatchers.Default`), because the
  strip decodes up to 12 PNGs pixel by pixel.
- A pure `sameHost(base, url)` in `ui/MakeScreen.kt` is the WebView guard.

## Testing

- `lib/notes.test.ts`: the row mapping, dropping a drawing with no PNG, and the
  limit clamp (missing, `0`, `100`, `"abc"`).
- `lib/makes.test.ts`: the Drive `file/d/…/view`, `open?id=` and `uc?id=` forms, a
  non-Drive URL passed through, and blank → null.
- `lib/cards.test.ts` stays green. The query split is checked by hand against
  Supabase (below), because `queries.ts` has no tests today.
- `SameHostTest` (JVM): the same host passes, a subdomain or other host is blocked,
  and `about:blank`/`javascript:` are blocked.
- `npm test`, `npm run typecheck`, `G testDebugUnitTest assembleDebug`.
- By hand: the `/api/today` payload before and after, with 20+ notes in `cards`
  (it should drop to one note); `curl /api/notes?limit=12`.
- On the DC-1 (Derek):
  - The Note card opens the Fridge, and the pencil still draws.
  - Draw a note, and it's first in the strip.
  - Old notes open large.
  - A make with a Drive picture shows, and a Scratch link opens and plays.
  - A link to another site does nothing.
  - "Back" returns each time.

## Build order

1. Backend: the query split and its index, `lib/notes.ts` and `lib/makes.ts` with
   tests, and `GET /api/notes`. Check by hand: the `/api/today` size, and `curl
   /api/notes`. This step ships on its own and fixes the payload even before the
   tablet changes.
2. Tablet: the model and API, `ui/Drawing.kt`, the drawing, `FridgeScreen`, the
   large-note overlay, `MakeScreen`, and the Note card's tap and pill.
3. Docs: the `PROPOSAL.md` build status (milestone 6 done), the README's "where
   things stand", and `sheet-template.md` (Drive pictures must be shared "anyone
   with the link").

## Open questions (recommendation first)

1. **Make links: open them in a WebView, or don't show them on the tablet at all?**
   Recommended: a WebView limited to the link's host. With no links, the Fridge is a
   picture wall, which is safer and simpler, but a game a kid made can't be played.
2. **Card tap: should it open the Fridge, or keep drawing, with the Fridge behind the
   "Fridge →" pill only?** Recommended: open the Fridge, because the pill alone is a
   small target. Either way, the pencil keeps drawing.
3. **"Put it back up" for an old note?** It would copy the row as a new note.
   Recommended: leave it out for now. It's an easy add later (`POST /api/notes
   { fromId }`).

## Risks and unknowns

- **WebView on the DC-1.** Reflective LCD at 60 Hz, so a Scratch game should run,
  but it hasn't been tried. Scratch's own player is heavy. If it's too slow, the
  fallback is open question 1's "picture only".
- **Drive thumbnails** need the file shared publicly, and Google sometimes changes
  the thumbnail endpoint. The rewrite is one function in `lib/makes.ts`, and a bad
  URL just shows the fridge drawing.
- **Kiosk.** A WebView must not open the system browser or a new window.
  `setSupportMultipleWindows(false)` and blocking downloads keep the kids in the app.

## Out of scope

- Adding makes from the tablet, taking photos of makes, and the `projects` table.
- Deleting or re-posting notes from the tablet, and who drew a note (posts carry the
  tablet's token name, not a kid).
- The Saturday "golden week" banner.
