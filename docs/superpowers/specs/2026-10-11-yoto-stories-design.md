# Step 6: Stories from the Yoto

Date: 2026-10-11. Milestone 6 of `docs/PROPOSAL.md` (the Yoto half; "Our makes" is
separate).

## Goal

A kid opens Play something → Stories, sees the covers of the family's Yoto cards,
taps one and picks where it plays: **on the Yoto player** (no card needed in the
slot) or **here in the kitchen** from the tablet's speaker. The Playing card then
shows the story with play/pause and stop. A card pushed into the Yoto by hand also
shows up on the Playing card, a few minutes late.

## Decisions

- **The backend owns Yoto.** The OAuth tokens, the library cache and the commands
  all live in `lib/yoto.ts`, behind `api/yoto/[[...path]].ts`. The tablet carries no
  Yoto credentials, and `/api/today` reports the Yoto entry (the `"yoto"` Playing
  target already exists in `lib/types.ts` and `Today.kt` for this). This is the
  opposite of the TV, which had to run on the tablet only because Vercel can't reach
  the home network. Yoto is a cloud service, so Vercel can reach it.
- **Commands go over MQTT, one short session each.** Yoto's REST API has no
  play/pause. The players listen on AWS IoT (`wss://aqrphjqbp3u2z-ats.iot.eu-west-2.amazonaws.com`,
  custom authorizer `PublicJWTAuthorizer`, the access token as the password). A
  command connects, publishes at QoS 1 and disconnects, in about a second. There is
  no long-lived connection, because a serverless function can't keep one.
- **Status comes from REST, helped by our own commands.** `GET /device-v2/{id}/status`
  gives `activeCard`, `isOnline` and `updatedAt`. It refreshes about every 5 minutes
  and never says "paused". Each command we send writes `kv.yoto_now`
  (`{ cardId, playing, at }`), and whichever of the two is newer wins. A pause stays
  a pause while the status still shows the same card.
- **Login is one-time, on a laptop.** `node scripts/yoto-login.mjs <client id>`
  runs Yoto's documented browser flow (authorization code + PKCE, redirect to
  `http://localhost:8765/callback`) and prints a refresh token for
  `YOTO_REFRESH_TOKEN`. Yoto rotates refresh tokens, so the current one is kept in
  `kv.yoto_auth`, along with a hash of the env token it started from. Changing the
  env var (logging in again) starts a new chain. The access token is cached there
  until five minutes before it expires, so refreshes happen every few hours, not
  once a minute.
- **The tablet plays a card itself** with the podcast player (ExoPlayer). It asks
  for signed track URLs (`/content/{id}?playable=true&signingType=s3`) at tap time,
  because they expire.
- **No chapter picker, no volume, no sleep timer.** A card starts from the top.
  Volume belongs to the knobs on the Yoto. This matches step 5's "no timers".
- **Only the family library.** Make-your-own cards (`/content/mine`) are left out
  for now.

## 1. Backend (`lib/yoto.ts`, `api/yoto/[[...path]].ts`)

| Route | Does |
|---|---|
| `GET /api/yoto` | `{ cards: StoryCard[], player: { id, name, online } \| null }`. The cards come from the library cache. The player comes from a live `devices/mine` call, so the sheet can say "asleep". |
| `GET /api/yoto/tracks?cardId=` | `{ tracks: { title, chapter, url, durationSec }[] }`: signed URLs, in chapter order. `yoto:#…` placeholders are skipped. |
| `POST /api/yoto/play` | `{ cardId }` → MQTT `device/{id}/command/card/start` with `{"uri":"https://yoto.io/<cardId>"}`. |
| `POST /api/yoto/control` | `{ action: pause \| resume \| stop }` → `…/command/card/{action}`. |

- `StoryCard = { id, title, author, imageUrl }`, taken from `card.title`,
  `card.metadata.author` and `card.metadata.cover.imageL` in `/card/family/library`.
  Blanks and duplicates are dropped, and the rest are sorted by title.
- **Which player:** `YOTO_DEVICE_ID` if it's set. Otherwise the first one that's
  online, otherwise the first one.
- **Library cache:** `kv.yoto_library` (`{ fetchedAt, cards, playerId }`), refreshed
  after 6 hours and by the daily cron. A stale copy is served if Yoto is down.
- **Asleep:** before publishing, `devices/mine` is checked. An offline player
  answers 502 "The Yoto is asleep. Press its button, then try again". Without that
  check, the command would vanish into the broker.
- **`/api/today`:** `fetchYotoPlaying()` joins the other `settle()` calls. It makes
  one Yoto call per request (the status), plus the library cache. Without
  `YOTO_CLIENT_ID`/`YOTO_REFRESH_TOKEN` it returns nothing and adds no warning, like
  Crate.
- **Function count:** this adds one function, `api/yoto`, for 7 of 12. `vercel.json`
  rewrites the bare `/api/yoto` like `cards` and `music`.
- **New dependency:** `mqtt` (mqtt.js 5). It's imported on demand, so `/api/today`'s
  cold start doesn't load it.

## 2. What the family sees

**Stories tab.** A grid of square covers (170 dp), each with its title and author.
A card with no cover gets the book drawing. Empty library: "No Yoto cards yet.
Cards added in the Yoto app show up here."

**Story sheet** (tap a cover). The title, the author, then "Where should it play?"
over two big tiles:

- **On the Yoto**, with the Yoto drawing and the player's name. It reads "Asleep:
  press its button" when offline, and the tile stays tappable, because the kid may
  have just pressed it. A tap shows "Starting on the Yoto…". Success goes home;
  failure keeps the sheet open with the backend's sentence.
- **Here in the kitchen**, with the record drawing and "Plays on this speaker".
  A tap shows "Getting it ready…", fetches the tracks, plays them in order and goes
  home.
- **Not now.**

**Playing card.**

- The Yoto entry shows the cover (or the Yoto drawing), the title, the author and the
  "Yoto" pill. The controls are play/pause (64 dp) and a "Stop" pill. The header
  reads "Yoto playing" / "Yoto paused" / "Yoto quiet".
- A card played here appears as the kitchen entry (the existing `"podcast"` target),
  with the card title, the author and −30/+30.

**New drawing:** `YotoIllustration`, the boxy player with two knobs, a card in the
slot and a pixel smile on its screen. It's used on the sheet and as the Playing
fallback.

## 3. Setup (Derek, once)

1. At yoto.dev, sign in, then My apps → create an app. Allowed callback URL:
   `http://localhost:8765/callback`; scopes `family:library:view`,
   `family:devices:view`, `family:devices:control`. Note its client ID.
2. `node scripts/yoto-login.mjs <client id>`, then sign in with the family Yoto
   account.
3. Vercel env: `YOTO_CLIENT_ID`, `YOTO_REFRESH_TOKEN`, optionally `YOTO_DEVICE_ID`.
   Redeploy.

## Testing

- `lib/yoto.test.ts` (vitest, pure):
  - token choice and rotation
  - library mapping
  - player choice
  - track mapping
  - status-vs-command resolution, covering the four cases: no command, a newer
    command, a pause outlived by the status, a different card since
  - MQTT topic/payload
- `StoryQueueTest` (JVM): a card's tracks become a play queue; no playable tracks →
  nothing.
- `npm test`, `npm run typecheck`, `G testDebugUnitTest assembleDebug`.
- On the hardware (Derek): log in; Stories lists the cards; one card on the Yoto
  (asleep, then awake); pause, resume, stop from the Playing card; one card here;
  a card pushed in by hand shows within ~5 minutes.

## Build order

1. Backend: `lib/yoto.ts` + tests, the route, `/api/today`, the cron, the login
   script, env and `vercel.json`. Hardware check: the login and `curl /api/yoto`.
2. Tablet: model + API, the drawing, the story queue, the Stories tab and sheet,
   the Playing card controls.
3. Docs.

## Risks and unknowns

- The broker host, the authorizer name and the topic layout are copied from the
  maintained open-source clients (`yoto_api` for Python, `yoto-nodejs-client`),
  which match yoto.dev's "Connecting to players" page. yoto.dev itself couldn't be
  read while writing this. If the host differs, it's one constant.
- Scopes: the login asks for `offline_access family:library:view family:devices:view
  family:devices:control`, which must also be turned on for the app at yoto.dev. The
  first login asked only for `openid profile offline_access` and every API call
  answered 403.
- If Yoto's portal refuses a `localhost` callback, the fallback is the device-code
  flow (`/oauth/device/code`). It still works in `yoto_api` 4.x, but is reported as
  deprecated.

## Out of scope

"Our makes"; make-your-own cards; chapter choice; volume; a sleep timer; live MQTT
status on the tablet.
