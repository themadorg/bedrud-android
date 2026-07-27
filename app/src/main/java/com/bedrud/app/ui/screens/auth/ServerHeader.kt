package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * The active server's brand mark (colored circle + initial), name, and URL, stacked and centered.
 *
 * Shared across the auth flow — the sign-in hub and the email login screen both render it — so the
 * mark stays at the exact same position and styling between steps (shared-element continuity).
 */
@Composable
internal fun ServerHeader(
    displayName: String?,
    serverUrl: String?,
    iconColorHex: String?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(Dimens.brandMark)
                .clip(BedrudShapeTokens.pill)
                .background(
                    iconColorHex?.let(::parseInstanceColor)
                        ?: MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (displayName ?: "B").take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
        Spacer(Modifier.height(Dimens.space16))
        Text(
            text = displayName ?: stringResource(R.string.instance_default_displayName),
            style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (serverUrl != null) {
            Spacer(Modifier.height(Dimens.space4))
            Text(
                text = serverUrl.trimEnd('/'),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Parses a `#RRGGBB` instance color, falling back to a neutral blue when absent or malformed. */
internal fun parseInstanceColor(hex: String): Color {
    val cleaned = hex.trimStart('#')
    if (cleaned.length != 6) return Color(0xFF3B82F6)
    return try {
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }
}
