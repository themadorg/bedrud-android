package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.inkCentered

/**
 * A short label in a pill beside what it describes — "Recommended" on a server choice, "Admin"
 * beside a name. One badge for every such label, so they share one shape, one colour and one size
 * wherever they appear.
 *
 * The label is centred on its own letters, as an avatar's initial is, rather than on a Latin
 * capital's: it is alone in a shape barely taller than it, with no neighbour to share a baseline
 * with. Centred on a capital, a Persian label's tails hung below the pill's middle and left it
 * 1.5dp low.
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
                .inkCentered(text, badgeStyle),
        )
    }
}
