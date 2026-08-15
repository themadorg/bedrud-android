# Bedrud Android — Design System

Material 3 (native, expressive), rounded, token-driven. A warm **rose + teal** brand on warm-neutral
surfaces. Everything visual flows through a token layer under `app/src/main/java/com/bedrud/app/ui/theme/`
so there are no magic values scattered through screens.

> This document describes the **Android app**. The original web app used a different, sharper
> "rose + teal, 0px-radius" system; that is not what this client ships. Treat this file as the source
> of truth for Android UI work.

## Token layer (`ui/theme/`)

| File                | Holds                                                                              | Rule                                    |
|---------------------|------------------------------------------------------------------------------------|-----------------------------------------|
| `Color.kt`          | Reference palette — the raw rose/teal/neutral/red/amber tonal ramps                | Never read directly from UI             |
| `Theme.kt`          | `BedrudTheme` + the light/dark `ColorScheme` role mapping (full M3 role set)       | UI reads `MaterialTheme.colorScheme.*`  |
| `ExtendedColors.kt` | Non-M3 semantic colors (e.g. `warning`) via `LocalBedrudColors`                    | UI reads `MaterialTheme.bedrudColors.*` |
| `Type.kt`           | `Typography` (M3 type scale) + RTL font families                                   | UI reads `MaterialTheme.typography.*`   |
| `Shape.kt`          | `BedrudShapes` (M3 scale) + `BedrudShapeTokens` (semantic: card/field/button/pill) | No raw `RoundedCornerShape(n.dp)`       |
| `Dimens.kt`         | Spacing scale (4dp grid) + component sizes/heights/icon sizes                      | No raw `n.dp` for spacing/sizing        |
| `Elevation.kt`      | Tonal elevation levels                                                             | Surfaces stay low (outline-first)       |
| `Motion.kt`         | Durations + easing for transitions                                                 | No inline animation timings             |
| `Alpha.kt`          | Opacity tokens (`disabled`) for content the app dims itself                        | No inline alpha floats                  |

**The rule:** screens and components reference `MaterialTheme.*` + the token objects. Raw hex colors and
raw `n.dp` literals don't belong in `ui/screens/**` or `ui/components/**`.

## Color

Brand seeds:

- **Primary — rose `#E11D48`** — CTAs, selection, focus, brand identity.
- **Tertiary — teal `#14B8A6`** — accents, "recommended"/info affordances, highlight states.
- **Secondary — muted rose** — lower-emphasis components that still tie to the brand.
- **Neutrals — warm stone** — surfaces/text read as part of the rose family, not clinical grey.
- **Error — red `#DC2626`** — reserved for errors and irreversible/destructive actions.
- **Warning — amber `#B45309` (light) / `#FBBF24` (dark)** — non-critical cautions (e.g. insecure http). M3 has no warning role, so it's a custom extended-color token (`MaterialTheme.bedrudColors.warning`, from `ExtendedColors.kt`); never use error-red for a warning.

The full Material 3 role set is specified for light **and** dark (primary/secondary/tertiary + their
containers, the surface-tonal levels `surfaceContainerLowest…Highest`, `inverse*`, `outline`, `scrim`),
so any component that reaches for a role gets an on-brand value instead of an M3 default.

`dynamicColor` is **off** by default so the brand is preserved; Material You can be opted into per-call
via `BedrudTheme(dynamicColor = true)`.

### Accessibility (non-negotiable)

- **Color is never the only signal** — pair status with an icon, label, ring, or shape (e.g. a selected
  card uses a filled radio **and** a 2dp primary border, not color alone).
- Body text on surfaces uses `onSurface`; secondary text uses `onSurfaceVariant` — both meet WCAG AA.
- Primary CTA is rose `#E11D48` (AA on white; dark mode lifts to `#FB7185`).
- Every interactive element has a visible focus/selection state and a ≥48dp touch target (`Dimens.minTouchTarget`).

## Typography (`Type.kt`)

Material 3 type scale. `FontFamily.SansSerif` (system) for LTR; **Vazirmatn** / **Shabnam** for RTL
(Arabic, Persian), selected automatically in `BedrudTheme` from the active `AppLanguage`.

## Shape (`Shape.kt`)

Rounded, Material-3-native. Scale: `xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 28 · full`. Semantic tokens:
`field = md`, `button = md`, `card = lg`, `chip = sm`, `pill = full`, `sheetTop = xxl (top corners)`.

## Spacing & sizing (`Dimens.kt`)

4dp base grid (`space2…space56`). Layout: `screenPadding 24`, `screenPaddingCompact 16`,
`maxContentWidth 480` (keeps forms readable on tablets/foldables). Components: `buttonHeight 48`,
`buttonHeightLarge 56`, `fieldMinHeight 56`, `minTouchTarget 48`, `borderThin 1`, `borderStrong 2`,
icon sizes `iconXs 16 · iconSm 18 · iconMd 24 · iconLg 32`, `avatar 40`, `brandMark 72`.

## Elevation (`Elevation.kt`) & Motion (`Motion.kt`)

Elevation is tonal and light — the app leans on outlines + tonal surfaces over shadows; most surfaces
sit at level 0–1. Motion uses shared duration tokens (`durationShort/Medium/Long`) + `standardEasing`;
drive `animate*AsState` with `tween(Motion.durationMedium, easing = Motion.standardEasing)`.

## Disabled state (`Alpha.kt`)

M3 components (buttons, radios, fields) already dim themselves when `enabled = false`. Where the app
disables a *composite* instead — a whole card that can't be picked, a logo behind an unavailable
sign-in method — M3's own answer is the normal colors at reduced opacity, so apply `Alpha.disabled`
(0.38) rather than swapping in muted colors. Recolouring a container to `onSurfaceVariant` is not
enough on its own: that is also what an unselected-but-selectable element looks like, so the
disabled state reads as merely deselected.

## Components (`ui/components/`)

- **`BedrudButton`** — 6 variants (PRIMARY, SECONDARY, TONAL, OUTLINE, GHOST, DESTRUCTIVE). Token-driven height
  (`defaultMinSize(buttonHeight)`, so callers can grow it, e.g. `height(buttonHeightLarge)` for a full CTA),
  shape (`BedrudShapeTokens.button`), and padding. Built-in `loading` state.
- **`BedrudCard` / `BedrudOutlinedCard`** — outline-first cards, tonal surface, minimal elevation.
- **`BedrudCompactTopBar`** — compact status-bar-aware header. Takes either a `title: String` or a
  slot `title` composable (the rooms header uses the slot for its "{server} rooms" name, in a single
  neutral tone, with a trailing chevron marking it as the server switcher's entry point), plus an
  `actions` row.
- **`BedrudSnackbarHost`** — Material 3 snackbar with the rounded shape token; used across the auth
  screens and the rooms dashboard.
- **Selectable cards** (e.g. the server chooser) — a `selectableGroup()` of `Surface`s marked
  `selectable(role = RadioButton)`, selection shown by a radio **and** a primary border.
- **Per-server color** — `parseInstanceColor("#RRGGBB")` in `ui/theme/InstanceColor.kt` is the single
  source of truth for an instance's accent color (server header, profile row, and each rooms card's
  leading stripe + colored "on {server}" tag).
- **Rooms cards** — an outlined card with a per-server accent stripe on the leading edge; swiped left
  (M3 `SwipeToDismissBox`) for a contextual action — **Remove** a recent from local history (instant),
  or **Delete** a room you own (routed through a confirm dialog).
- **`DevOnly` / `DevHintBadge`** — see below.

## Bottom sheets (`BedrudBottomSheet`)

Every sheet in the app goes through **`BedrudBottomSheet`** so they share one container, one drag
handle, one shape, one set of insets, and one gutter. Those are **fixed, not defaulted** — the
component takes no colour, shape or state parameters at all:

- **Material's drag handle**, not a hand-drawn bar. A bare `Box` can match the 32×4dp look but
  carries none of the accessibility semantics or the expanded touch target.
- **`BedrudShapeTokens.sheetTop`**, which is already M3's 28dp `extraLarge` top corners — the token
  names the default rather than departing from it.
- **`BottomSheetDefaults.ContainerColor`** (`surfaceContainerLow`), with no override available.
- **`navigationBarsPadding()` then `imePadding()`**, always. IME padding resolves to zero with the
  keyboard down, so a sheet carrying a text field needs no special case, and nav-bar insets already
  consumed are not counted twice.

**Why there is no `containerColor` parameter.** There used to be, defaulting to M3's, and the one
caller that overrode it — the meeting chrome — set the container to `surface`. In the dark palette
that is `Stone950` `#0C0A09`: the *exact* colour of `background`, which is what the meeting screen
draws behind it. The sheet therefore had no edge at all whenever the camera was off. M3's
`surfaceContainerLow` (`#161311`) exists precisely to lift a sheet off the background, and it is
equally opaque over video, so a darker container bought nothing anywhere. A default is a
suggestion; the fix was to delete the knob, not to re-tune it.

The sheet state is not a parameter either: exposing it would put an experimental Material type in
the signature and force `@OptIn` onto every screen that shows a sheet. `ModalBottomSheet` is now
referenced in exactly one file.

**Actions inside a sheet are a list, not a stack of cards.** `BedrudSheetActionRow` follows the M3
list-item spec — 56dp one-line, 72dp when it carries a supporting line, `iconMd` leading icon — and
is deliberately **not** wrapped in a per-row filled or outlined container. M3 reserves per-item
containers for *selectable cards*; a menu of actions is a list. Emphasis comes from `contentColor`
(e.g. the error colour for a destructive choice), and selection from a trailing check, not from a
filled row.

That also avoids a trap worth recording: **never rely on a border to separate a control from a
raised surface.** In the dark palette `outlineVariant` (`Stone800`, `#292524`) lands within two RGB
units of a sheet or dialog surface (`#2b2624`), so a default `OutlinedButton` border is drawn and
perceptually invisible there. Outlined controls read correctly on the app background
(`Stone950`) and disappear on raised surfaces. If an outline is genuinely needed on a raised
surface, give it an explicit colour with real contrast.

## Meeting chrome

The in-call screen has its own chrome standard (palette via `meetingChromeColors()`, metrics under
the `meeting*` tokens in `Dimens.kt`, timing in `Motion.meetingChromeAutoHideDelayMs`):

- **Top bar** (`MeetingTopBar`): invite/participants entry at the start; the room name centered,
  with the recording dot (dev-gated until the server exposes recording state) and a reconnecting
  dot when applicable; camera flip (**only while the local camera is live**) and audio output at
  the end. The center block is weight-balanced so the title never shifts as trailing actions
  appear.
- **Controls bar** (`MeetingControlsBar`): a floating pill — camera, screen share, mic, chat,
  hang-up — with a **drag handle** on top. Tapping the handle or swiping up anywhere on the bar
  opens the more-options sheet, mirroring how a bottom sheet is pulled up. There is no "⋯" button.
- **Grid** (`MeetingVideoGrid`): the local participant **always** has a tile, camera on or off —
  it is where the speaking ring proves the room is receiving you, so it cannot be conditional.
  (This reverses the original "self-tile only while the camera is on" rule from #104.) There is
  no "no one else is here" copy: the self-tile already shows an empty room for what it is, and
  the invite entry sits in the top bar. If someone is sharing while you are alone, the stream
  takes the stage and the grid stands down. Breakpoints:
  1–3 tiles stack as full-width rows; 4 → 2×2; 5 → 2×2 plus one half-width centered; 6 → 2×3;
  beyond that the last slot collapses into a **"+N"** tile that opens the participants list.
  Landscape transposes rows into columns.
- **Tiles**: name chip centered along the bottom edge, carrying the mic-off badge. There is no
  camera badge — the tile already shows an avatar in place of video when the camera is off, so it
  only restated what the tile was showing.
  **Double-tap** expands a tile to fullscreen and long-press opens the participant actions;
  there is no corner button. Since touch exploration cannot produce a double tap, the tile also
  carries a custom accessibility action for fullscreen — the gesture is never the only route.
- **Streams** (`MeetingStreamTile`): every live screenshare gets a strip tile above the grid.
  Several people can share at once; watching is **opt-in per viewer, one stream at a time**
  (LiveKit selective subscription — no gossip protocol). Unwatched shares render as a
  placeholder with a watch button, your own share offers stop, and long-pressing the watched
  stream opens `MeetingStreamSheet` (dev-hinted volume until share audio exists (#105), leave
  stream — neutral, not red: leaving is reversible).
- **Per-tile fullscreen** (`MeetingParticipantFullscreen`): chrome (name chip, collapse button,
  controls bar) auto-hides after `meetingChromeAutoHideDelayMs` of inactivity; any tap toggles it
  back; while hidden the system bars hide too (immersive). The hardware back key collapses
  fullscreen instead of leaving the meeting.
- **Audio input** (`MeetingAudioSettingsSheet`): output device + output volume (the voice-call
  stream the hardware keys drive), and the input mode. **Push to talk** turns the mic slot into a
  hold-to-talk pill — outlined idle, filled while transmitting — enabling the mic only while held
  and never touching the persisted mic preference. **Voice activity** with auto sensitivity keeps
  the platform's own processing (today's behavior); manual sensitivity engages
  `VoiceGateProcessor`, a capture post-processor that mutes frames whose RMS falls below the
  slider's dBFS threshold (with a ~300ms hangover so syllables don't clip). Noise suppression
  (Off / Device) applies on the next join — the audio device module is built per connection.
- **Mic meter**: the same processor always measures (gating stays conditional), publishing a
  0..1 level rendered as four bars, and the bars dim the moment the manual gate shuts. It lives
  **beside the sensitivity slider** in the audio settings sheet and nowhere else. It used to sit
  in the controls-bar mic pill, but a capture meter answers "is the microphone picking me up",
  not "can anyone hear me" — it bounces just as happily while a broken publish sends nothing, and
  next to the speaking ring that reads as a second, contradictory answer to the same question.
  Setting a threshold is the one job only a local meter can do, so that is the job it kept. The
  UI samples the level per animation frame inside the draw scope rather than through a flow, so a
  100 Hz audio signal costs redraws, not recompositions.
- **Speaking ring** (`Modifier.speakingRing`): every participant surface — grid tile, invite-sheet
  avatar, fullscreen name chip — carries the room's own report of who it hears, from
  `RoomEvent.ActiveSpeakersChanged` via `RoomManager.speakingLevels`. The ring thickens with the
  reported level and fades rather than blinking, because the server announces speakers in bursts
  roughly twice a second and never announces silence; `SpeakingTracker` holds each identity for
  `HoldMillis` past its last mention to bridge the gap. **The local participant is in that server
  list like anyone else**, which is the entire point: your own ring lighting up is round-trip
  proof that your audio reached the SFU and was announced to the room, where the mic meter only
  proves the microphone works. Colour never carries it alone — speech also earns a badge in the
  name chip, in the mic-off badge's slot (a muted participant is never a speaking one).
- **Mic pill status ring** (`MicStatusRing`, `VoiceReachMonitor`): anything stopping your voice
  reaching the room is reported on the **outline of the mic pill itself**, in the amber `warning`
  role — never as a banner or toast. The status belongs on the control that fixes it, and a chip
  floating above the bar was one more thing covering the call. `VoiceReachMonitor` compares the
  gate's raw capture level against `speakingLevels` every `SampleIntervalMillis` and names the
  cause; the reach check needs at least one remote participant, since an empty room has no reason
  to report a speaker and no one to miss you.

  Both states share the colour because they are the same news, so **motion carries the cause**: a
  reconnect sends a single arc travelling around the outline (a dashed stroke whose phase moves,
  which follows the pill's rounded corners where a rotated gradient would squash them), while
  anything you can fix yourself — muted, push-to-talk not held, gate shut — pulses in place,
  stationary because nothing is in progress. There is no "connecting" case: first connect happens
  behind a full-screen state, before this bar exists. The wording each state would have used
  survives as the pill's `stateDescription`, so the ring is never colour-and-motion alone.
- **Connected moment**: connecting otherwise ends in silence — the screen simply becomes the call.
  The top bar says "Connected" in the room-name slot for `meetingConnectedNoticeMs`, then hands
  the slot back. It fires again after a reconnect, which is when it is needed most.
- **Sheets**: long-press a tile → `MeetingParticipantSheet` (per-viewer volume slider, local
  mute / don't-watch / pin / fullscreen; admins get kick/ban plus the dev-hinted room mute /
  room deafen / chat mute, #108). The top-bar invite entry, the "+N" tile and the more-options
  "Invite a friend" row all open `MeetingInviteSheet` (participant avatar grid, share targets —
  system share, copy, inline QR, email, Telegram, WhatsApp — and the raw link). The controls
  bar's handle opens `MeetingMoreOptionsSheet`, which mirrors the five call controls along its
  top and lists deafen, hide-all-cameras (viewer-side data saver), audio settings, the
  dev-hinted noise suppression (#106), invite, and admin room settings. The output picker uses
  trailing radios. `MeetingRecordingBanner` (dev-gated, #107) drops below the top bar from the
  recording dot. There is no side panel anymore — the participants list lives in the invite
  sheet.

## Dev-only affordances

Where UI exists but its backend/business logic doesn't yet, build the UI and mark it with a **dev-only**
hint so it never misleads end users:

- `DevOnly { … }` renders its content only on debug/`dev` builds.
- `DevHintBadge("…")` is a small "not wired up yet" pill (icon + label).

Both are gated by `BuildConfig.DEV_HINTS` (`true` on debug + `dev`, `false` on release) via
`core/DevFlags.kt`. Nothing dev-gated ships to beta/stable users.

## Internationalization

User-facing strings live in `res/values/strings.xml` (+ locale variants: ar, de, es, fa, fr, ja, ru, tr,
zh) — **not** inline in composables. Every string must be translated in all locale files: the project's
lint fails CI on `MissingTranslation`, so shipping English-only is not an option. RTL is fully supported
(layout direction + Vazirmatn/Shabnam fonts via `LocaleHelper`).

## Self-hosting / rebranding

To re-skin, retune the ramps in `Color.kt` (or reseed with Material Theme Builder from the two brand seeds
and paste the result into `Theme.kt`). Because every role and token funnels through the theme layer, a brand
swap is a one-file change — no screen edits.
