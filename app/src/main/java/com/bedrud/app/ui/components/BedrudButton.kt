package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.typeCentered

enum class BedrudButtonVariant {
    PRIMARY,
    SECONDARY,
    TONAL,
    OUTLINE,
    GHOST,
    DESTRUCTIVE
}

/**
 * The padding every variant puts around its content. At the default font size the vertical half
 * changes nothing, since the minimum height is taller than a line plus this padding. It matters
 * once the label outgrows that minimum — a raised font size, or a label that wraps — and keeps
 * the text off the button's edges.
 */
private val ContentPadding = PaddingValues(horizontal = Dimens.space24, vertical = Dimens.space8)

@Composable
fun BedrudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BedrudButtonVariant = BedrudButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val shape = BedrudShapeTokens.button

    when (variant) {
        BedrudButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = Dimens.buttonHeight),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = ContentPadding
            ) {
                ButtonContent(text, loading, leadingIcon, trailingIcon)
            }
        }

        BedrudButtonVariant.SECONDARY -> {
            Button(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = Dimens.buttonHeight),
                enabled = enabled && !loading,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                contentPadding = ContentPadding
            ) {
                ButtonContent(text, loading, leadingIcon, trailingIcon)
            }
        }

        BedrudButtonVariant.TONAL -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = Dimens.buttonHeight),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = ContentPadding
            ) {
                ButtonContent(text, loading, leadingIcon, trailingIcon)
            }
        }

        BedrudButtonVariant.OUTLINE -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = Dimens.buttonHeight),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = ContentPadding
            ) {
                ButtonContent(text, loading, leadingIcon, trailingIcon)
            }
        }

        BedrudButtonVariant.GHOST -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = Dimens.buttonHeight),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = ContentPadding
            ) {
                ButtonContent(text, loading, leadingIcon, trailingIcon)
            }
        }

        BedrudButtonVariant.DESTRUCTIVE -> {
            Button(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = Dimens.buttonHeight),
                enabled = enabled && !loading,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = ContentPadding
            ) {
                ButtonContent(text, loading, leadingIcon, trailingIcon)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconSm),
            strokeWidth = 2.dp,
            color = LocalContentColor.current
        )
        Spacer(modifier = Modifier.width(Dimens.space8))
    }

    if (!loading) {
        leadingIcon?.let {
            it()
            Spacer(modifier = Modifier.width(Dimens.space8))
        }
    }

    val labelStyle = MaterialTheme.typography.labelLarge
    Text(
        text = text,
        style = labelStyle,
        // A button is a fixed-height container, and with a leading or trailing icon the label is
        // also centred against something that is not text. Corrected per style rather than per
        // string, so two buttons side by side keep one baseline even when only one of their
        // labels has a descender.
        modifier = Modifier.typeCentered(labelStyle),
    )

    trailingIcon?.let {
        Spacer(modifier = Modifier.width(Dimens.space8))
        it()
    }
}
