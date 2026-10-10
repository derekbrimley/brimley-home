# Step 5: Watch on the TV, podcasts on the tablet

Date: 2026-10-09. Milestone 5 of `docs/PROPOSAL.md`.

## Goal

A kid taps a poster on the Watch tab, confirms, and the title starts on the living-room
Chromecast with Google TV, with no Google TV remote and no Google TV home screen. The
Playing card then shows the TV is on, with a few big controls. Podcasts on the Listen
shelf play from the tablet itself (its speaker, or whatever Bluetooth speaker it is
connected to), like music now does.

## Decisions

- **Android TV Remote protocol v2**, not ADB: no developer mode on the TV, pairing by an
  on-screen code, power and current-app state for free. Reference implementation:
  the Python `androidtvremote2` library.
- **Everything TV-related runs on the tablet.** Vercel cannot reach the home network.
  No backend changes.
- **No timers.** No "stop after this", no "off in N minutes". A "TV off" button only.
- **Podcasts play on the tablet** with ExoPlayer (`androidx.media3`). Music already plays
  on the tablet through Spotify Connect (`KITCHEN_DEVICE_NAME=DC-1`).
- The TV only knows what the tablet started. Google TV does not report what is playing
  inside Disney+, so a title started elsewhere shows as the app name.

## 1. Talking to the TV (`home.brimley.tv`)

| Unit | Purpose |
|---|---|
| `Proto.kt` | Minimal protobuf writer/reader (varint, length-delimited, nested messages) for the protocol's messages. Hand-written, no Gradle protobuf plugin. Messages are framed with a varint length prefix. |
| `TvIdentity.kt` | The tablet's client certificate: an RSA 2048 key pair generated once in AndroidKeyStore (which issues the self-signed certificate). Stores the paired TV's certificate fingerprint, host and port in SharedPreferences. |
| `TvPairing.kt` | Port 6467. PairingRequest → Options (hexadecimal, 6 symbols, input role) → Configuration → TV shows the code → PairingSecret. Secret = SHA-256(client modulus, client exponent, server modulus, server exponent, bytes of code[2..6]); the first byte must equal code[0..2], otherwise the code was mistyped. |
| `TvRemote.kt` | Port 6466, one long-lived TLS connection. Answers `remote_configure`, `remote_set_active`, `remote_ping_request`. Tracks `remote_start` (power), volume, and the current app package (from `remote_ime_key_inject.app_info`). Sends `remote_key_inject` (SHORT) and `remote_app_link_launch_request`. Reconnects with backoff (1, 2, 5, 10, 30 s). |
| `TvFinder.kt` | mDNS discovery of `_androidtvremote2._tcp` with NsdManager. The last host is cached; discovery runs again when a connect fails. |
| `TvController.kt` | The one object the UI uses. `StateFlow<TvState>` and `play(link)`, `playPause()`, `volumeUp()`, `volumeDown()`, `powerOff()`, `pair(code)`. |

`TvState`: `NotPaired`, `Connecting`, `Unreachable`, `Off`, `On(appPackage: String?, started: StartedTitle?)`.
`StartedTitle` is the catalog item the tablet launched (title, service label, poster);
it is cleared when the TV turns off.

TLS: the server certificate is accepted during pairing and pinned by fingerprint
afterwards; a different certificate means "pair again".

`play(link)`: if the TV is off, send KEYCODE_POWER and wait for `remote_start` true
(up to 8 s); then send the app link. If the TV is unreachable, try once more after
reconnecting; then fail with "Couldn't reach the TV".

`powerOff()`: sends KEYCODE_POWER only when the TV reports it is on.

Lifetime: `TvController` is created by `BrimleyApplication`, connects at app start and
stays connected; pings from the TV detect a dead link within about 10 s.

### Pairing screen

Shown on the first Play when not paired, and from "TV not set up" on the Playing card.
Full screen: the TV drawing, "Look at the TV", the six entered characters, a 16-key pad
(0–9, A–F), keys ≥ 64 dp, a Cancel button. A wrong code shakes the entry and asks the
TV for a new code. This is the one place the tablet takes input; it recurs only if the
TV is reset or the tablet's key is lost.

### Debug link screen

Debug builds only: a list of every catalog link with a Send button, plus the TV state.
Used once per service on the real TV to confirm deep links open the right title
(Max's JustWatch links are the least certain).

## 2. What the family sees

**Watch tab.** The confirmation sheet stays. Play shows "Starting on the TV…"; success
closes the picker and returns home; failure keeps the sheet with "Couldn't reach the
TV" and Try again. Titles with no link are hidden from the shelves.

**Playing card, TV entry.** Merged on the tablet with `today.playing` (the backend
cannot see the TV).

- Started by the tablet: poster, title, service, "Living room TV" pill.
- On some other way: the TV drawing, "Living room TV", and the app label from the
  package (`com.disney.disneyplus` → Disney+, `com.netflix.ninja` → Netflix,
  `com.wbd.stream` → Max, `com.google.android.youtube.tv` → YouTube, …; unknown → no
  label).
- Controls, 64 dp: play/pause, volume −, volume +, and a "TV off" pill.
- Header: "TV on" / "TV off" / "TV not set up" (tappable: opens pairing).
- Gone within a few seconds of the TV turning off.

No new card; the band and footer do not change.

## 3. Podcasts on the tablet (`home.brimley.audio`)

- Listen items carry the RSS feed in `link`. Tapping one opens the sheet with the five
  newest episodes (title, date, length); tapping an episode plays it immediately.
- `FeedReader.kt`: fetch with OkHttp, parse with XmlPullParser (`item`, `title`,
  `enclosure@url`, `pubDate`, `itunes:duration`, channel `itunes:image`).
- `PodcastPlayer.kt`: one ExoPlayer owned by the Application, audio attributes set to
  speech with `handleAudioFocus = true` (so it pauses Spotify on the tablet). No
  service; the app is always in the foreground. Exposes a `StateFlow` of the current
  episode, position and playing flag.
- Playing card: a kitchen entry (cover, episode title, show name, "Kitchen speaker")
  with −30 s, play/pause, +30 s. Removed when the episode ends or is stopped.

## 4. Kiosk, deploys, docs

- When device owner, `setLockTaskPackages(home.brimley, com.spotify.music)` so lock
  task never blocks Spotify. Skipped silently otherwise.
- `.vercelignore`: `android/`, `docs/`.
- README: TV pairing steps; music plays on the DC-1 via `KITCHEN_DEVICE_NAME=DC-1`.
- PROPOSAL: drop the dongle/Cast and option B text; mark step 5 done; record "no
  timers for now".

## Testing

- JVM unit tests in `android/app/src/test`: protobuf round trips against byte fixtures
  for each message we send or read; the pairing secret and code check against a known
  vector; RSS parsing of a fixture feed; package → label mapping.
- `./gradlew testDebugUnitTest assembleDebug` (JDK 17). Backend `npm test` and
  `npm run typecheck` unchanged.
- On the hardware (Derek): pair; send one link per service from the debug screen; a
  Watch tap end to end; TV off; a podcast episode through the Bluetooth speaker.

## Build order

1. Proto + identity + pairing + remote + finder + controller.
2. Pairing screen and debug link screen (test on hardware here).
3. Watch Play and the Playing card's TV entry.
4. Podcasts.
5. Kiosk allow-list, `.vercelignore`, docs.

## Out of scope

Timers; Yoto (milestone 6); a backend proxy for TV commands; the Cast SDK.
