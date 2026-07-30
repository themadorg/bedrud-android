package com.bedrud.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bedrud.app.ui.theme.BedrudRadius

@Composable
fun BedrudOutlinedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(BedrudRadius.md),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            border = border,
            colors = colors,
            elevation = elevation,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            border = border,
            colors = colors,
            elevation = elevation,
            content = content
        )
    }
}

@Composable
fun BedrudCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(BedrudRadius.md)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            border = border,
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            CardContent(title, subtitle, content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            border = border,
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            CardContent(title, subtitle, content)
        }
    }
}

/**
 * The small primary-tinted section label at the top of a settings/profile/admin card. Pass the
 * card's own edge padding via [modifier] when the header sits directly in an unpadded card column.
 */
@Composable
fun CardSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun ColumnScope.CardContent(
    title: String?,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (title != null || subtitle != null) {
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        content()
    }
}
