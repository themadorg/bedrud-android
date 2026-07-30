package com.bedrud.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A colored circle showing the first letter of [name], uppercased — the app's initials avatar,
 * used for servers (tinted with the instance color) and people (participants, the profile card).
 *
 * [fallbackInitial] is shown when [name] is null or blank: "?" by default; callers that render
 * genuinely nameless entries (e.g. meeting participants before their name arrives) pass "" to keep
 * the circle empty, and the auth brand mark passes "B".
 */
@Composable
fun InitialsAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    fallbackInitial: String = "?",
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = (name?.takeIf { it.isNotBlank() } ?: fallbackInitial).take(1).uppercase(),
            style = textStyle,
            color = contentColor
        )
    }
}
