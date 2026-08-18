package com.bedrud.app.ui.screens.meeting

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.bedrud.app.R
import com.bedrud.app.core.api.parseApiErrorMessage
import com.bedrud.app.core.call.CallService
import com.bedrud.app.core.deeplink.BedrudURLParser
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.util.hostOf
import com.bedrud.app.ui.util.setPlainText
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.core.livekit.ParticipantMetadata
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.core.meeting.VideoAspect
import com.bedrud.app.core.pip.PipStateHolder
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.bedrud.app.models.JoinRoomRequest
import com.bedrud.app.models.JoinRoomResponse
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    // Every server this person has added, by host. A room link on one of them opens in the app
    // rather than the website; anything else is somebody else's page and goes to a browser.
    val instances by instanceManager.store.instances.collectAsState()
    val knownHosts = remember(instances) { instances.mapNotNull { hostOf(it.serverURL) }.toSet() }
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

    // Connecting otherwise ends in silence — the screen simply becomes the call, and the one thing
    // people want confirmed is never said. It is announced once per connection, and again after a
    // reconnect, which is when it is needed most.
    //
    // Held here rather than in the bar itself: the bar is removed from composition while the chat
    // panel or a fullscreen tile is open, so anything remembered inside it would start again from
    // nothing each time one closed, and announce a connection made long ago.
    var showConnectedNotice by remember { mutableStateOf(false) }
    LaunchedEffect(connectionState) {
        showConnectedNotice = connectionState == ConnectionState.CONNECTED
        if (showConnectedNotice) {
            delay(Motion.meetingConnectedNoticeMs)
            showConnectedNotice = false
        }
    }

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
    val deafenedIdentities by roomManager.deafenedIdentities.collectAsState()
    val error by roomManager.error.collectAsState()
    val wasKicked by roomManager.wasKicked.collectAsState()

    val participantVersion by roomManager.participantVersion.collectAsState()
    val participantNames by roomManager.participantNames.collectAsState()
    // Who the room says it hears, and why it might not be hearing this device.
    val speakingLevels by roomManager.speakingLevels.collectAsState()
    val voiceAlert by roomManager.voiceAlert.collectAsState()
    val watchedStreamIdentity by roomManager.watchedStreamIdentity.collectAsState()
    val chatMessages by roomManager.chatMessages.collectAsState()
    val isChatBlocked by roomManager.isChatBlocked.collectAsState()
    var showChat by remember { mutableStateOf(false) }
    // Whether the sheet is *on its way* to being on screen, which is what the controls bar's chat
    // button follows. `showChat` only says whether it is still composed.
    var chatSheetVisible by remember { mutableStateOf(false) }
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

    // How much of the conversation has been seen. Kept current for as long as the panel is open,
    // not only at the moment it opens — otherwise everything said while it was open, including
    // this device's own messages, turns unread again the instant the panel closes. It also follows
    // the list back down when a room is left and the messages are cleared, so a stale high-water
    // mark cannot swallow the next room's first arrivals.
    var lastReadCount by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(showChat, chatMessages.size) {
        if (showChat || chatMessages.size < lastReadCount) lastReadCount = chatMessages.size
    }
    // Only what someone else said is news: a message you sent yourself never wants your attention.
    val unreadCount = if (showChat) {
        0
    } else {
        chatMessages.drop(lastReadCount).count { !it.isLocal }
    }

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
                        Spacer(modifier = Modifier.height(Dimens.space16))
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
                            // The call stays composed while chat is open — the sheet sits over it.
                            if (!isTileFullscreen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                MeetingTopBar(
                                    roomName = roomName,
                                    connectionState = connectionState,
                                    showConnectedNotice = showConnectedNotice,
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
                                // Grid tiles: the local participant first, then the remote ones.
                                // The self-tile is unconditional even with the camera off — it is
                                // where you watch your own ring light up, which is the one place
                                // the app can tell you the room is receiving your voice.
                                val gridTiles = remember(participantVersion, pinnedIdentity) {
                                    buildList {
                                        add(room.localParticipant)
                                        addAll(room.remoteParticipants.values)
                                    }.sortedByDescending { it.identity?.value == pinnedIdentity }
                                }
                                // Nobody else in the grid: a stream takes the stage and shares the
                                // full height instead of staying strip-sized, and the self-tile
                                // stands down for it — there is no one to hear you anyway.
                                val aloneInRoom = gridTiles.size <= 1
                                val expandStreams =
                                    aloneInRoom && streamParticipants.isNotEmpty()
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
                                                .padding(horizontal = Dimens.meetingScreenMargin)
                                                .padding(bottom = Dimens.meetingGridBottomSpace)
                                        } else {
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Dimens.meetingScreenMargin)
                                                .padding(bottom = Dimens.space8)
                                                .aspectRatio(VideoAspect.RATIO)
                                        },
                                    )
                                }

                                if (!expandStreams) {
                                    MeetingVideoGrid(
                                        tiles = gridTiles,
                                        room = room,
                                        localIdentity = localIdentity,
                                        disabledVideoIdentities = locallyHiddenVideoIdentities,
                                        hideAllIncomingVideo = hideAllIncomingVideo,
                                        mutedIdentities = locallyMutedIdentities,
                                        pinnedIdentity = pinnedIdentity,
                                        speakingLevels = speakingLevels,
                                        isLocalMicEnabled = isMicEnabled,
                                        isLocalDeafened = isDeafened,
                                        deafenedIdentities = deafenedIdentities,
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
                                            .padding(horizontal = Dimens.meetingScreenMargin)
                                            .padding(bottom = Dimens.meetingGridBottomSpace),
                                    )
                                }
                            }
                            }

                            // Nothing can open this while the indicator is off, but the guard is
                            // stated here too so the banner cannot be reached by a new route.
                            if (RecordingIndicatorEnabled &&
                                showRecordingBanner && !isTileFullscreen && !showChat
                            ) {
                                MeetingRecordingBanner(
                                    elapsedLabel = RecordingElapsedPlaceholder,
                                    onAcknowledge = { showRecordingBanner = false },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = Dimens.space56)
                                        .padding(horizontal = Dimens.meetingScreenMargin),
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
                                    speakingLevel =
                                        speakingLevels[fullscreenParticipant.identity?.value] ?: 0f,
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
                                    showChat = chatSheetVisible,
                                    unreadCount = unreadCount,
                                    inputMode = inputMode,
                                    connectionState = connectionState,
                                    voiceAlert = voiceAlert,
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
                                    connectionState = connectionState,
                                    voiceAlert = voiceAlert,
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
                                        isDeafened = sheetIdentity in deafenedIdentities,
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
                                val inviteParticipants = remember(participantVersion, speakingLevels) {
                                    participants.map { participant ->
                                        val identity = participant.identity?.value
                                            ?: RoomManager.UNKNOWN_PARTICIPANT_NAME
                                        InviteSheetParticipant(
                                            identity = identity,
                                            name = participant.name?.ifBlank { identity } ?: identity,
                                            avatarUrl = ParticipantMetadata.avatarUrl(participant.metadata),
                                            isLocal = identity == localIdentity,
                                            speakingLevel = speakingLevels[identity] ?: 0f,
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
                                    micLevelProvider = { roomManager.currentMicLevel() },
                                    voiceGateOpenProvider = { roomManager.isVoiceGateOpen() },
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

                            // A sheet over the call, not a page in front of it: the grid keeps
                            // rendering behind the scrim while chat is open.
                            if (showChat) {
                                MeetingChatSheet(
                                    messages = chatMessages,
                                    input = chatInput,
                                    currentIdentity = localIdentity.orEmpty(),
                                    onInputChange = { chatInput = it },
                                    onSend = {
                                        if (chatInput.isNotBlank()) {
                                            scope.launch {
                                                roomManager.sendChatMessage(chatInput.trim())
                                                chatInput = ""
                                            }
                                        }
                                    },
                                    onSendAttachment = { text, attachment ->
                                        scope.launch {
                                            roomManager.sendChatMessage(text, listOf(attachment))
                                        }
                                    },
                                    onSendPoll = { poll ->
                                        scope.launch { roomManager.sendChatMessage("", poll = poll) }
                                    },
                                    onToggleReaction = { messageId, emoji ->
                                        scope.launch { roomManager.reactToMessage(messageId, emoji) }
                                    },
                                    onVote = { messageId, optionId ->
                                        scope.launch { roomManager.voteInPoll(messageId, optionId) }
                                    },
                                    // Whoever is still here is named from the room itself; anyone who
                                    // has left is named from what the room remembered while they were
                                    // in it. Only an identity this device never saw at all falls back
                                    // to itself — a vote from before it joined, say.
                                    resolveName = { identity ->
                                        participants
                                            .firstOrNull { it.identity?.value == identity }
                                            ?.name
                                            ?.takeIf { it.isNotBlank() }
                                            ?: participantNames[identity]
                                            ?: identity
                                    },
                                    onClose = { showChat = false },
                                    onVisibleChange = { chatSheetVisible = it },
                                    imageContext = roomInfo?.id?.let { roomId ->
                                        ChatImageContext(
                                            roomId = roomId,
                                            roomApi = roomApi,
                                            serverURL = serverURL,
                                            accessToken = accessToken,
                                        )
                                    },
                                    // The room's own setting comes first: it explains the closed
                                    // dock for everyone present, where a block explains it for one
                                    // person. Either way the conversation stays readable.
                                    sendDisabledReason = when {
                                        roomInfo?.settings?.allowChat == false ->
                                            R.string.meeting_chat_disabledByRoom
                                        isChatBlocked -> R.string.meeting_chat_blockedByModerator
                                        else -> null
                                    },
                                    knownHosts = knownHosts,
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
                        Spacer(modifier = Modifier.height(Dimens.space8))
                        // Connection failures span many causes (TLS, network, server, auth), so
                        // rather than map each to its own message we show one general line and
                        // surface the raw error verbatim below it -- scroll-capped and copyable,
                        // so the actual cause can be read and reported.
                        Text(
                            text = stringResource(R.string.meeting_state_connectionFailedMessage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Dimens.meetingStatePadding)
                        )
                        val connectionError = error
                        if (!connectionError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(Dimens.space16))
                            val errorCopiedMessage = stringResource(R.string.meeting_toast_errorCopied)
                            val copyDescription = stringResource(R.string.common_action_copy)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.space24)
                                    .clip(BedrudShapeTokens.chip)
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
                                        .heightIn(max = Dimens.meetingErrorDetailMaxHeight)
                                        .verticalScroll(rememberScrollState())
                                        .padding(start = Dimens.space12, top = Dimens.space12, bottom = Dimens.space12),
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
                                        modifier = Modifier.size(Dimens.iconSm),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimens.space24))
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
                modifier = Modifier.size(Dimens.meetingStateIcon),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(Dimens.space16))
            Text(
                text = stringResource(R.string.meeting_state_kickedTitle),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Dimens.space8))
            Text(
                text = stringResource(R.string.meeting_state_kickedMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimens.meetingStatePadding)
            )
            Spacer(modifier = Modifier.height(Dimens.space24))
            androidx.compose.material3.FilledTonalButton(onClick = onBack) {
                Text(stringResource(R.string.meeting_button_backToDashboard))
            }
        }
    }
}
