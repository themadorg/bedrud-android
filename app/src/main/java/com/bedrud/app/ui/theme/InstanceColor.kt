package com.bedrud.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Neutral blue used when an instance color is missing or malformed. */
private val InstanceColorFallback = Color(0xFF3B82F6)

/**
 * Parses a `#RRGGBB` instance color into a Compose [Color], falling back to a neutral blue when the
 * value is absent or malformed.
 *
 * Single source of truth for the per-server accent color, shared by the auth server header, the
 * profile server row, and the rooms list (the leading stripe + colored server name on each card).
 */
fun parseInstanceColor(hex: String?): Color {
    val cleaned = hex?.trimStart('#') ?: return InstanceColorFallback
    if (cleaned.length != 6) return InstanceColorFallback
    return try {
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (_: Exception) {
        InstanceColorFallback
    }
}
