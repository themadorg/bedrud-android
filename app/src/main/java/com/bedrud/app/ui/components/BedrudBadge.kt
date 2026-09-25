package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.typeCentered

/**
 * A short label in a pill beside what it describes — "Recommended" on a server choice, "Admin"
 * beside a name. One badge for every such label, so they share one shape, one colour and one size
 * wherever they appear.
 */
@Composable
fun BedrudBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = BedrudShapeTokens.pill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        val badgeStyle = MaterialTheme.typography.labelSmall
        Text(
            text = text,
            style = badgeStyle,
            modifier = Modifier
                .padding(horizontal = Dimens.space8, vertical = Dimens.space2)
                .typeCentered(badgeStyle),
        )
    }
}
