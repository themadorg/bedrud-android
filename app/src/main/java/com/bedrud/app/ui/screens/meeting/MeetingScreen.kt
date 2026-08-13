package com.bedrud.app.ui.screens.meeting

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.core.api.parseApiErrorMessage
import com.bedrud.app.core.call.CallService
import com.bedrud.app.core.chat.ChatImageUtils
import com.bedrud.app.core.chat.ChatUpload
import com.bedrud.app.core.deeplink.BedrudURLParser
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.components.ChatImageLightbox
import com.bedrud.app.ui.components.ConfirmDialog
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.util.setPlainText
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.core.meeting.VideoAspect
import com.bedrud.app.core.meeting.chat.ChatWire
import com.bedrud.app.core.pip.PipStateHolder
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.bedrud.app.models.JoinRoomRequest
import com.bedrud.app.models.JoinRoomResponse
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.compose.koinInject

@Composable
fun MeetingScreen(
    roomName: String,
    onLeave: () -> Unit,
    instanceManager: InstanceManager = koinInject(),
    pipStateHolder: PipStateHolder = koinInject(),
    settingsStore: SettingsStore = koinInject(),
) {
    val roomApi = instanceManager.roomApi.collectAsState().value ?: return
    val roomManager = instanceManager.roomManager.collectAsState().value ?: return
    val authManager = instanceManager.authManager.collectAsState().value
    val currentUser by (authManager?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val serverURL = instanceManager.store.activeInstance?.serverURL.orEmpty()
    val accessToken = authManager?.getAccessToken()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val screenShareFailedMessage = stringResource(R.string.meeting_error_screenShareFailed)
    val permissionsRequiredMessage = stringResource(R.string.meeting_error_permissionsRequired)
    val roomNoLongerExistsMessage = stringResource(R.string.meeting_error_roomNoLongerExists)
    val joinFailedMessage = stringResource(R.string.meeting_error_joinFailed)
    val linkCopiedMessage = stringResource(R.string.meeting_toast_linkCopied)
    val clipLabel = stringResource(R.string.app_name)
    val isInPipMode by pipStateHolder.isInPipMode.collectAsState()

    val connectionState by roomManager.connectionState.collectAsState()

    // Arm auto-PiP only while the call is actually connected. MainActivity.onUserLeaveHint reads
    // this flag, and the camera/mic permission dialog fires onUserLeaveHint on some devices
    // (One UI, notably) — flagging from composition start sent the join flow into PiP while the
    // user was still in the app.
    DisposableEffect(connectionState) {
        pipStateHolder.setInMeeting(connectionState == ConnectionState.CONNECTED)
        onDispose {
            pipStateHolder.setInMeeting(false)
        }
    }
    val isMicEnabled by roomManager.isMicEnabled.collectAsState()
    val micMediaError by roomManager.micMediaError.collectAsState()
    val isCameraEnabled by roomManager.isCameraEnabled.collectAsState()
    val cameraMediaError by roomManager.cameraMediaError.collectAsState()
    val isScreenShareEnabled by roomManager.isScreenShareEnabled.collectAsState()
    val isDeafened by roomManager.isDeafened.collectAsState()
    val error by roomManager.error.collectAsState()
    val wasKicked by roomManager.wasKicked.collectAsState()

    val participantVersion by roomManager.participantVersion.collectAsState()
    val watchedStreamIdentity by roomManager.watchedStreamIdentity.collectAsState()
    val chatMessages by roomManager.chatMessages.collectAsState()
    var showChat by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }

    // Long-pressing a tile opens the participant sheet for that identity
    var participantSheetIdentity by remember { mutableStateOf<String?>(null) }
    // Long-pressing the watched stream opens its sheet
    var streamSheetIdentity by remember { mutableStateOf<String?>(null) }
    // Viewer-side "hide all cameras" (data saver) from the more-options sheet
    var hideAllIncomingVideo by remember { mutableStateOf(false) }
    // Pinned participant leads the grid ordering
    var pinnedIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    // Recording banner, behind the dev-gated recording dot. TODO(#107)
    var showRecordingBanner by remember { mutableStateOf(false) }
    var showAudioSettingsSheet by remember { mutableStateOf(false) }
    var showInputModeSheet by remember { mutableStateOf(false) }
    var showNoiseSuppressionSheet by remember { mutableStateOf(false) }
    var noiseSuppressionMode by remember { mutableStateOf(settingsStore.getNoiseSuppression()) }

    // Identities whose video this viewer has locally hidden (does not affect other viewers)
    var locallyHiddenVideoIdentities by remember { mutableStateOf(setOf<String>()) }
    val onToggleVideoDisabled: (String) -> Unit = { identity ->
        locallyHiddenVideoIdentities = if (identity in locallyHiddenVideoIdentities) {
            locallyHiddenVideoIdentities - identity
        } else {
            locallyHiddenVideoIdentities + identity
        }
    }

    // Identities this viewer has locally muted (does not affect what other participants hear)
    val locallyMutedIdentities by roomManager.locallyMutedIdentities.collectAsState()
    val onToggleLocalMute: (String) -> Unit = { identity -> roomManager.toggleLocalMute(identity) }
    val participantVolumes by roomManager.participantVolumes.collectAsState()
    val inputMode by roomManager.inputMode.collectAsState()
    val autoSensitivity by roomManager.autoSensitivity.collectAsState()
    val voiceSensitivity by roomManager.voiceSensitivity.collectAsState()

    // Unread chat count while panel is closed
    var lastReadCount by rememberSaveable { mutableIntStateOf(0) }
    val unreadCount = if (showChat) 0 else (chatMessages.size - lastReadCount).coerceAtLeast(0)
    LaunchedEffect(showChat) { if (showChat) lastReadCount = chatMessages.size }

    // Leave/end dialog
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showRoomSettingsSheet by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }

    // Per-tile fullscreen: which participant fills the screen, and whether its chrome is showing
    var fullscreenParticipantIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreenChromeVisible by remember { mutableStateOf(true) }

    var roomInfo by remember { mutableStateOf<JoinRoomResponse?>(null) }
    var isJoining by remember { mutableStateOf(true) }

    fun startMeetingCall(info: JoinRoomResponse) {
        CallService.start(
            context,
            roomName,
            info.livekitHost!!,
            info.token!!,
            currentUser?.avatarUrl,
        )
        isJoining = false
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            scope.launch {
                val started = roomManager.startScreenShare(result.data!!)
                if (!started) {
                    val message = roomManager.error.value ?: screenShareFailedMessage
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    var pendingMediaAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Request permissions, then start the system-call foreground service
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val pending = pendingMediaAction
        pendingMediaAction = null

        if (pending != null) {
            if (permissions.values.all { it }) {
                pending()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(permissionsRequiredMessage)
                }
            }
            return@rememberLauncherForActivityResult
        }

        val mediaGranted = permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.RECORD_AUDIO] == true
        if (mediaGranted && roomInfo != null) {
            startMeetingCall(roomInfo!!)
        } else if (!mediaGranted) {
            scope.launch {
                snackbarHostState.showSnackbar(permissionsRequiredMessage)
            }
            isJoining = false
        }
    }

    // Join room via API and connect to LiveKit (or reattach to an ongoing call)
    LaunchedEffect(roomName) {
        // Even when reattaching to a call CallService already has running in the
        // background, roomInfo (adminId, roomId, ...) belongs to this fresh
        // composition and must be re-fetched — it isn't carried over from the
        // previous MeetingScreen instance that was disposed when the call was
        // minimized. Skipping this call left isAdmin stuck false (hiding the
        // kick/ban/mute menu) until a full leave-and-rejoin.
        val reattaching = CallService.isRunning && CallService.activeRoomName == roomName

        try {
            val response = roomApi.joinRoom(JoinRoomRequest(roomName = roomName))
            if (response.isSuccessful) {
                val info = response.body()
                if (info?.token != null && info.livekitHost != null) {
                    roomInfo = info
                    if (reattaching) {
                        isJoining = false
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                } else {
                    snackbarHostState.showSnackbar(roomNoLongerExistsMessage)
                    isJoining = false
                    onLeave()
                }
            } else {
                // The server names the specific problem (e.g. "Room not found") when it has
                // one; show that instead of a generic failure. Either way, leave -- staying on
                // this screen after a failed join left the user stuck on an infinite spinner
                // with no way back except the system back button.
                snackbarHostState.showSnackbar(parseApiErrorMessage(response) ?: joinFailedMessage)
                isJoining = false
                onLeave()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbarHostState.showSnackbar(e.message ?: joinFailedMessage)
            isJoining = false
            onLeave()
        }
    }

    // Handle server-side disconnect: when connection drops after being connected, leave
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            wasConnected = true
        } else if (wasConnected && connectionState == ConnectionState.DISCONNECTED) {
            onLeave()
        }
    }

    // Show error
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // "You were removed" overlay shown after kick
    if (wasKicked) {
        KickedScreen(onBack = {
            roomManager.disconnect()
            onLeave()
        })
        return
    }

    Scaffold(
        contentWindowInsets = BedrudScaffoldContentInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (connectionState) {
            ConnectionState.DISCONNECTED,
            ConnectionState.CONNECTING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (connectionState == ConnectionState.CONNECTING)
                                stringResource(R.string.meeting_status_connecting, roomName)
                            else stringResource(R.string.meeting_status_preparing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            ConnectionState.CONNECTED,
            ConnectionState.RECONNECTING -> {
                val room = roomManager.room
                if (room != null) {
                    // Video grid (recomposes when participantVersion changes)
                    val participants = remember(participantVersion) {
                        buildList {
                            room.localParticipant.let { add(it) }
                            addAll(room.remoteParticipants.values)
                        }
                    }

                    val isAdmin = roomInfo?.let { info ->
                        currentUser?.id == info.adminId
                    } ?: false
                    val roomId = roomInfo?.id ?: ""
                    val audioState = rememberMeetingAudioState(roomManager.audioHandler)

                    val localIdentity = room.localParticipant.identity?.value

                    if (isInPipMode) {
                        val pipWatchedIdentity = watchedStreamIdentity
                        val pipParticipant = if (pipWatchedIdentity != null) {
                            participants.find { it.identity?.value == pipWatchedIdentity }
                        } else {
                            participants.firstOrNull {
                                it.identity != room.localParticipant.identity
                            } ?: participants.firstOrNull()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pipParticipant != null) {
                                val screenSharePublication = pipParticipant.getTrackPublication(
                                    Track.Source.SCREEN_SHARE,
                                )
                                val screenShareTrack = screenSharePublication
                                    ?.track as? io.livekit.android.room.track.VideoTrack
                                val isScreenShareMuted = screenSharePublication?.muted == true

                                val cameraPublication = pipParticipant.getTrackPublication(Track.Source.CAMERA)
                                val cameraTrack = cameraPublication
                                    ?.track as? io.livekit.android.room.track.VideoTrack
                                val isCameraMuted = cameraPublication?.muted == true
                                val isPipVideoLocallyDisabled =
                                    pipParticipant.identity?.value in locallyHiddenVideoIdentities

                                val pipTrack = when {
                                    pipWatchedIdentity != null && screenShareTrack != null && !isScreenShareMuted ->
                                        screenShareTrack
                                    cameraTrack != null && !isCameraMuted && !isPipVideoLocallyDisabled -> cameraTrack
                                    else -> null
                                }

                                if (pipTrack != null) {
                                    VideoTrackView(
                                        videoTrack = pipTrack,
                                        modifier = Modifier.fillMaxSize(),
                                        passedRoom = room,
                                    )
                                } else {
                                    Text(
                                        text = (pipParticipant.name ?: "").take(1).uppercase(),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        // Normal mode
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(padding)
                        ) {
                            val isTileFullscreen = fullscreenParticipantIdentity != null
                            if (!showChat && !isTileFullscreen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                MeetingTopBar(
                                    roomName = roomName,
                                    connectionState = connectionState,
                                    isCameraEnabled = isCameraEnabled,
                                    onInvite = { showInviteSheet = true },
                                    onSwitchCamera = { roomManager.switchCamera() },
                                    onOpenAudioOutput = { showAudioSheet = true },
                                    onRecordingClick = { showRecordingBanner = true },
                                )


                                // Every live screenshare gets its own strip tile; watching is
                                // opt-in per viewer, one stream at a time.
                                val streamParticipants = remember(participantVersion) {
                                    participants.filter {
                                        it.getTrackPublication(Track.Source.SCREEN_SHARE) != null
                                    }
                                }
                                // Grid tiles: the local participant only while their camera is
                                // on (there is no self-tile for an audio-only self), then the
                                // remote participants.
                                val gridTiles = remember(participantVersion, isCameraEnabled, pinnedIdentity) {
                                    buildList {
                                        if (isCameraEnabled) add(room.localParticipant)
                                        addAll(room.remoteParticipants.values)
                                    }.sortedByDescending { it.identity?.value == pinnedIdentity }
                                }
                                // With no grid underneath, streams take the stage and share the
                                // full height instead of staying strip-sized.
                                val expandStreams =
                                    gridTiles.isEmpty() && streamParticipants.isNotEmpty()
                                streamParticipants.forEach { presenter ->
                                    val presenterIdentity = presenter.identity?.value
                                    MeetingStreamTile(
                                        participant = presenter,
                                        isLocal = presenterIdentity == localIdentity,
                                        isWatched = presenterIdentity == watchedStreamIdentity,
                                        room = room,
                                        participantVersion = participantVersion,
                                        onWatch = { roomManager.watchStream(presenterIdentity) },
                                        onExpand = {
                                            if (presenterIdentity != null) {
                                                fullscreenParticipantIdentity = presenterIdentity
                                                fullscreenChromeVisible = true
                                            }
                                        },
                                        onOpenStreamSheet = {
                                            streamSheetIdentity = presenterIdentity
                                        },
                                        onStopShare = {
                                            scope.launch { roomManager.stopScreenShare() }
                                        },
                                        modifier = if (expandStreams) {
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(horizontal = Dimens.space8)
                                                .padding(bottom = Dimens.meetingGridBottomSpace)
                                        } else {
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Dimens.space8)
                                                .padding(bottom = Dimens.space8)
                                                .aspectRatio(VideoAspect.RATIO)
                                        },
                                    )
                                }

                                if (gridTiles.isEmpty() && streamParticipants.isEmpty()) {
                                    // Alone with the camera off: there is no self-tile, so give
                                    // the stage a hint instead of a blank void.
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = Dimens.screenPadding)
                                            .padding(bottom = Dimens.meetingGridBottomSpace),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.meeting_empty_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(Dimens.space8))
                                        Text(
                                            text = stringResource(R.string.meeting_empty_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                } else if (gridTiles.isNotEmpty()) {
                                    MeetingVideoGrid(
                                        tiles = gridTiles,
                                        room = room,
                                        localIdentity = localIdentity,
                                        disabledVideoIdentities = locallyHiddenVideoIdentities,
                                        hideAllIncomingVideo = hideAllIncomingVideo,
                                        mutedIdentities = locallyMutedIdentities,
                                        pinnedIdentity = pinnedIdentity,
                                        onOpenParticipantActions = { identity ->
                                            participantSheetIdentity = identity
                                        },
                                        onExpandTile = { identity ->
                                            fullscreenParticipantIdentity = identity
                                            fullscreenChromeVisible = true
                                        },
                                        onOverflowClick = { showInviteSheet = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = Dimens.space8)
                                            .padding(bottom = Dimens.meetingGridBottomSpace),
                                    )
                                }
                            }
                            }

                            if (showRecordingBanner && !isTileFullscreen && !showChat) {
                                MeetingRecordingBanner(
                                    elapsedLabel = RecordingElapsedPlaceholder,
                                    onAcknowledge = { showRecordingBanner = false },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = Dimens.space56)
                                        .padding(horizontal = Dimens.space8),
                                )
                            }

                            val fullscreenParticipant = fullscreenParticipantIdentity?.let { id ->
                                participants.find { it.identity?.value == id }
                            }
                            // Leave fullscreen automatically when that participant leaves the room.
                            LaunchedEffect(fullscreenParticipantIdentity, participantVersion) {
                                if (fullscreenParticipantIdentity != null && fullscreenParticipant == null) {
                                    fullscreenParticipantIdentity = null
                                }
                            }
                            // The hardware back key leaves fullscreen first, like the collapse
                            // button, instead of leaving the meeting screen.
                            BackHandler(enabled = fullscreenParticipant != null) {
                                fullscreenParticipantIdentity = null
                            }
                            if (fullscreenParticipant != null) {
                                MeetingParticipantFullscreen(
                                    participant = fullscreenParticipant,
                                    room = room,
                                    participantVersion = participantVersion,
                                    chromeVisible = fullscreenChromeVisible,
                                    isVideoLocallyDisabled =
                                        fullscreenParticipant.identity?.value in locallyHiddenVideoIdentities,
                                    onToggleChrome = { fullscreenChromeVisible = !fullscreenChromeVisible },
                                    onAutoHideChrome = { fullscreenChromeVisible = false },
                                    onCollapse = { fullscreenParticipantIdentity = null },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            val roomLink = remember(serverURL, roomName) {
                                BedrudURLParser.buildMeetingLink(serverURL, roomName)
                            }
                            val copyRoomLink = {
                                scope.launch {
                                    clipboard.setPlainText(clipLabel, roomLink)
                                    snackbarHostState.showSnackbar(linkCopiedMessage)
                                }
                                Unit
                            }
                            val toggleMicAction = {
                                val action: () -> Unit = {
                                    scope.launch { roomManager.toggleMicrophone() }
                                    Unit
                                }
                                if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                    action()
                                } else {
                                    pendingMediaAction = action
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                            }
                            val toggleCameraAction = {
                                val action: () -> Unit = {
                                    scope.launch { roomManager.toggleCamera() }
                                    Unit
                                }
                                if (hasPermission(Manifest.permission.CAMERA)) {
                                    action()
                                } else {
                                    pendingMediaAction = action
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                }
                            }
                            val toggleScreenShareAction = {
                                if (isScreenShareEnabled) {
                                    scope.launch { roomManager.stopScreenShare() }
                                    Unit
                                } else {
                                    val projectionManager = context.getSystemService(
                                        Context.MEDIA_PROJECTION_SERVICE
                                    ) as MediaProjectionManager
                                    screenCaptureLauncher.launch(
                                        projectionManager.createScreenCaptureIntent()
                                    )
                                }
                            }
                            val toggleChatAction = {
                                showChat = !showChat
                            }
                            val endCallAction = {
                                if (isAdmin) {
                                    showLeaveDialog = true
                                } else {
                                    CallService.stop(context)
                                    onLeave()
                                }
                            }

                            if (!isTileFullscreen || fullscreenChromeVisible) {
                                MeetingControlsBar(
                                    isMicEnabled = isMicEnabled,
                                    isCameraEnabled = isCameraEnabled,
                                    micHasError = micMediaError,
                                    cameraHasError = cameraMediaError,
                                    isScreenShareEnabled = isScreenShareEnabled,
                                    showChat = showChat,
                                    unreadCount = unreadCount,
                                    inputMode = inputMode,
                                    micLevelProvider = { roomManager.currentMicLevel() },
                                    voiceGateOpenProvider = { roomManager.isVoiceGateOpen() },
                                    onPushToTalkChange = { active ->
                                        scope.launch {
                                            roomManager.setPushToTalkTransmitting(active)
                                        }
                                    },
                                    onToggleMic = toggleMicAction,
                                    onToggleCamera = toggleCameraAction,
                                    onToggleScreenShare = toggleScreenShareAction,
                                    onToggleChat = toggleChatAction,
                                    onOpenMoreOptions = { showMoreOptionsSheet = true },
                                    onEndCall = endCallAction,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = Dimens.space12),
                                )
                            }

                            if (showMoreOptionsSheet) {
                                MeetingMoreOptionsSheet(
                                    isMicEnabled = isMicEnabled,
                                    isCameraEnabled = isCameraEnabled,
                                    micHasError = micMediaError,
                                    cameraHasError = cameraMediaError,
                                    isScreenShareEnabled = isScreenShareEnabled,
                                    showChat = showChat,
                                    unreadCount = unreadCount,
                                    isDeafened = isDeafened,
                                    hideAllIncomingVideo = hideAllIncomingVideo,
                                    isRoomSettingsAvailable = isAdmin,
                                    inputMode = inputMode,
                                    micLevelProvider = { roomManager.currentMicLevel() },
                                    voiceGateOpenProvider = { roomManager.isVoiceGateOpen() },
                                    onPushToTalkChange = { active ->
                                        scope.launch {
                                            roomManager.setPushToTalkTransmitting(active)
                                        }
                                    },
                                    onToggleMic = toggleMicAction,
                                    onToggleCamera = toggleCameraAction,
                                    onToggleScreenShare = toggleScreenShareAction,
                                    onToggleChat = toggleChatAction,
                                    onEndCall = endCallAction,
                                    onToggleDeafen = { scope.launch { roomManager.toggleDeafen() } },
                                    onToggleHideAllIncomingVideo = {
                                        hideAllIncomingVideo = !hideAllIncomingVideo
                                    },
                                    onOpenAudioSettings = { showAudioSettingsSheet = true },
                                    onOpenNoiseSuppression = { showNoiseSuppressionSheet = true },
                                    onOpenRoomSettings = { showRoomSettingsSheet = true },
                                    onDismiss = { showMoreOptionsSheet = false },
                                )
                            }

                            participantSheetIdentity?.let { sheetIdentity ->
                                val sheetParticipant = participants.find {
                                    it.identity?.value == sheetIdentity
                                }
                                if (sheetParticipant == null) {
                                    participantSheetIdentity = null
                                } else {
                                    MeetingParticipantSheet(
                                        name = sheetParticipant.name?.ifBlank { sheetIdentity }
                                            ?: sheetIdentity,
                                        identity = sheetIdentity,
                                        isAdmin = isAdmin,
                                        roomId = roomId,
                                        roomApi = roomApi,
                                        snackbarHostState = snackbarHostState,
                                        scope = scope,
                                        volume = participantVolumes[sheetIdentity] ?: 1f,
                                        onVolumeChange = {
                                            roomManager.setParticipantVolume(sheetIdentity, it)
                                        },
                                        isLocallyMuted = sheetIdentity in locallyMutedIdentities,
                                        onToggleLocalMute = { onToggleLocalMute(sheetIdentity) },
                                        isVideoLocallyDisabled =
                                            sheetIdentity in locallyHiddenVideoIdentities,
                                        onToggleVideoDisabled = {
                                            onToggleVideoDisabled(sheetIdentity)
                                        },
                                        isPinned = pinnedIdentity == sheetIdentity,
                                        onTogglePin = {
                                            pinnedIdentity =
                                                if (pinnedIdentity == sheetIdentity) null
                                                else sheetIdentity
                                        },
                                        onFullscreen = {
                                            fullscreenParticipantIdentity = sheetIdentity
                                            fullscreenChromeVisible = true
                                        },
                                        onDismiss = { participantSheetIdentity = null },
                                    )
                                }
                            }

                            streamSheetIdentity?.let { sheetIdentity ->
                                val presenter = participants.find {
                                    it.identity?.value == sheetIdentity
                                }
                                if (presenter == null) {
                                    streamSheetIdentity = null
                                } else {
                                    MeetingStreamSheet(
                                        presenterName = presenter.name?.ifBlank { sheetIdentity }
                                            ?: sheetIdentity,
                                        onLeaveStream = { roomManager.watchStream(null) },
                                        onDismiss = { streamSheetIdentity = null },
                                    )
                                }
                            }

                            if (showInviteSheet) {
                                val inviteParticipants = remember(participantVersion) {
                                    participants.map { participant ->
                                        val identity = participant.identity?.value
                                            ?: RoomManager.UNKNOWN_PARTICIPANT_NAME
                                        InviteSheetParticipant(
                                            identity = identity,
                                            name = participant.name?.ifBlank { identity } ?: identity,
                                            avatarUrl = participant.metadata?.let { meta ->
                                                try {
                                                    val obj = JSONObject(meta)
                                                    if (obj.has("avatarUrl")) {
                                                        obj.getString("avatarUrl")
                                                    } else {
                                                        null
                                                    }
                                                } catch (_: Exception) {
                                                    null
                                                }
                                            },
                                            isLocal = identity == localIdentity,
                                        )
                                    }
                                }
                                MeetingInviteSheet(
                                    participants = inviteParticipants,
                                    roomLink = roomLink,
                                    snackbarHostState = snackbarHostState,
                                    scope = scope,
                                    onCopyLink = copyRoomLink,
                                    onDismiss = { showInviteSheet = false },
                                )
                            }

                            if (showAudioSheet) {
                                MeetingAudioSourceSheet(
                                    audioHandler = roomManager.audioHandler,
                                    audioState = audioState,
                                    onDismiss = { showAudioSheet = false },
                                )
                            }

                            if (showAudioSettingsSheet) {
                                MeetingAudioSettingsSheet(
                                    audioHandler = roomManager.audioHandler,
                                    audioState = audioState,
                                    inputMode = inputMode,
                                    autoSensitivity = autoSensitivity,
                                    sensitivity = voiceSensitivity,
                                    onOpenInputModePicker = { showInputModeSheet = true },
                                    onAutoSensitivityChange = { roomManager.setAutoSensitivity(it) },
                                    onSensitivityChange = { roomManager.setVoiceSensitivity(it) },
                                    onDismiss = { showAudioSettingsSheet = false },
                                )
                            }

                            if (showInputModeSheet) {
                                MeetingInputModeSheet(
                                    inputMode = inputMode,
                                    onSelect = { mode ->
                                        scope.launch { roomManager.setInputMode(mode) }
                                    },
                                    onDismiss = { showInputModeSheet = false },
                                )
                            }

                            if (showNoiseSuppressionSheet) {
                                MeetingNoiseSuppressionSheet(
                                    mode = noiseSuppressionMode,
                                    onSelect = { mode ->
                                        settingsStore.setNoiseSuppression(mode)
                                        noiseSuppressionMode = mode
                                    },
                                    onDismiss = { showNoiseSuppressionSheet = false },
                                )
                            }

                            if (showRoomSettingsSheet) {
                                roomInfo?.let { info ->
                                    MeetingRoomSettingsSheet(
                                        roomId = info.id,
                                        roomApi = roomApi,
                                        isPublic = info.isPublic,
                                        settings = info.settings,
                                        snackbarHostState = snackbarHostState,
                                        onDismiss = { showRoomSettingsSheet = false },
                                        onSaved = { newIsPublic, newSettings ->
                                            roomInfo = info.copy(isPublic = newIsPublic, settings = newSettings)
                                        },
                                    )
                                }
                            }

                            if (showLeaveDialog) {
                                MeetingLeaveSheet(
                                    onDismiss = { showLeaveDialog = false },
                                    onJustLeave = {
                                        showLeaveDialog = false
                                        CallService.stop(context)
                                        onLeave()
                                    },
                                    onEndForEveryone = {
                                        showLeaveDialog = false
                                        scope.launch {
                                            try {
                                                roomApi.deleteRoom(roomId)
                                            } catch (_: Exception) {}
                                            CallService.stop(context)
                                            onLeave()
                                        }
                                    },
                                )
                            }

                            AnimatedVisibility(
                                visible = showChat,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                ChatPanel(
                                    modifier = Modifier.fillMaxSize(),
                                    messages = chatMessages,
                                    chatInput = chatInput,
                                    onChatInputChange = { chatInput = it },
                                    onSend = {
                                        if (chatInput.isNotBlank()) {
                                            scope.launch {
                                                roomManager.sendChatMessage(chatInput.trim())
                                                chatInput = ""
                                            }
                                        }
                                    },
                                    onClose = { showChat = false },
                                    roomId = roomInfo?.id,
                                    roomApi = roomApi,
                                    serverURL = serverURL,
                                    accessToken = accessToken,
                                    onSendWithAttachment = { text, attachment ->
                                        scope.launch {
                                            roomManager.sendChatMessage(text, listOf(attachment))
                                        }
                                    },
                                )
                            }

                        }
                    }
                }
            }

            ConnectionState.FAILED -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.meeting_state_connectionFailed),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Connection failures span many causes (TLS, network, server, auth), so
                        // rather than map each to its own message we show one general line and
                        // surface the raw error verbatim below it -- scroll-capped and copyable,
                        // so the actual cause can be read and reported.
                        Text(
                            text = stringResource(R.string.meeting_state_connectionFailedMessage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        val connectionError = error
                        if (!connectionError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val errorCopiedMessage = stringResource(R.string.meeting_toast_errorCopied)
                            val copyDescription = stringResource(R.string.common_action_copy)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = connectionError,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                                )
                                // Copy action lives on the box itself, pinned to its top-right.
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            clipboard.setPlainText(clipLabel, connectionError)
                                            snackbarHostState.showSnackbar(errorCopiedMessage)
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = copyDescription,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        androidx.compose.material3.FilledTonalButton(
                            // The service normally self-stops the instant the connection fails
                            // (see CallService); this is a guarded safety net for any case where
                            // it is still up, so we never navigate back leaving its notification.
                            onClick = {
                                if (CallService.isRunning) CallService.stop(context)
                                onLeave()
                            }
                        ) {
                            Text(stringResource(R.string.meeting_button_goBack))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    onClose: () -> Unit,
    closeContentDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = closeContentDescription,
            )
        }
    }
    androidx.compose.material3.HorizontalDivider()
}

// ── Participants Panel ─────────────────────────────────────────────────────────

// ── Kicked screen ─────────────────────────────────────────────────────────────

@Composable
private fun KickedScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Badge,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.meeting_state_kickedTitle),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.meeting_state_kickedMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onBack) {
                Text(stringResource(R.string.meeting_button_backToDashboard))
            }
        }
    }
}

@Composable
private fun ChatPanel(
    modifier: Modifier = Modifier.fillMaxSize(),
    messages: List<ChatMessage>,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    roomId: String? = null,
    roomApi: RoomApi? = null,
    serverURL: String = "",
    accessToken: String? = null,
    onSendWithAttachment: ((String, com.bedrud.app.core.livekit.ChatAttachment) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val flatFabElevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp
    )

    // Detect whether user has scrolled away from the bottom
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= messages.size - 2
        }
    }

    // Auto-scroll to bottom only when following
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || roomId == null || roomApi == null) return@rememberLauncherForActivityResult
        scope.launch {
            isUploading = true
            uploadError = null
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open image")
                val bytes = stream.readBytes()
                stream.close()
                val mimeType = context.contentResolver.getType(uri) ?: ChatUpload.DEFAULT_MIME
                val ext = ChatUpload.extensionForMime(mimeType)
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData(
                    ChatUpload.MULTIPART_FILE_FIELD,
                    ChatUpload.fileName(ext),
                    requestBody,
                )
                val response = roomApi.uploadChatImage(roomId, part)
                if (response.isSuccessful) {
                    val result = response.body()!!
                    val attachment = com.bedrud.app.core.livekit.ChatAttachment(
                        kind = ChatWire.ATTACHMENT_KIND_IMAGE,
                        url = result.url,
                        mime = result.mime,
                        w = result.width,
                        h = result.height,
                        size = result.size,
                    )
                    onSendWithAttachment?.invoke(chatInput.trim(), attachment)
                    onChatInputChange("")
                } else {
                    uploadError = "Upload failed (${response.code()})"
                }
            } catch (e: Exception) {
                uploadError = e.message ?: "Upload failed"
            } finally {
                isUploading = false
            }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
    ) {
        PanelHeader(
            title = stringResource(R.string.meeting_panel_chat),
            onClose = onClose,
            closeContentDescription = stringResource(R.string.meeting_contentDescription_closeChat),
        )

        // Messages list + scroll-to-bottom button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        serverURL = serverURL,
                        accessToken = accessToken,
                        onImageClick = { previewImageUrl = it },
                    )
                }
            }

            // Scroll-to-bottom FAB when user scrolled up
            if (!isAtBottom) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    elevation = flatFabElevation
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.meeting_contentDescription_scrollToBottom),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // Upload status
        if (isUploading) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(text = stringResource(R.string.meeting_chat_uploading), style = MaterialTheme.typography.labelSmall)
            }
        }
        uploadError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // Input dock — imePadding lifts above keyboard; extra bottom inset clears controls bar when closed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .then(
                    if (!isKeyboardVisible) {
                        Modifier.padding(bottom = 72.dp)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Image picker button
            if (roomApi != null && roomId != null) {
                IconButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isUploading,
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = stringResource(R.string.meeting_contentDescription_attachImage),
                        tint = if (!isUploading) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            OutlinedTextField(
                value = chatInput,
                onValueChange = onChatInputChange,
                placeholder = { Text(stringResource(R.string.meeting_chat_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            scope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = BidiUtils.textDirection(chatInput),
                ),
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = chatInput.isNotBlank() && !isUploading
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.meeting_contentDescription_send),
                    tint = if (chatInput.isNotBlank() && !isUploading)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    ChatImageLightbox(
        url = previewImageUrl,
        serverURL = serverURL,
        accessToken = accessToken,
        onClose = { previewImageUrl = null },
    )
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    serverURL: String,
    accessToken: String?,
    onImageClick: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isLocal) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.senderName,
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Column(
            horizontalAlignment = if (message.isLocal) Alignment.End else Alignment.Start
        ) {
            // Image attachments
            message.attachments.filter { it.kind == ChatWire.ATTACHMENT_KIND_IMAGE }.forEach { att ->
                val isDataUri = att.url.startsWith("data:")
                if (isDataUri) {
                    // Decode base64 data URI to bitmap in-memory
                    val bitmap = remember(att.url) {
                        runCatching {
                            val b64 = att.url.substringAfter(",")
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?.asImageBitmap()
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.meeting_chat_sharedImage),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onImageClick(att.url) },
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                } else {
                    AsyncImage(
                        model = ChatImageUtils.imageRequest(context, serverURL, att.url, accessToken),
                        contentDescription = stringResource(R.string.meeting_contentDescription_viewImage),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onImageClick(att.url) },
                        contentScale = ContentScale.FillWidth,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Text content
            if (message.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(
                            if (message.isLocal) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = BidiUtils.wrap(message.text),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = BidiUtils.textDirection(message.text),
                        ),
                        color = if (message.isLocal) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
