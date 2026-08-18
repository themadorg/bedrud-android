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

Material 3 type scale on **Vazirmatn, applied unconditionally** — the typeface is never selected
from the interface language. It covers the Latin and Arabic scripts, which is every locale the app
ships except Russian, Japanese and Chinese; those fall to the platform's fallback chain (see the
end of this section, tracked in #118).

The font used to be picked from `AppLanguage`: Persian got Shabnam, other RTL languages got
Vazirmatn, everyone else got `FontFamily.SansSerif`. That confuses the language someone *reads*
with the script they *type*. Persian display names, room names and chat messages arrive in an
English interface constantly, and the platform sans has no Arabic-script glyphs, so those names
fell through to the system fallback — on Samsung, `SECNaskhArabic` in its `elegant` variant, a
high-contrast calligraphic book face rendering inside an 11sp tile chip beside Roboto.

Vazirmatn covers both scripts, so the choice disappears. Its Latin glyphs **are** Roboto, merged in
by the font's own build script, so Latin text is unchanged wherever the platform sans already
resolved to Roboto. It is a variable font, and the four weights are real instances of the `wght`
axis rather than four files — which is also what Shabnam could not do: it was static, discontinued
upstream, and registered under four weights that all loaded the same Regular face, so Persian UI
had no weight hierarchy at all.

**The boundary.** Vazirmatn carries no Cyrillic, Greek or CJK, so Russian, Japanese and Chinese
resolve through the platform's fallback chain. That is not a regression — those three resolved the
same way before Vazirmatn became the base font — but it does mean the app draws a different
typeface for them than for everyone else, and on a device whose owner has themed the system font
it will not even be the same one twice. Verified rendering cleanly on a Samsung SM-S928B in all
three; the open question is only whether to bundle a companion face. Tracked in #118.

**Rejected: keeping the platform sans for Latin.** `Typeface.CustomFallbackBuilder` (API 29+) can
leave the system font drawing Latin and hand Vazirmatn only the Arabic-script runs, which would
respect an owner's themed system font. It was turned down because it reintroduces the thing this
change removed — text whose typeface depends on where it happens to be rendered — and because a
design system that pins spacing, shape, elevation and colour has no reason to leave the typeface
to the OEM. Revisit only if themed-font users complain.

## Shape (`Shape.kt`)

Rounded, Material-3-native. Scale: `xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 28 · full`. Semantic tokens:
`field = md`, `button = md`, `card = lg`, `chip = sm`, `pill = full`, `sheetTop = xxl (top corners)`,
`videoTile = xxl`, `controlsBar = xxl` — the last two deliberately equal, since the call screen
floats both on one background and a tile curving at a different rate from the bar beneath it reads
as two unrelated systems rather than one.

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
  with a reconnecting dot when applicable; camera flip (**only while the local camera is live**) and audio output at
  the end. The center block is weight-balanced so the title never shifts as trailing actions
  appear. Connecting is confirmed once — the name slot says "Connected" for
  `Motion.meetingConnectedNoticeMs` and then hands itself back — and that timer lives in
  `MeetingScreen`, not in the bar: the bar is removed from composition whenever the chat panel or
  a fullscreen tile is open, so state held inside it would restart on the way back and announce a
  connection made long ago.
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
- **Tiles**: name chip centered along the bottom edge. The mic-off badge **leads** the chip and the
  speaking badge trails it, so the two states never contend for one slot and neither can be
  mistaken for the other at a glance. A pin badge, when present, sits outermost — it is a state of
  the tile, while the other two are states of the person. There is no
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
- **Chat** (`MeetingChatPanel`, `MeetingChatRow`): consecutive messages from one person, sent
  within `ChatClusterGapMs` of each other, are drawn as a single run — one name and one avatar at
  the top, then a bubble per message with the corners facing the sender's own side tightened so
  the run reads as one block. Senders are told apart by **identity, not display name**: two people
  may pick the same name. The local side gets neither name nor avatar, being already on the
  reader's own side of the panel. There are **no timestamps** on screen — the send time is kept
  only to order the conversation and to decide where a run breaks — and **no empty state**: an
  empty panel over an open call is self-explanatory. Remote avatars take a stable colour hashed
  from identity across the theme's own accent roles, so a busy room stays scannable without a
  palette of its own.
  The list is **reversed**, one item per message rather than one per run: the conversation then
  hangs from the bottom edge without being scrolled there, a long run can still be recycled, and
  arriving messages leave the reader's position alone. Whether to stay at the newest is settled
  when a scroll comes to rest, never while a message is landing — otherwise each arrival reads as
  "the reader has scrolled away" and strands the view a message further back. Your own send
  overrides it and always returns to the latest.
  Messages are ordered by **when they were sent**, not when they arrived: a burst can reach the
  data channel out of order, and a conversation that reads 7, 8, 6 is wrong however it came.
  Chat is a **sheet over the call** (`MeetingChatSheet`), not a screen in front of it. It used to fill
  the window wearing its own `TopAppBar`, on the same background as the call, arriving with a plain
  fade — nothing of the room was left on screen, so it read as somewhere you went rather than
  something the call has. Now the tiles keep rendering behind the scrim, and the header is a plain
  title-and-close row, because an app bar is the signature of a screen.
  Three heights, arbitrated by the platform sheet: half on open, full on drag, gone on a second drag
  down. The keyboard expands it unprompted — a half sheet under a keyboard leaves almost no
  conversation. Fully expanded still stops short of the status bar, so a band of the call stays in view.
  Two layout details are load-bearing. The content keeps its **full height** and the part hanging
  below the screen is removed with bottom padding, rather than being sized to what is visible: sizing
  it that way fed the sheet's own height back into the offset that positioned it, and the sheet
  collapsed into a sliver. And the **drag handle is drawn inside the content**, not in the sheet's
  handle slot, because that slot sits above the content and cannot be subtracted from it — with it in
  place the content overflowed by exactly one handle and took the input dock off the screen with it.
  The composer is **built as the call's controls bar** — same corner token, same container colour,
  same hairline, same `meetingScreenMargin` — rather than as a chat widget of its own. The two sit in
  the same place on screen and swap with each other, so they are the same object with different
  contents. Its attach control is a paperclip: the picker is still images-only, because an image is
  the only attachment the wire carries.
  The field **wraps and grows the bar with it**, up to five lines, then scrolls inside its own
  height. It used to be single-line, which scrolled the text sideways and hid the start of the
  sentence being written — the one part a writer needs in order to finish it. Five lines is a long
  message by the standards of a call, and stopping there keeps the dock from climbing over the
  conversation being answered.
  Attach and poll **show only while the field is empty**. They hold a fixed column on the left, and
  once the field wraps that column is a tall empty block beside the text — while what a writer needs
  at that moment is width, not two controls they are not using. Standing down buys back the whole
  column, which is often a line of the message. They return the moment the field is empty again,
  which is also when attaching a picture or opening a poll is what someone is there to do. The trade
  is deliberate: attaching to a half-written message means clearing it first.
  The controls **sit at the bottom** of the grown bar, beside the line being written, rather than
  floating against the middle of a block of text. Two paddings make that work and they are not
  interchangeable: the bar's own is `space4`, because the icons are `chatDockIcon` tall and anything
  more pushes past the `chatDockBar` minimum and silently grows the one-line dock; the field's is
  `space8`, which brings a single line of text up to the icons' height so bottom-aligning is
  indistinguishable from centring while there is only one line, and which then sits under the last
  line so the bottom-aligned icons land centred on it rather than below it.
  A **long press opens the message menu** (`MeetingChatMessageMenu`), which straddles the bubble:
  the quick reactions as a pill above it, what can be done with the message — **who reacted**, Copy
  and Share — as a card below, and the message itself readable between them. One box containing
  both used to cover the very thing the menu was about. Both surfaces are sized to their contents
  rather than to the panel: stretched wide they read as bands across the conversation, and the far
  end of a full-width row is out of thumb reach. Copy and Share act on the words, so a message
  carrying only a picture offers neither. The bubble does **not** move to make room — lifting it clear the way the
  messengers do needs the whole conversation dimmed behind an overlay, and a menu that jumps the
  list on long press is worse than one that opens quietly where the message already is. Where
  there is no room below, which is every time for the newest message, **both surfaces flip above**
  the bubble rather than sliding up over it; the card records where it landed so the pill can clear
  it. Both hang off the bubble's own outer edge, which swaps sides with the layout direction.
  The gesture used to start selecting text inside the bubble, and both cannot own it — on a
  phone a long press means "act on this message" everywhere else, and copying is what the selecting
  was for.
  **Links in a message are tappable** (`ChatLinks`, `MeetingChatText`): underlined and slightly
  heavier rather than tinted, because a bubble is one of two colours depending on whose message it
  is and no single accent reads on both without fighting one of them. The link is the literal text,
  never anchor text pointing elsewhere, so a message from a stranger cannot dress one address up as
  another. Detection is deliberately conservative — a bare host only counts when it carries a path,
  so `bedrud.xyz/m/standup` is a link and "see you Sept. 11" is not; the cost of missing an exotic
  URL is a copy-paste, the cost of linkifying prose is a tappable target that is always wrong.
  Links open in a **Custom Tab**, the way OAuth does, so closing it returns to the call.
  A link to a room on a server the reader has added is meant to open in the app instead, and the
  code is there — but it is gated off while a call is running, which today is whenever chat is on
  screen. Telecom refuses to place a second call over an unholdable one, so the deep link dead-ends
  in "Cannot place a call as there is an unholdable call". Leaving the current call to follow a
  link is a real feature and a destructive one; until it exists a room link opens the page.
  Tapping a picture opens the
  lightbox, which can **save it to `Pictures/Bedrud`** via `MediaStore` — no permission at all from
  Android 10 on, and `WRITE_EXTERNAL_STORAGE` capped at API 28 for the one older version supported.
  The lightbox says whether it worked for `Motion.lightboxOutcomeNoticeMs` and then gets out of the
  way, rather than raising a snackbar the dialog would cover.
  When chat is off for the room, or a moderator has blocked this person, the input dock is
  replaced by a **warning-coloured** notice saying which — never the error colour, since nothing
  has gone wrong — and the conversation stays readable: restricting who may speak should not
  retract what was already said. Enforcement is client-side by necessity: messages travel
  participant-to-participant over the LiveKit data channel, so the server can only set a
  `chatBlocked` flag on the participant's metadata and trust each client to honour it.
- **Reactions** (`MeetingChatReactions`): one person holds **one reaction per message** — a second
  emoji moves it, the same one again takes it back — drawn as chips under the bubble, the reader's
  own filled the way their own messages are. A chip is deliberately **about half the height of the
  bubble it hangs off** (20dp against a one-line bubble's 38dp) so it reads as an annotation rather
  than a second message. That is smaller than the usual minimum tap target, taken knowingly: it is
  the size chat apps have settled on, and the picker behind a long-press is the recovery path when
  a tap misses. Its box is set by the height token alone — a colour emoji's line box runs taller
  than the label style it is drawn with, so vertical padding on the chip would silently override
  the token. The picker offers a fixed eight rather than the full
  emoji keyboard the web has: a picker large enough to search would cover the conversation being
  reacted to. The pill is **narrower than the eight it holds and scrolls horizontally** — five and
  a half targets wide, so the sixth is half-showing and says there is more behind the edge without
  an arrow to announce it. It caps without stretching, so a set that does fit keeps its own width
  and never scrolls.
  Chips sort by count, and ties keep the order they were first reacted with, so the row
  does not reshuffle when somebody joins a tie. The message menu offers **who reacted** once there
  are any, opening a breakdown grouped by emoji in the order the chips already showed — the
  question a reader has is "who liked this", not "what did each person pick".
- **Polls** (`MeetingChatPoll`, `MeetingChatPollSheet`): composed in a sheet — a question and two to
  six answers — and drawn as the message that carried them. The tally is **on show from the first
  vote** rather than hidden until this reader votes: everyone can see the room anyway, so hiding it
  buys no secrecy and leaves whoever is still deciding with nothing to go on. A vote can be moved
  but never withdrawn, because the wire has no packet that says so and a control working only on
  this device would be a lie. Answers cannot be reordered, unlike the web's drag handles: dragging a
  row inside a sheet that scrolls over a keyboard fights every gesture around it.
  A poll question uses **balanced line breaking** (`LineBreak.Heading`), so a question that wraps
  splits into even lines instead of filling the first and leaving a stub — the default put a dangling
  "now, or" above half an empty line. Reaction chips are padded **asymmetrically** (less on the emoji
  side than the count side): an emoji's ink starts inset inside its glyph box where a digit fills its
  advance width, so equal padding reads lopsided.
  Voter names come from a **cache of everyone the room has seen**, not from its current participant
  list: a vote names an identity and nothing more, so somebody who voted and then left showed up in
  the results as `guest-vblonfbf`, and a voter who never sent a message left no name anywhere else
  to fall back on. The cache lives as long as the room does.
  Both ride the chat topic as packets of their own (`reaction`, `poll_vote`) rather than as new
  versions of the message — nobody may rewrite what somebody else said, and the message being
  reacted to may well predate this device's join. Nothing is re-synced on join, so a reaction or a
  vote cast before arriving is one this device never sees; the other clients have the same hole, and
  closing it means changing the shared protocol rather than this app.
- **More options** (`MeetingMoreOptionsSheet`): the controls row keeps **its bar** inside the sheet —
  same corner, fill and hairline as the collapsed one. Pulled out of that container it was five loose
  shapes on the sheet's surface (a rounded-rect camera, two circles, a wide mic pill, the hang-up)
  with nothing holding the variety together and `button` barely separating from the sheet's own
  container. Expanding now reveals the options *below the bar you were already looking at* rather
  than restyling it mid-drag, so the divider under the row is gone too: the bar's own edge separates.
  Deafen uses a **crossed headphone**, matching the badge on your own tile — deafening is about what
  reaches your ears, where a speaker icon says something about the room.
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
  `HoldMillis` past its last mention to bridge the gap.

  **Your own ring is bridged locally.** The server reports speakers when the set changes rather
  than on a clock: measured on a real call, gaps between two reports naming the same speaker ran
  to a median of 800ms with a tail past three seconds, so a hold long enough to survive a sentence
  would leave every ring lit seconds after its owner stopped. No single hold fixes both. For the
  one participant this device can measure directly, the locally captured level therefore fills the
  gaps — held past the last loud frame like the voice warning, since speech dips below the bar
  between every pair of words. The server still decides whether the ring may light at all: the
  bridge applies only while the room has confirmed hearing this device within the last few seconds,
  and never while muted, so the ring keeps meaning "the room hears me" and not "my microphone
  works". Remote participants cannot be bridged and keep the server's timing. **The local participant is in that server
  list like anyone else**, which is the entire point: your own ring lighting up is round-trip
  proof that your audio reached the SFU and was announced to the room, where the mic meter only
  proves the microphone works. Colour never carries it alone — speech also earns a badge in the
  name chip (`SpeakingBadge`), trailing the name.

  **The badge calms, it does not leave.** It is always in the chip, brightening to the accent while
  the room hears someone and settling back to a faint outline when it stops. An icon that came and
  went would flicker through every pause between sentences — speech is bursty, and those pauses are
  constant — and would shove the name sideways on each one. Fading in place is the same choice the
  ring makes for the same reason, and both run on `Motion.meetingSpeakingFadeMs` so they can never
  disagree about when someone started talking. Only presence is shown, not loudness: the ring
  already carries level in its thickness, and a badge this small cannot render a magnitude legibly
  enough to be worth the motion. A **muted** participant's grid tile drops the badge entirely
  rather than showing it calm — they can never be speaking, so its resting state would only repeat
  what the mic-off badge beside the name already said. The fullscreen chip keeps it
  unconditionally, having no mute badge to make it redundant.
- **Mute is a soft mute.** Muting through LiveKit disables the underlying track, and a disabled
  track stops feeding the capture chain — so a plain mute leaves nothing to measure and no way to
  notice you talking into a muted microphone. The track therefore stays enabled and the room is
  kept from hearing anything by two independent means: the **publication is muted**, which is what
  every other participant's mute indicator reads and what the server is told, and **every captured
  frame is zeroed** by `VoiceGateProcessor.forceSilence` before it can reach the encoder. The
  silencing is switched on *before* the track is re-enabled, never after. Joining muted publishes
  first (already silenced) and then mutes, because an unpublished track never reaches the capture
  chain at all. The honest cost: while muted the microphone is genuinely open, so the system's mic
  indicator stays lit. Nothing audible can leave the device, but audio is being captured on it.

  The override that covers the publish window is **only** ever that: it is handed back in a
  `finally`, and being muted is never represented by it. LiveKit's `setMicrophoneEnabled` returns
  early whenever it already agrees with the requested state, skipping the call path that would
  clear it — so a flag used for steady-state muting gets stranded set, and the microphone stays
  silent behind a button that says it is open, with both indicators dead because nothing is
  reaching the room.

  **The guard is a question, not a flag.** `VoiceGateProcessor.roomMayHear` is asked on *every*
  10ms frame and answered from the single source of truth — the app's mute state and LiveKit's
  publication must **both** say the microphone is open. A flag would have to be set correctly at
  every transition, and the one that is missed (an unmute that fails after the flag was cleared, a
  path added later that forgets it) is a live microphone behind a muted button; a question has no
  window to get wrong. It **fails closed** in every direction: the default denies, so a processor
  that was never wired transmits silence rather than audio; a missing publication or absent room
  denies; and an exception while deciding denies. The gate's own rules are checked *after* it, so
  no sensitivity setting can re-open a muted microphone. `VoiceGateProcessorTest` pins each of
  these, including that the level is still measured while muted — silence goes out, the voice is
  still heard locally, which is the entire point.
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
  trailing radios. `MeetingRecordingBanner` and the dot that opened it are **switched off** behind
  `RecordingIndicatorEnabled` (#107): the server has no egress client and registers no recording
  routes, so nothing in the app can be recording, and a permanently lit privacy light above a
  banner claiming every camera and message is captured is worse than none at all. The UI is kept
  and still compiles so the flag turns it back on. There is no side panel anymore — the
  participants list lives in the invite sheet.

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
lint fails CI on `MissingTranslation`, so shipping English-only is not an option. RTL is fully supported:
`LocaleHelper` and `BedrudTheme` set the layout direction from the active `AppLanguage`, while the
typeface does not vary by locale at all — see [Typography](#typography-typekt).

## Self-hosting / rebranding

To re-skin, retune the ramps in `Color.kt` (or reseed with Material Theme Builder from the two brand seeds
and paste the result into `Theme.kt`). Because every role and token funnels through the theme layer, a brand
swap is a one-file change — no screen edits.
