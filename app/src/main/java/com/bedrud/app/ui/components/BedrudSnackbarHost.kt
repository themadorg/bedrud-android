package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.bedrudColors
import com.bedrud.app.ui.theme.typeCentered

/** What a message is about, which decides the icon it leads with. */
enum class BedrudSnackbarKind { INFO, SUCCESS, WARNING, ERROR }

/**
 * How long a message stays, as Material's own snackbar decides it: until its action is taken when
 * it has one, briefly otherwise. Not longer for an error: a caller often keeps its progress state
 * until the message is put away, and a longer message held a spinner on screen for as long.
 */
private fun defaultDuration(actionLabel: String?): SnackbarDuration =
    if (actionLabel != null) SnackbarDuration.Indefinite else SnackbarDuration.Short

/** A message and its [kind]. An error carries a close button, to put it away once it is read. */
data class BedrudSnackbarVisuals(
    override val message: String,
    val kind: BedrudSnackbarKind,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = kind == BedrudSnackbarKind.ERROR,
    override val duration: SnackbarDuration = defaultDuration(actionLabel),
) : SnackbarVisuals

/** Shows [message] as a [kind] message, and returns how it was put away. */
suspend fun SnackbarHostState.showMessage(
    message: String,
    kind: BedrudSnackbarKind,
    actionLabel: String? = null,
    duration: SnackbarDuration = defaultDuration(actionLabel),
): SnackbarResult = showSnackbar(BedrudSnackbarVisuals(message, kind, actionLabel, duration = duration))

/**
 * The icon a [kind] leads with, named for a screen reader. Each status takes its inverse color,
 * since the snackbar sits on the inverse surface; information stays in the message's own color.
 */
@Composable
private fun SnackbarKindIcon(kind: BedrudSnackbarKind) {
    val (icon, tint, nameResource) = when (kind) {
        BedrudSnackbarKind.INFO ->
            Triple(Icons.Outlined.Info, MaterialTheme.colorScheme.inverseOnSurface, R.string.common_contentDescription_info)
        BedrudSnackbarKind.SUCCESS ->
            Triple(Icons.Outlined.CheckCircle, MaterialTheme.bedrudColors.inverseSuccess, R.string.common_contentDescription_success)
        BedrudSnackbarKind.WARNING ->
            Triple(Icons.Outlined.WarningAmber, MaterialTheme.bedrudColors.inverseWarning, R.string.common_contentDescription_warning)
        BedrudSnackbarKind.ERROR ->
            Triple(Icons.Outlined.ErrorOutline, MaterialTheme.bedrudColors.inverseError, R.string.common_contentDescription_error)
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(nameResource),
        tint = tint,
        modifier = Modifier.size(Dimens.iconMd),
    )
}

/**
 * App-wide snackbar host.
 *
 * The standard Material 3 [Snackbar] — its inverse colors, action and close button laid out as
 * Material's own `Snackbar(snackbarData)` lays them out — with two additions of the app's own: the
 * rounded shape token, and a leading icon saying what kind of message it is, for a message shown
 * through [showMessage]. Material's snackbar has no leading icon; one is added here, in this one
 * place, because a failure and a confirmation otherwise look identical.
 */
@Composable
fun BedrudSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState, modifier) { data ->
        val visuals = data.visuals
        val kind = (visuals as? BedrudSnackbarVisuals)?.kind
        Snackbar(
            modifier = Modifier.padding(Dimens.space12),
            shape = BedrudShapeTokens.snackbar,
            action = visuals.actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                    ) { Text(label) }
                }
            },
            dismissAction = if (visuals.withDismissAction) {
                {
                    IconButton(onClick = data::dismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_contentDescription_dismiss),
                        )
                    }
                }
            } else null,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
            ) {
                kind?.let { SnackbarKindIcon(it) }
                val messageStyle = MaterialTheme.typography.bodyMedium
                Text(
                    text = visuals.message,
                    style = messageStyle,
                    modifier = Modifier.typeCentered(messageStyle),
                )
            }
        }
    }
}
