# Bedrud Android — Design System

Material 3 (native, expressive), rounded, token-driven. A warm **rose + teal** brand on warm-neutral
surfaces. Everything visual flows through a token layer under `app/src/main/java/com/bedrud/app/ui/theme/`
so there are no magic values scattered through screens.

> This document describes the **Android app**. The original web app used a different, sharper
> "rose + teal, 0px-radius" system; that is not what this client ships. Treat this file as the source
> of truth for Android UI work.

## Token layer (`ui/theme/`)

| File | Holds | Rule |
|------|-------|------|
| `Color.kt` | Reference palette — the raw rose/teal/neutral/red tonal ramps | Never read directly from UI |
| `Theme.kt` | `BedrudTheme` + the light/dark `ColorScheme` role mapping (full M3 role set) | UI reads `MaterialTheme.colorScheme.*` |
| `Type.kt` | `Typography` (M3 type scale) + RTL font families | UI reads `MaterialTheme.typography.*` |
| `Shape.kt` | `BedrudShapes` (M3 scale) + `BedrudShapeTokens` (semantic: card/field/button/pill) | No raw `RoundedCornerShape(n.dp)` |
| `Dimens.kt` | Spacing scale (4dp grid) + component sizes/heights/icon sizes | No raw `n.dp` for spacing/sizing |
| `Elevation.kt` | Tonal elevation levels | Surfaces stay low (outline-first) |
| `Motion.kt` | Durations + easing for transitions | No inline animation timings |

**The rule:** screens and components reference `MaterialTheme.*` + the token objects. Raw hex colors and
raw `n.dp` literals don't belong in `ui/screens/**` or `ui/components/**`.

## Color

Brand seeds:

- **Primary — rose `#E11D48`** — CTAs, selection, focus, brand identity.
- **Tertiary — teal `#14B8A6`** — accents, "recommended"/info affordances, highlight states.
- **Secondary — muted rose** — lower-emphasis components that still tie to the brand.
- **Neutrals — warm stone** — surfaces/text read as part of the rose family, not clinical grey.
- **Error — red `#DC2626`** — reserved for errors and irreversible/destructive actions.

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

## Components (`ui/components/`)

- **`BedrudButton`** — 5 variants (PRIMARY, SECONDARY, OUTLINE, GHOST, DESTRUCTIVE). Token-driven height
  (`defaultMinSize(buttonHeight)`, so callers can grow it, e.g. `height(buttonHeightLarge)` for a full CTA),
  shape (`BedrudShapeTokens.button`), and padding. Built-in `loading` state.
- **`BedrudCard` / `BedrudOutlinedCard`** — outline-first cards, tonal surface, minimal elevation.
- **Selectable cards** (e.g. the server chooser) — a `selectableGroup()` of `Surface`s marked
  `selectable(role = RadioButton)`, selection shown by a radio **and** a primary border.
- **`DevOnly` / `DevHintBadge`** — see below.

## Dev-only affordances

Where UI exists but its backend/business logic doesn't yet, build the UI and mark it with a **dev-only**
hint so it never misleads end users:

- `DevOnly { … }` renders its content only on debug/`dev` builds.
- `DevHintBadge("…")` is a small "not wired up yet" pill (icon + label).

Both are gated by `BuildConfig.DEV_HINTS` (`true` on debug + `dev`, `false` on release) via
`core/DevFlags.kt`. Nothing dev-gated ships to beta/stable users.

## Internationalization

User-facing strings live in `res/values/strings.xml` (+ locale variants: ar, de, es, fa, fr, ja, ru, tr,
zh) — **not** inline in composables. Missing translations fall back to the default (English). RTL is fully
supported (layout direction + Vazirmatn/Shabnam fonts via `LocaleHelper`).

## Self-hosting / rebranding

To re-skin, retune the ramps in `Color.kt` (or reseed with Material Theme Builder from the two brand seeds
and paste the result into `Theme.kt`). Because every role and token funnels through the theme layer, a brand
swap is a one-file change — no screen edits.
