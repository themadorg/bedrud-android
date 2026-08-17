package com.bedrud.app.ui.screens.meeting

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.util.PlainTextMimeType
import com.bedrud.app.ui.util.sharePlainText
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch

/** One participant entry for the invite sheet's avatar grid. */
data class InviteSheetParticipant(
    val identity: String,
    val name: String,
    val avatarUrl: String?,
    val isLocal: Boolean,
    /** The room's reported speaking level (0..1) — see `RoomManager.speakingLevels`. */
    val speakingLevel: Float = 0f,
)

private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
private const val WHATSAPP_PACKAGE = "com.whatsapp"

/**
 * The participants + invite sheet (top-bar invite entry, the grid's "+N" tile, and the
 * more-options "Invite a friend" row all land here): everyone in the room as an avatar grid, then
 * the share targets and the raw room link.
 */
@Composable
fun MeetingInviteSheet(
    participants: List<InviteSheetParticipant>,
    roomLink: String,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.meeting_share_chooserTitle)
    val appNotInstalledMessage = stringResource(R.string.meeting_invite_appNotInstalled)
    var showQr by remember { mutableStateOf(false) }

    BedrudBottomSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.meeting_panel_participants, participants.size),
            color = colors.onButton,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space8),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(InviteGridColumns),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = Dimens.inviteGridMaxHeight),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
            verticalArrangement = Arrangement.spacedBy(Dimens.space12),
        ) {
            items(participants, key = { it.identity }) { participant ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.space4),
                ) {
                    // The avatar outline is the list's version of the tile ring — the same signal
                    // in the place people look to check who is in the room.
                    if (!participant.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = participant.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(Dimens.avatarLg)
                                .clip(CircleShape)
                                .speakingRing(participant.speakingLevel, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        InitialsAvatar(
                            name = participant.name,
                            size = Dimens.avatarLg,
                            fallbackInitial = "",
                            modifier = Modifier.speakingRing(participant.speakingLevel, CircleShape),
                        )
                    }
                    Text(
                        text = if (participant.isLocal) {
                            stringResource(R.string.meeting_label_you)
                        } else {
                            participant.name
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onButtonVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        HorizontalDivider(
            color = colors.divider,
            modifier = Modifier.padding(vertical = Dimens.space8),
        )

        if (showQr) {
            val qrBitmap = remember(roomLink) { encodeRoomLinkQr(roomLink) }
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.space8),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.meeting_invite_qrDescription),
                        modifier = Modifier
                            .size(Dimens.inviteQrSize)
                            .clip(BedrudShapeTokens.card)
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(Dimens.space8),
                    )
                }
            }
        }

        // Share targets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = Dimens.space8),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space16),
        ) {
            InviteTarget(
                colors = colors,
                icon = Icons.Default.Share,
                label = stringResource(R.string.meeting_invite_sendInvite),
                onClick = {
                    context.sharePlainText(roomLink, chooserTitle)
                },
            )
            InviteTarget(
                colors = colors,
                icon = Icons.Default.ContentCopy,
                label = stringResource(R.string.meeting_invite_copyLink),
                onClick = onCopyLink,
            )
            InviteTarget(
                colors = colors,
                icon = Icons.Default.QrCode2,
                label = stringResource(R.string.meeting_invite_qrCode),
                onClick = { showQr = !showQr },
            )
            InviteTarget(
                colors = colors,
                icon = Icons.Default.Email,
                label = stringResource(R.string.meeting_invite_email),
                onClick = {
                    shareTo(context, roomLink, packageName = null, email = true) {
                        scope.launch { snackbarHostState.showSnackbar(appNotInstalledMessage) }
                    }
                },
            )
            InviteTarget(
                colors = colors,
                // No Telegram glyph in material-icons-extended; the send plane is the closest fit.
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.meeting_invite_telegram),
                onClick = {
                    shareTo(context, roomLink, TELEGRAM_PACKAGE) {
                        scope.launch { snackbarHostState.showSnackbar(appNotInstalledMessage) }
                    }
                },
            )
            InviteTarget(
                colors = colors,
                icon = Icons.Default.Whatsapp,
                label = stringResource(R.string.meeting_invite_whatsapp),
                onClick = {
                    shareTo(context, roomLink, WHATSAPP_PACKAGE) {
                        scope.launch { snackbarHostState.showSnackbar(appNotInstalledMessage) }
                    }
                },
            )
        }

        // The raw link, tappable to copy
        Surface(
            onClick = onCopyLink,
            shape = BedrudShapeTokens.field,
            color = colors.button,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = Dimens.space12,
                    vertical = Dimens.space12,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
            ) {
                Text(
                    text = roomLink,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onButton,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.meeting_invite_copyLink),
                    tint = colors.onButtonVariant,
                    modifier = Modifier.size(Dimens.iconSm),
                )
            }
        }
    }
}

private const val InviteGridColumns = 4

@Composable
private fun InviteTarget(
    colors: MeetingChromeColors,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.button,
            modifier = Modifier.size(Dimens.inviteTargetSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onButton,
                    modifier = Modifier.size(Dimens.iconMd),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onButtonVariant,
            maxLines = 1,
        )
    }
}

/**
 * Send the link to a specific app ([packageName]), the email composer ([email]), or fail over to
 * [onNotInstalled] when the target can't handle it.
 */
private fun shareTo(
    context: Context,
    roomLink: String,
    packageName: String?,
    email: Boolean = false,
    onNotInstalled: () -> Unit,
) {
    val intent = if (email) {
        Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(Intent.EXTRA_TEXT, roomLink)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = PlainTextMimeType
            putExtra(Intent.EXTRA_TEXT, roomLink)
            setPackage(packageName)
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onNotInstalled()
    }
}

/** Renders the room link as a QR bitmap; null when encoding fails (never expected for a URL). */
private fun encodeRoomLinkQr(roomLink: String): Bitmap? = try {
    BarcodeEncoder().encodeBitmap(roomLink, BarcodeFormat.QR_CODE, QrPixelSize, QrPixelSize)
} catch (_: Exception) {
    null
}

private const val QrPixelSize = 512
