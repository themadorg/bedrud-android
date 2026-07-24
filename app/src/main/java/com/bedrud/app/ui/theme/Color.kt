package com.bedrud.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bedrud reference palette — the raw tonal ramps every semantic color role is built from.
 *
 * This is the "reference palette" layer of the design system. Nothing in the UI should read
 * these directly; screens/components always go through [androidx.compose.material3.MaterialTheme]'s
 * `colorScheme` (mapped in Theme.kt). Keeping the ramps here means the whole brand can be
 * re-tuned in one place.
 *
 * Brand seeds:
 *   - Primary  = rose  (#E11D48) — CTAs, focus, brand identity
 *   - Tertiary = teal  (#14B8A6) — accents, highlights, "raised hand" style states
 * Neutrals are warm-tinted (stone) so surfaces feel related to the rose brand rather than clinical.
 *
 * The ramps follow the Material 3 tonal approach (light → dark). They were hand-tuned from the
 * Bedrud web brand; to regenerate a mathematically-derived scheme, reseed with Material Theme
 * Builder using the two seeds above and paste the result back into Theme.kt.
 */

// ── Rose (primary) ─────────────────────────────────────────────────────────
val Rose50 = Color(0xFFFFF1F2)
val Rose100 = Color(0xFFFFE4E6)
val Rose200 = Color(0xFFFECDD3)
val Rose300 = Color(0xFFFDA4AF)
val Rose400 = Color(0xFFFB7185)
val Rose500 = Color(0xFFF43F5E)
val Rose600 = Color(0xFFE11D48)
val Rose700 = Color(0xFFBE123C)
val Rose800 = Color(0xFF9F1239)
val Rose900 = Color(0xFF881337)
val Rose950 = Color(0xFF4C0519)

// ── Teal (tertiary / accent) ───────────────────────────────────────────────
val Teal50 = Color(0xFFF0FDFA)
val Teal100 = Color(0xFFCCFBF1)
val Teal200 = Color(0xFF99F6E4)
val Teal300 = Color(0xFF5EEAD4)
val Teal400 = Color(0xFF2DD4BF)
val Teal500 = Color(0xFF14B8A6)
val Teal600 = Color(0xFF0D9488)
val Teal700 = Color(0xFF0F766E)
val Teal800 = Color(0xFF115E59)
val Teal900 = Color(0xFF134E4A)
val Teal950 = Color(0xFF042F2E)

// ── Red (error) ────────────────────────────────────────────────────────────
val Red50 = Color(0xFFFEF2F2)
val Red100 = Color(0xFFFEE2E2)
val Red200 = Color(0xFFFECACA)
val Red400 = Color(0xFFF87171)
val Red600 = Color(0xFFDC2626)
val Red700 = Color(0xFFB91C1C)
val Red900 = Color(0xFF7F1D1D)
val Red950 = Color(0xFF450A0A)

// ── Warm neutrals (stone) ──────────────────────────────────────────────────
// Surfaces + text. Warm-tinted so they read as part of the rose brand family.
val WarmWhite = Color(0xFFFFFBF9)   // light background / base surface
val Neutral0 = Color(0xFFFFFFFF)
val Neutral50 = Color(0xFFFAF6F4)
val Neutral100 = Color(0xFFF5F0EE)
val Neutral150 = Color(0xFFEFE9E7)
val Neutral200 = Color(0xFFE9E3E1)
val Neutral250 = Color(0xFFE6DFDD)
val Stone200 = Color(0xFFE7E5E4)    // light outline variant
val Stone400 = Color(0xFFA8A29E)    // light outline / dark muted text
val Stone600 = Color(0xFF57534E)    // light muted text / dark outline
val Stone800 = Color(0xFF292524)    // dark surface variant / outline variant
val Stone900 = Color(0xFF1C1917)    // light ink
val Stone950 = Color(0xFF0C0A09)    // dark background

// Dark surface tones (warm, ascending elevation)
val NeutralDark0 = Color(0xFF070605)
val NeutralDark50 = Color(0xFF161311)
val NeutralDark100 = Color(0xFF201C1A)
val NeutralDark150 = Color(0xFF2B2624)
val NeutralDark200 = Color(0xFF363130)
val NeutralDarkText = Color(0xFFF5F5F4)

// ── Muted rose (secondary) ─────────────────────────────────────────────────
// Lower-chroma rose for less-prominent components, so secondary still ties to the brand.
val Mauve600 = Color(0xFF8F5A66)
val Mauve100 = Color(0xFFFCE0E4)
val Mauve900 = Color(0xFF3A1721)
val MauveDark300 = Color(0xFFE5B0BA)
val MauveDark700 = Color(0xFF6E3B46)
val MauveDark900 = Color(0xFF55232E)
