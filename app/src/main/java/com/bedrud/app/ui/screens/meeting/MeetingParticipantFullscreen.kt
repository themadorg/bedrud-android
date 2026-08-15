package com.bedrud.app.ui.screens.meeting

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Scrim opacity behind the fullscreen chrome (name chip, collapse button). */
private const val FullscreenChipAlpha = 0.7f

/**
 * A single participant expanded to fill the call screen.
 *
 * Chrome (the name chip, the collapse button, and — at the screen level — the controls bar)
 * auto-hides after [Motion.meetingChromeAutoHideDelayMs] of inactivity, and any tap toggles it
 * back; while hidden the system bars are hidden too, so the view is fully immersive. The caller
 * owns the chrome-visibility state; this composable runs the auto-hide timer and the system-bar
 * side effect.
 */
@Composable
fun MeetingParticipantFullscreen(
    participant: Participant,
    room: Room,
    participantVersion: Int,
    chromeVisible: Boolean,
    isVideoLocallyDisabled: Boolean,
    speakingLevel: Float,
    onToggleChrome: () -> Unit,
    onAutoHideChrome: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val trackVersion = participantVersion

    val identity = participant.identity?.value ?: RoomManager.UNKNOWN_PARTICIPANT_NAME
    val name = participant.name?.ifBlank { identity } ?: identity

    // Chrome auto-hide: (re)arm whenever the chrome becomes visible.
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(Motion.meetingChromeAutoHideDelayMs)
            onAutoHideChrome()
        }
    }

    // Immersive mode: hide the system bars while the chrome is hidden, restore on exit.
    val view = LocalView.current
    DisposableEffect(chromeVisible) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            if (chromeVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val screenShareRef = resolveParticipantScreenShare(participant)
    val cameraPublication = participant.getTrackPublication(Track.Source.CAMERA)
    val cameraTrack = cameraPublication
        ?.track as? io.livekit.android.room.track.VideoTrack
    val isCameraMuted = cameraPublication?.muted == true

    val avatarUrl = remember(participant.metadata) {
        participant.metadata?.let { meta ->
            try {
                val obj = JSONObject(meta)
                if (obj.has("avatarUrl")) obj.getString("avatarUrl") else null
            } catch (_: Exception) { null }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures { onToggleChrome() }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            screenShareRef != null && screenShareRef.isRenderable -> {
                VideoTrackView(
                    trackReference = screenShareRef.trackReference,
                    modifier = Modifier.fillMaxSize(),
                    room = room,
                    mirror = false,
                    scaleType = ScaleType.FitInside,
                )
            }
            cameraTrack != null && !isCameraMuted && !isVideoLocallyDisabled -> {
                VideoTrackView(
                    videoTrack = cameraTrack,
                    modifier = Modifier.fillMaxSize(),
                    passedRoom = room,
                )
            }
            !avatarUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = stringResource(R.string.meeting_contentDescription_participantAvatar),
                    modifier = Modifier
                        .size(Dimens.meetingFullscreenAvatar)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                InitialsAvatar(
                    name = name,
                    size = Dimens.meetingFullscreenAvatar,
                    textStyle = MaterialTheme.typography.displaySmall,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    fallbackInitial = ""
                )
            }
        }

        if (chromeVisible) {
            // Name chip, top center — mirrors the grid tile's chip position language, speaking
            // badge included. A ring around a screen-filling view would read as an edge artifact
            // rather than as a state, so here the chip carries the signal on its own.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(Dimens.space8)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = FullscreenChipAlpha),
                        BedrudShapeTokens.chip,
                    )
                    .padding(horizontal = Dimens.space8, vertical = Dimens.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (speakingLevel > 0f) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.meeting_contentDescription_speaking),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.meetingBadgeIcon),
                    )
                }
            }

            Surface(
                onClick = onCollapse,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = FullscreenChipAlpha),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(Dimens.space8)
                    .size(Dimens.meetingTileAction),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = stringResource(R.string.meeting_contentDescription_tileExitFullscreen),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            }
        }
    }
}
