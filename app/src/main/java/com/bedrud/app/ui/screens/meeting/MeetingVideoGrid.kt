package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import org.json.JSONObject

/** Most tiles the grid shows at once; beyond this the last slot collapses into a "+N" tile. */
private const val MAX_GRID_TILES = 6

/** Tiles per row (portrait) for a given visible-tile count; transposed in landscape. */
private fun gridRows(count: Int): List<Int> = when (count) {
    0 -> emptyList()
    1 -> listOf(1)
    2 -> listOf(1, 1)
    3 -> listOf(1, 1, 1)
    4 -> listOf(2, 2)
    5 -> listOf(2, 2, 1)
    else -> listOf(2, 2, 2)
}

/**
 * The participant grid. [tiles] is the already-filtered list to display — the local participant
 * only while their camera is on, then the remote participants. Counts up to [MAX_GRID_TILES] lay
 * out per the design's breakpoints (full-width rows up to 3, then two columns); anything beyond
 * collapses into a trailing "+N" overflow tile that opens the participants list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingVideoGrid(
    tiles: List<Participant>,
    room: Room,
    localIdentity: String?,
    stageScreenShareIdentity: String?,
    disabledVideoIdentities: Set<String>,
    hideAllIncomingVideo: Boolean,
    mutedIdentities: Set<String>,
    pinnedIdentity: String?,
    onOpenParticipantActions: (String) -> Unit,
    onExpandTile: (String) -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = if (tiles.size > MAX_GRID_TILES) tiles.take(MAX_GRID_TILES - 1) else tiles
    val overflowCount = tiles.size - visible.size
    val slotCount = visible.size + if (overflowCount > 0) 1 else 0
    val rows = gridRows(slotCount)
    // Below 4 tiles rows are full-width; from 4 up the grid is two-column, so a lone tile in the
    // last row renders at half width, centered (the design's 5-tile layout).
    val twoColumnGrid = slotCount >= 4

    val tileContent: @Composable (slotIndex: Int, slotModifier: Modifier) -> Unit =
        { slotIndex, slotModifier ->
            if (slotIndex < visible.size) {
                val participant = visible[slotIndex]
                ParticipantTile(
                    participant = participant,
                    isLocalParticipant = participant.identity?.value == localIdentity,
                    room = room,
                    stageScreenShareIdentity = stageScreenShareIdentity,
                    disabledVideoIdentities = disabledVideoIdentities,
                    hideAllIncomingVideo = hideAllIncomingVideo,
                    mutedIdentities = mutedIdentities,
                    isPinned = participant.identity?.value == pinnedIdentity,
                    onOpenParticipantActions = onOpenParticipantActions,
                    onExpand = onExpandTile,
                    modifier = slotModifier,
                )
            } else {
                OverflowTile(
                    count = overflowCount,
                    onClick = onOverflowClick,
                    modifier = slotModifier,
                )
            }
        }

    BoxWithConstraints(modifier = modifier) {
        val landscape = maxWidth > maxHeight
        if (!landscape) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Dimens.meetingTileGap),
            ) {
                var slotIndex = 0
                rows.forEach { rowCount ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.meetingTileGap),
                    ) {
                        HalfWidthCentered(single = rowCount == 1 && twoColumnGrid) {
                            repeat(rowCount) {
                                tileContent(
                                    slotIndex,
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                                slotIndex++
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.meetingTileGap),
            ) {
                var slotIndex = 0
                rows.forEach { columnCount ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.meetingTileGap),
                    ) {
                        repeat(columnCount) {
                            tileContent(
                                slotIndex,
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                            slotIndex++
                        }
                    }
                }
            }
        }
    }
}

/** Centers a lone two-column-grid tile at half width by flanking it with equal spacers. */
@Composable
private fun RowScope.HalfWidthCentered(
    single: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    if (single) {
        Spacer(modifier = Modifier.weight(0.5f))
        content()
        Spacer(modifier = Modifier.weight(0.5f))
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ParticipantTile(
    participant: Participant,
    isLocalParticipant: Boolean = false,
    room: Room? = null,
    stageScreenShareIdentity: String? = null,
    disabledVideoIdentities: Set<String> = emptySet(),
    hideAllIncomingVideo: Boolean = false,
    mutedIdentities: Set<String> = emptySet(),
    isPinned: Boolean = false,
    onOpenParticipantActions: ((String) -> Unit)? = null,
    onExpand: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val identity = participant.identity?.value ?: RoomManager.UNKNOWN_PARTICIPANT_NAME
    val isVideoLocallyDisabled =
        identity in disabledVideoIdentities || (!isLocalParticipant && hideAllIncomingVideo)
    val isLocallyMuted = identity in mutedIdentities

    val screenShareRef = if (identity != stageScreenShareIdentity) {
        resolveParticipantScreenShare(participant)
    } else {
        null
    }

    val cameraPublication = participant.getTrackPublication(Track.Source.CAMERA)
    val cameraTrack = cameraPublication
        ?.track as? io.livekit.android.room.track.VideoTrack
    val isCameraMuted = cameraPublication?.muted == true
    val isCameraOff = cameraTrack == null || isCameraMuted
    val micPublication = participant.getTrackPublication(Track.Source.MICROPHONE)
    val isMicOff = micPublication == null || micPublication.muted
    val name = participant.name?.ifBlank { identity } ?: identity

    // Parse avatar URL from participant metadata
    val avatarUrl = remember(participant.metadata) {
        participant.metadata?.let { meta ->
            try {
                val obj = JSONObject(meta)
                if (obj.has("avatarUrl")) obj.getString("avatarUrl") else null
            } catch (_: Exception) { null }
        }
    }

    val canOpenMenu = !isLocalParticipant && onOpenParticipantActions != null

    Box(
        modifier = modifier
            .clip(BedrudShapeTokens.videoTile)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (canOpenMenu) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onOpenParticipantActions?.invoke(identity) }
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            screenShareRef != null && screenShareRef.isRenderable && room != null -> {
                VideoTrackView(
                    trackReference = screenShareRef.trackReference,
                    modifier = Modifier.fillMaxSize(),
                    room = room,
                    mirror = false,
                    scaleType = ScaleType.FitInside,
                )
            }
            cameraTrack != null && !isCameraMuted && !isVideoLocallyDisabled && room != null -> {
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
                        .size(Dimens.meetingTileAvatar)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                // Initials placeholder
                InitialsAvatar(
                    name = name,
                    size = Dimens.meetingTileAvatar,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    fallbackInitial = ""
                )
            }
        }

        // Name chip with media-state badges, centered along the bottom edge
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Dimens.space8)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = TileChipAlpha),
                    BedrudShapeTokens.chip,
                )
                .padding(horizontal = Dimens.space8, vertical = Dimens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
        ) {
            if (isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.meeting_action_pin),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.meetingBadgeIcon),
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isMicOff) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = stringResource(R.string.meeting_contentDescription_micOff),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.meetingBadgeIcon),
                )
            }
            if (isCameraOff) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = stringResource(R.string.meeting_contentDescription_cameraOff),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.meetingBadgeIcon),
                )
            }
        }

        // Fullscreen affordance, top-end corner
        if (onExpand != null) {
            Surface(
                onClick = { onExpand(identity) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = TileChipAlpha),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.space6)
                    .size(Dimens.meetingTileAction),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = stringResource(R.string.meeting_contentDescription_tileFullscreen),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            }
        }
    }
}

/** Scrim opacity behind tile chrome (name chip, corner actions) so it reads over video. */
private const val TileChipAlpha = 0.7f

@Composable
private fun OverflowTile(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = BedrudShapeTokens.videoTile,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.meeting_tile_overflow, count),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
