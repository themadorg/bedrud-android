package com.bedrud.app.core.livekit

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.core.audio.NoiseSuppressionMode
import com.bedrud.app.core.audio.VoiceGateProcessor
import com.bedrud.app.core.audio.VoiceReachMonitor
import com.bedrud.app.core.call.CallConnectionService
import com.bedrud.app.core.meeting.chat.ChatWire
import com.bedrud.app.core.registerNotificationChannel
import com.bedrud.app.ui.screens.settings.SettingsStore
import io.livekit.android.AudioOptions
import io.livekit.android.audio.AudioProcessorOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.RoomException
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

data class ChatAttachment(
    val kind: String,   // e.g. ChatWire.ATTACHMENT_KIND_IMAGE
    val url: String,
    val mime: String,
    val w: Int = 0,
    val h: Int = 0,
    val size: Int = 0,
)

data class ChatMessage(
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLocal: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList(),
)

class RoomManager(
    private val application: Application,
    private val settingsStore: SettingsStore,
) {

    private var _room: Room? = null
    val room: Room? get() = _room

    private var _audioHandler: CallAudioSwitch? = null
    val audioHandler: CallAudioSwitch? get() = _audioHandler

    private var eventScope: CoroutineScope? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isMicEnabled = MutableStateFlow(settingsStore.getMicEnabled())
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    private val _micMediaError = MutableStateFlow(false)
    val micMediaError: StateFlow<Boolean> = _micMediaError.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _cameraMediaError = MutableStateFlow(false)
    val cameraMediaError: StateFlow<Boolean> = _cameraMediaError.asStateFlow()

    private val _isScreenShareEnabled = MutableStateFlow(false)
    val isScreenShareEnabled: StateFlow<Boolean> = _isScreenShareEnabled.asStateFlow()

    private val _isDeafened = MutableStateFlow(settingsStore.getDeafened())
    val isDeafened: StateFlow<Boolean> = _isDeafened.asStateFlow()
    private var micMutedBeforeDeafen = false

    // Identities this viewer has locally muted (does not affect what other participants hear)
    private val _locallyMutedIdentities = MutableStateFlow<Set<String>>(emptySet())
    val locallyMutedIdentities: StateFlow<Set<String>> = _locallyMutedIdentities.asStateFlow()

    // Per-participant playback volume for this viewer only (1.0 when unset). A local mute
    // overrides it to zero without losing the chosen level.
    private val _participantVolumes = MutableStateFlow<Map<String, Float>>(emptyMap())
    val participantVolumes: StateFlow<Map<String, Float>> = _participantVolumes.asStateFlow()

    // Incremented on every participant change to trigger recomposition
    private val _participantVersion = MutableStateFlow(0)
    val participantVersion: StateFlow<Int> = _participantVersion.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _roomName = MutableStateFlow<String?>(null)
    val roomName: StateFlow<String?> = _roomName.asStateFlow()

    var onDisconnected: (() -> Unit)? = null

    private val _wasKicked = MutableStateFlow(false)
    val wasKicked: StateFlow<Boolean> = _wasKicked.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Which participant's screenshare this viewer is watching — at most one at a time.
    // The published track itself announces a share; watching is a per-viewer subscription.
    private val _watchedStreamIdentity = MutableStateFlow<String?>(null)
    val watchedStreamIdentity: StateFlow<String?> = _watchedStreamIdentity.asStateFlow()

    // Input mode (voice activity / push-to-talk) with its manual voice gate. The gate object is
    // handed to LiveKit at connect and reconfigured in place afterwards.
    private val voiceGate = VoiceGateProcessor().apply {
        roomMayHear = ::roomMayHearMe
    }
    private val _inputMode = MutableStateFlow(settingsStore.getInputMode())
    val inputMode: StateFlow<MeetingInputMode> = _inputMode.asStateFlow()
    private val _autoSensitivity = MutableStateFlow(settingsStore.getAutoSensitivity())
    val autoSensitivity: StateFlow<Boolean> = _autoSensitivity.asStateFlow()
    private val _voiceSensitivity = MutableStateFlow(settingsStore.getVoiceSensitivity())
    val voiceSensitivity: StateFlow<Float> = _voiceSensitivity.asStateFlow()

    // Who the room currently hears, identity to level (0..1). This is the server's own view,
    // echoed back over the signal connection and including the local participant, so a live entry
    // for yourself is proof your audio is reaching the room rather than only reaching your meter.
    private val speakingTracker = SpeakingTracker()
    private val _speakingLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val speakingLevels: StateFlow<Map<String, Float>> = _speakingLevels.asStateFlow()
    private var speakingDecayJob: Job? = null

    // Why the room is not hearing this participant, when it plainly should be.
    private val voiceReachMonitor = VoiceReachMonitor()
    private val _voiceAlert = MutableStateFlow(MeetingVoiceAlert.None)
    val voiceAlert: StateFlow<MeetingVoiceAlert> = _voiceAlert.asStateFlow()

    /** Live mic capture level (0..1) for the in-call meter; sampled per frame by the UI. */
    fun currentMicLevel(): Float = if (_isMicEnabled.value) voiceGate.level else 0f

    /** False only while the manual voice gate is holding audio back. */
    fun isVoiceGateOpen(): Boolean = voiceGate.gateOpen

    /**
     * Whether outgoing audio is allowed to leave this device right now.
     *
     * Consulted by [VoiceGateProcessor] on every captured frame, and deliberately demanding: the
     * app's own mute state and LiveKit's publication must *both* say the microphone is open. Since
     * mute keeps the track running, agreement between the two is the only thing standing between a
     * muted button and a live microphone, so disagreement — or a missing publication, or no room
     * at all — is treated as muted.
     */
    private fun roomMayHearMe(): Boolean {
        if (!_isMicEnabled.value) return false
        val publication = _room?.localParticipant
            ?.getTrackPublication(Track.Source.MICROPHONE) ?: return false
        return !publication.muted
    }

    private fun syncVoiceGate() {
        voiceGate.sensitivity = _voiceSensitivity.value
        voiceGate.gateEnabled =
            _inputMode.value == MeetingInputMode.VOICE_ACTIVITY && !_autoSensitivity.value
    }

    private fun onActiveSpeakers(speakers: List<Participant>) {
        val reported = speakers.mapNotNull { speaker ->
            speaker.identity?.value?.let { it to speaker.audioLevel }
        }.toMap()
        _speakingLevels.value = speakingTracker.onSpeakers(reported, SystemClock.elapsedRealtime())
        startSpeakingDecay()
    }

    /**
     * Keeps ticking only while someone is held as speaking: the server announces a new speaker but
     * never announces silence, so the last one needs a timer to drop out. Idles between bursts.
     */
    private fun startSpeakingDecay() {
        if (speakingDecayJob?.isActive == true || _speakingLevels.value.isEmpty()) return
        speakingDecayJob = eventScope?.launch {
            while (isActive && _speakingLevels.value.isNotEmpty()) {
                delay(SpeakingTracker.PruneIntervalMillis)
                val pruned = speakingTracker.prune(SystemClock.elapsedRealtime())
                if (pruned != _speakingLevels.value) _speakingLevels.value = pruned
            }
        }
    }

    /**
     * Watches the local capture level against the room's view of it, so a microphone that captures
     * but never arrives is called out instead of being hidden behind a bouncing meter. Reads the
     * gate's raw level rather than [currentMicLevel], which reports zero while muted — being muted
     * is one of the cases worth reporting.
     */
    private fun startVoiceReachMonitor(localIdentity: String?) {
        eventScope?.launch {
            var lastFrameCount = voiceGate.frameCount
            while (isActive) {
                delay(VoiceReachMonitor.SampleIntervalMillis)
                // The gate only writes its level when a frame arrives, and capture stops while
                // muted — so a level whose frame counter has not moved since the last sample is a
                // stale reading, not silence at that volume. Reporting it as-is left the warning
                // lit forever after a mute mid-sentence.
                val frames = voiceGate.frameCount
                val capturing = frames != lastFrameCount
                lastFrameCount = frames
                _voiceAlert.value = voiceReachMonitor.sample(
                    nowMillis = SystemClock.elapsedRealtime(),
                    micLevel = if (capturing) voiceGate.level else 0f,
                    isMicEnabled = _isMicEnabled.value,
                    isPushToTalk = _inputMode.value == MeetingInputMode.PUSH_TO_TALK,
                    isGateOpen = voiceGate.gateOpen,
                    roomHearsMe = localIdentity != null &&
                        _speakingLevels.value.containsKey(localIdentity),
                    roomHasOthers = _room?.remoteParticipants?.isNotEmpty() == true,
                )
            }
        }
    }

    suspend fun connectIfNeeded(
        url: String,
        token: String,
        roomName: String? = null,
        avatarUrl: String? = null,
    ) {
        if (_connectionState.value == ConnectionState.CONNECTED && _roomName.value == roomName) {
            return
        }
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            disconnect()
        }
        connect(url, token, roomName, avatarUrl)
    }

    suspend fun connect(url: String, token: String, roomName: String? = null, avatarUrl: String? = null) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            _error.value = null
            _roomName.value = roomName

            val audioHandler = CallAudioSwitch(application)
            _audioHandler = audioHandler

            syncVoiceGate()
            val useDeviceNoiseSuppression =
                settingsStore.getNoiseSuppression() == NoiseSuppressionMode.DEVICE
            val room = LiveKit.create(
                application,
                overrides = LiveKitOverrides(
                    audioOptions = AudioOptions(
                        audioHandler = audioHandler,
                        javaAudioDeviceModuleCustomizer = { builder ->
                            builder.setUseHardwareNoiseSuppressor(useDeviceNoiseSuppression)
                        },
                        audioProcessorOptions = AudioProcessorOptions(
                            capturePostProcessor = voiceGate,
                        ),
                    ),
                ),
            )
            _room = room

            room.connect(url, token)

            _connectionState.value = ConnectionState.CONNECTED

            // Set avatar metadata on local participant
            if (!avatarUrl.isNullOrBlank()) {
                try {
                    val metadata = JSONObject().apply {
                        put("avatarUrl", avatarUrl)
                    }.toString()
                    room.localParticipant.updateMetadata(metadata)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set avatar metadata", e)
                }
            }

            // Restore the mic to whatever the user last set it to (unmuted by default
            // on a fresh install); camera stays off until the user turns it on. A silent
            // non-publish only flags micMediaError here — no banner during connect.
            setTrackEnabled(
                enabled = settingsStore.getMicEnabled() &&
                    _inputMode.value != MeetingInputMode.PUSH_TO_TALK,
                label = "microphone",
                deviceErrorRes = R.string.meeting_error_microphoneFailed,
                stateFlow = _isMicEnabled,
                errorFlow = _micMediaError,
                sync = ::syncMicrophoneState,
                setEnabled = { setMicrophonePublishing(room.localParticipant, it) },
                onApplied = { CallConnectionService.updateMuteState(!it) },
                reportEnableFailure = false,
            )

            try {
                room.localParticipant.setCameraEnabled(false)
                _isCameraEnabled.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable camera", e)
                _isCameraEnabled.value = false
            }

            // Notify initial participant state
            _participantVersion.value++

            // Listen for room events
            eventScope?.cancel()
            eventScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            eventScope?.launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.DataReceived -> handleDataReceived(event)
                        is RoomEvent.ActiveSpeakersChanged -> onActiveSpeakers(event.speakers)
                        is RoomEvent.ParticipantConnected -> _participantVersion.value++
                        is RoomEvent.ParticipantDisconnected -> {
                            val disconnectedIdentity = event.participant.identity?.value
                            if (disconnectedIdentity != null &&
                                _watchedStreamIdentity.value == disconnectedIdentity
                            ) {
                                _watchedStreamIdentity.value = null
                            }
                            _participantVersion.value++
                        }
                        is RoomEvent.TrackSubscribed -> {
                            if (effectiveVolumeFor(event.participant.identity?.value) == 0.0) {
                                (event.track as? RemoteAudioTrack)?.setVolume(0.0)
                            }
                            enforceStreamSubscription(event.participant, event.publication)
                            _participantVersion.value++
                        }
                        is RoomEvent.TrackUnsubscribed -> _participantVersion.value++
                        is RoomEvent.TrackPublished -> {
                            if (event.publication.source == Track.Source.SCREEN_SHARE &&
                                event.participant == room.localParticipant
                            ) {
                                _isScreenShareEnabled.value = true
                            }
                            enforceStreamSubscription(event.participant, event.publication)
                            _participantVersion.value++
                        }
                        is RoomEvent.LocalTrackSubscribed -> _participantVersion.value++
                        is RoomEvent.TrackUnpublished -> {
                            if (event.publication.source == Track.Source.SCREEN_SHARE) {
                                if (event.participant == room.localParticipant) {
                                    _isScreenShareEnabled.value = false
                                } else if (
                                    event.participant.identity?.value ==
                                        _watchedStreamIdentity.value
                                ) {
                                    _watchedStreamIdentity.value = null
                                }
                            }
                            _participantVersion.value++
                        }
                        // When a track is muted/unmuted (e.g. camera turned off/on),
                        // increment participantVersion so UI recomposes and can switch
                        // between video and avatar.
                        is RoomEvent.TrackMuted -> {
                            if (event.participant == room.localParticipant) {
                                when (event.publication.source) {
                                    Track.Source.MICROPHONE -> {
                                        _isMicEnabled.value = false
                                        CallConnectionService.updateMuteState(true)
                                    }
                                    Track.Source.CAMERA -> _isCameraEnabled.value = false
                                    else -> Unit
                                }
                            }
                            _participantVersion.value++
                        }
                        is RoomEvent.TrackUnmuted -> {
                            if (event.participant == room.localParticipant) {
                                when (event.publication.source) {
                                    Track.Source.MICROPHONE -> {
                                        _isMicEnabled.value = true
                                        _micMediaError.value = false
                                        CallConnectionService.updateMuteState(false)
                                    }
                                    Track.Source.CAMERA -> {
                                        _isCameraEnabled.value = true
                                        _cameraMediaError.value = false
                                    }
                                    else -> Unit
                                }
                            }
                            _participantVersion.value++
                        }
                        is RoomEvent.Reconnecting -> {
                            _connectionState.value = ConnectionState.RECONNECTING
                        }
                        is RoomEvent.Reconnected -> {
                            _connectionState.value = ConnectionState.CONNECTED
                            _participantVersion.value++
                        }
                        is RoomEvent.Disconnected -> {
                            resetSpeakingState()
                            // "PARTICIPANT_REMOVED" is the LiveKit disconnect reason for kick
                            val kicked = event.reason.name == "PARTICIPANT_REMOVED"
                            if (kicked) _wasKicked.value = true
                            _connectionState.value = ConnectionState.DISCONNECTED
                            onDisconnected?.invoke()
                        }
                        else -> {}
                    }
                }
            }

            startVoiceReachMonitor(room.localParticipant.identity?.value)

            Log.d(TAG, "Connected to room: ${room.name}")
        } catch (e: RoomException) {
            Log.e(TAG, "Failed to connect to room", e)
            _connectionState.value = ConnectionState.FAILED
            _error.value = e.message ?: "Connection failed"
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error connecting to room", e)
            _connectionState.value = ConnectionState.FAILED
            _error.value = e.message ?: "Unexpected error"
        }
    }

    private fun handleDataReceived(event: RoomEvent.DataReceived) {
        val incoming = ChatWire.parseChat(event.data, event.topic) ?: return
        val senderName = incoming.senderName.ifBlank {
            event.participant?.name
                ?: event.participant?.identity?.value
                ?: UNKNOWN_PARTICIPANT_NAME
        }
        _chatMessages.value += ChatMessage(
            senderName = senderName,
            text = incoming.text,
            isLocal = false,
            attachments = incoming.attachments,
        )
    }

    /**
     * Mutes for the room while the microphone keeps running on the device.
     *
     * LiveKit's own mute disables the underlying track, and a disabled track stops feeding the
     * capture chain — so with a plain mute there is nothing left to measure and no way to notice
     * you talking into a muted microphone. The track therefore stays enabled here, and the room is
     * kept from hearing anything by two separate means: the publication is muted, which is what
     * every other participant's mute indicator reads, and every captured frame is zeroed by
     * [VoiceGateProcessor.forceSilence] before it can reach the encoder.
     *
     * The silencing is switched on *before* the track is re-enabled, never after, and the monitor
     * loop re-asserts it every tick so the two can never drift apart.
     */
    private suspend fun setMicrophonePublishing(
        localParticipant: LocalParticipant,
        enabled: Boolean,
    ): Boolean {
        if (enabled) {
            // Safe to lift before the call: [roomMayHearMe] still answers no until the
            // publication itself reports unmuted, so a failed unmute stays silent on its own.
            voiceGate.forceSilence = false
            return localParticipant.setMicrophoneEnabled(true)
        }

        try {
            voiceGate.forceSilence = true

            // Joining muted publishes nothing at all, and an unpublished track never reaches the
            // capture chain — so there has to be a publication before there is anything to keep
            // measuring. It is silenced from the moment it exists.
            if (localParticipant.getTrackPublication(Track.Source.MICROPHONE) == null) {
                localParticipant.setMicrophoneEnabled(true)
                // The mute has to land on a settled publication; muting the instant after
                // publishing tears the half-built track down again and capture stops with it.
                delay(MutedCaptureSettleMillis)
            }

            val published = localParticipant.setMicrophoneEnabled(false)
            localParticipant.getTrackPublication(Track.Source.MICROPHONE)?.track?.enabled = true
            return published
        } finally {
            // Always handed back. This only ever covers the publish window; being muted is not a
            // state this flag is allowed to represent, because it is set from call paths and
            // LiveKit can skip them — `setMicrophoneEnabled` returns early whenever it already
            // agrees with the requested state, which would strand the flag set and leave a
            // silent microphone behind an unmuted button. Steady-state muting belongs to
            // [roomMayHearMe], which is asked per frame and cannot be stranded.
            voiceGate.forceSilence = false
        }
    }

    private fun resetSpeakingState() {
        speakingDecayJob?.cancel()
        speakingDecayJob = null
        speakingTracker.clear()
        voiceReachMonitor.reset()
        _speakingLevels.value = emptyMap()
        _voiceAlert.value = MeetingVoiceAlert.None
    }

    fun disconnect() {
        resetSpeakingState()
        eventScope?.cancel()
        eventScope = null
        _room?.disconnect()
        _room?.release()
        _room = null
        _audioHandler = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _roomName.value = null
        _isMicEnabled.value = settingsStore.getMicEnabled()
        _micMediaError.value = false
        _isCameraEnabled.value = false
        _cameraMediaError.value = false
        _isScreenShareEnabled.value = false
        _isDeafened.value = settingsStore.getDeafened()
        micMutedBeforeDeafen = false
        _locallyMutedIdentities.value = emptySet()
        _participantVolumes.value = emptyMap()
        _participantVersion.value = 0
        _chatMessages.value = emptyList()
        _wasKicked.value = false
        _error.value = null
        _watchedStreamIdentity.value = null
        Log.d(TAG, "Disconnected from room")
    }

    private fun syncMicrophoneState() {
        val localParticipant = _room?.localParticipant ?: return
        val enabled = localParticipant.isMicrophoneEnabled
        _isMicEnabled.value = enabled
        CallConnectionService.updateMuteState(!enabled)
    }

    /**
     * Applies enable/disable to a local track publisher and keeps its paired flows honest:
     * [stateFlow] reflects what actually published, [errorFlow] flags an enable that didn't take,
     * and a thrown failure resyncs state from the SDK via [sync]. [onApplied] runs with the
     * settled value on the success path (telecom mute state, participant version bumps).
     * [reportEnableFailure] controls whether a silent non-publish also surfaces in [_error] —
     * the connect-time mic restore only flags it, the user-initiated toggles announce it.
     */
    private suspend fun setTrackEnabled(
        enabled: Boolean,
        label: String,
        @StringRes deviceErrorRes: Int,
        stateFlow: MutableStateFlow<Boolean>,
        errorFlow: MutableStateFlow<Boolean>,
        sync: () -> Unit,
        setEnabled: suspend (Boolean) -> Boolean,
        onApplied: (actuallyEnabled: Boolean) -> Unit = {},
        reportEnableFailure: Boolean = true,
    ) {
        try {
            val published = setEnabled(enabled)
            val actuallyEnabled = if (enabled) published else false
            stateFlow.value = actuallyEnabled
            onApplied(actuallyEnabled)
            if (enabled && !published) {
                errorFlow.value = true
                if (reportEnableFailure) _error.value = application.getString(deviceErrorRes)
            } else {
                errorFlow.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set $label enabled=$enabled", e)
            sync()
            if (enabled) errorFlow.value = true
            _error.value = application.getString(deviceErrorRes)
        }
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        settingsStore.setMicEnabled(enabled)
        // Unmuting while deafened is a shortcut to undeafen too, mirroring the
        // convention most call apps use for the mic button.
        if (enabled && _isDeafened.value) {
            _isDeafened.value = false
            settingsStore.setDeafened(false)
            reapplyAllVolumes()
        }
        val localParticipant = _room?.localParticipant ?: return
        if (localParticipant.isMicrophoneEnabled == enabled) {
            syncMicrophoneState()
            if (enabled) _micMediaError.value = false
            return
        }
        setTrackEnabled(
            enabled = enabled,
            label = "microphone",
            deviceErrorRes = R.string.meeting_error_microphoneFailed,
            stateFlow = _isMicEnabled,
            errorFlow = _micMediaError,
            sync = ::syncMicrophoneState,
            setEnabled = { setMicrophonePublishing(localParticipant, it) },
            onApplied = { CallConnectionService.updateMuteState(!it) },
        )
    }

    suspend fun toggleMicrophone() {
        setMicrophoneEnabled(!_isMicEnabled.value)
    }

    /**
     * Push-to-talk transmit state: enables the mic only while held, without touching the user's
     * persisted mic preference. Ignored outside push-to-talk mode.
     */
    suspend fun setPushToTalkTransmitting(active: Boolean) {
        if (_inputMode.value != MeetingInputMode.PUSH_TO_TALK) return
        val localParticipant = _room?.localParticipant ?: return
        if (active && _isDeafened.value) {
            _isDeafened.value = false
            settingsStore.setDeafened(false)
            reapplyAllVolumes()
        }
        setTrackEnabled(
            enabled = active,
            label = "microphone",
            deviceErrorRes = R.string.meeting_error_microphoneFailed,
            stateFlow = _isMicEnabled,
            errorFlow = _micMediaError,
            sync = ::syncMicrophoneState,
            setEnabled = { setMicrophonePublishing(localParticipant, it) },
            onApplied = { CallConnectionService.updateMuteState(!it) },
        )
    }

    /** Switch input modes: push-to-talk holds the mic closed until pressed, voice activity restores the persisted mic state. */
    suspend fun setInputMode(mode: MeetingInputMode) {
        if (_inputMode.value == mode) return
        settingsStore.setInputMode(mode)
        _inputMode.value = mode
        syncVoiceGate()
        when (mode) {
            MeetingInputMode.PUSH_TO_TALK -> setPushToTalkTransmitting(false)
            MeetingInputMode.VOICE_ACTIVITY -> setMicrophoneEnabled(settingsStore.getMicEnabled())
        }
    }

    fun setAutoSensitivity(value: Boolean) {
        settingsStore.setAutoSensitivity(value)
        _autoSensitivity.value = value
        syncVoiceGate()
    }

    fun setVoiceSensitivity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        settingsStore.setVoiceSensitivity(clamped)
        _voiceSensitivity.value = clamped
        syncVoiceGate()
    }

    suspend fun toggleDeafen() {
        val deafening = !_isDeafened.value
        settingsStore.setDeafened(deafening)
        if (deafening) {
            micMutedBeforeDeafen = !_isMicEnabled.value
            _isDeafened.value = true
            reapplyAllVolumes()
            if (_isMicEnabled.value) {
                setMicrophoneEnabled(false)
            }
        } else {
            _isDeafened.value = false
            reapplyAllVolumes()
            if (!micMutedBeforeDeafen) {
                setMicrophoneEnabled(true)
            }
        }
    }

    // Muted-for-me only: does not call the server and does not affect what other participants hear.
    fun toggleLocalMute(identity: String) {
        _locallyMutedIdentities.value = if (identity in _locallyMutedIdentities.value) {
            _locallyMutedIdentities.value - identity
        } else {
            _locallyMutedIdentities.value + identity
        }
        val participant = _room?.remoteParticipants?.values?.find { it.identity?.value == identity } ?: return
        val volume = effectiveVolumeFor(identity)
        for ((_, track) in participant.audioTrackPublications) {
            (track as? RemoteAudioTrack)?.setVolume(volume)
        }
    }

    /** Adjust how loud [identity] plays for this viewer only. */
    fun setParticipantVolume(identity: String, volume: Float) {
        _participantVolumes.value =
            _participantVolumes.value + (identity to volume.coerceIn(0f, 1f))
        val participant = _room?.remoteParticipants?.values
            ?.find { it.identity?.value == identity } ?: return
        val effective = effectiveVolumeFor(identity)
        for ((_, track) in participant.audioTrackPublications) {
            (track as? RemoteAudioTrack)?.setVolume(effective)
        }
    }

    private fun effectiveVolumeFor(identity: String?): Double {
        if (_isDeafened.value) return 0.0
        if (identity != null && identity in _locallyMutedIdentities.value) return 0.0
        return (identity?.let { _participantVolumes.value[it] } ?: 1f).toDouble()
    }

    private fun reapplyAllVolumes() {
        val room = _room ?: return
        for (participant in room.remoteParticipants.values) {
            val volume = effectiveVolumeFor(participant.identity?.value)
            for ((_, track) in participant.audioTrackPublications) {
                (track as? RemoteAudioTrack)?.setVolume(volume)
            }
        }
    }

    private fun syncCameraState() {
        val localParticipant = _room?.localParticipant ?: return
        _isCameraEnabled.value = localParticipant.isCameraEnabled
    }

    suspend fun toggleCamera() {
        val localParticipant = _room?.localParticipant ?: return
        val enabled = !_isCameraEnabled.value
        setTrackEnabled(
            enabled = enabled,
            label = "camera",
            deviceErrorRes = R.string.meeting_error_cameraFailed,
            stateFlow = _isCameraEnabled,
            errorFlow = _cameraMediaError,
            sync = ::syncCameraState,
            setEnabled = { localParticipant.setCameraEnabled(it) },
            onApplied = { _participantVersion.value++ },
        )
    }

    fun switchCamera() {
        if (!_isCameraEnabled.value) return
        val localParticipant = _room?.localParticipant ?: return
        val videoTrack = localParticipant.getTrackPublication(Track.Source.CAMERA)
            ?.track as? LocalVideoTrack ?: return

        try {
            val nextPosition = if (videoTrack.options.position == CameraPosition.FRONT) {
                CameraPosition.BACK
            } else {
                CameraPosition.FRONT
            }
            // Use switchCamera (capturer flip) — restartTrack disposes the RTC track and
            // races with LiveKit's RTCMetricsManager, crashing the app.
            videoTrack.switchCamera(position = nextPosition)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch camera", e)
            _error.value = application.getString(R.string.meeting_error_cameraSwitchFailed)
        }
    }

    suspend fun startScreenShare(mediaProjectionPermissionResultData: Intent): Boolean {
        val localParticipant = _room?.localParticipant ?: return false
        try {
            ensureScreenShareNotificationChannel()
            val published = localParticipant.setScreenShareEnabled(
                true,
                ScreenCaptureParams(
                    mediaProjectionPermissionResultData = mediaProjectionPermissionResultData,
                    notificationId = SCREEN_SHARE_NOTIFICATION_ID,
                    notification = buildScreenShareNotification(),
                    onStop = {
                        eventScope?.launch {
                            _isScreenShareEnabled.value = false
                        }
                    },
                ),
            )
            if (!published) {
                _isScreenShareEnabled.value = false
                _error.value = application.getString(R.string.meeting_error_screenShareFailed)
                return false
            }
            _isScreenShareEnabled.value = localParticipant.isScreenShareEnabled
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen share", e)
            _isScreenShareEnabled.value = false
            _error.value = application.getString(R.string.meeting_error_screenShareUnavailable)
            return false
        }
    }

    suspend fun stopScreenShare() {
        val localParticipant = _room?.localParticipant ?: return
        try {
            localParticipant.setScreenShareEnabled(false)
            _isScreenShareEnabled.value = localParticipant.isScreenShareEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop screen share", e)
            _error.value = application.getString(R.string.meeting_error_screenShareStopFailed)
        }
    }

    /**
     * Watch [identity]'s screenshare (or stop watching with null). At most one stream plays at a
     * time: subscribing to the new one unsubscribes every other share for this viewer only.
     */
    fun watchStream(identity: String?) {
        val room = _room ?: return
        _watchedStreamIdentity.value = identity
        for (participant in room.remoteParticipants.values) {
            val publication = participant.getTrackPublication(Track.Source.SCREEN_SHARE)
                as? RemoteTrackPublication ?: continue
            val shouldWatch = participant.identity?.value == identity
            if (publication.subscribed != shouldWatch) {
                publication.setSubscribed(shouldWatch)
            }
        }
        _participantVersion.value++
    }

    /**
     * Screenshares are opt-in per viewer: whenever one is published (or autoSubscribe races one
     * in), keep only the watched participant's share subscribed.
     */
    private fun enforceStreamSubscription(
        participant: Participant,
        publication: TrackPublication,
    ) {
        if (publication.source != Track.Source.SCREEN_SHARE) return
        val remotePublication = publication as? RemoteTrackPublication ?: return
        val shouldWatch = participant.identity?.value == _watchedStreamIdentity.value
        if (remotePublication.subscribed != shouldWatch) {
            remotePublication.setSubscribed(shouldWatch)
        }
    }

    /** Publishes on the reliable data channel; false (plus a log line) when publishing throws. */
    private suspend fun publishData(topic: String, data: ByteArray): Boolean {
        val localParticipant = _room?.localParticipant ?: return false
        return try {
            localParticipant.publishData(
                data = data,
                reliability = DataPublishReliability.RELIABLE,
                topic = topic,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish data on topic '$topic'", e)
            false
        }
    }

    private fun ensureScreenShareNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        application.registerNotificationChannel(
            id = SCREEN_SHARE_CHANNEL_ID,
            name = application.getString(R.string.screen_share_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
            description = application.getString(R.string.screen_share_channel_description),
        )
    }

    private fun buildScreenShareNotification() =
        NotificationCompat.Builder(application, SCREEN_SHARE_CHANNEL_ID)
            .setContentTitle(application.getString(R.string.screen_share_notification_title))
            .setContentText(application.getString(R.string.screen_share_notification_text))
            .setSmallIcon(R.drawable.ic_call_notification)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    suspend fun sendChatMessage(text: String, attachments: List<ChatAttachment> = emptyList()) {
        val room = _room ?: return
        val localParticipant = room.localParticipant
        val name = localParticipant.name ?: localParticipant.identity?.value ?: UNKNOWN_PARTICIPANT_NAME
        val identity = localParticipant.identity?.value ?: ""

        val sent = publishData(
            ChatWire.CHAT_DATA_TOPIC,
            ChatWire.encodeChat(
                senderName = name,
                senderIdentity = identity,
                text = text,
                attachments = attachments,
            ),
        )
        if (sent) {
            _chatMessages.value += ChatMessage(
                senderName = name,
                text = text,
                isLocal = true,
                attachments = attachments,
            )
        } else {
            _error.value = application.getString(R.string.meeting_error_messageSendFailed)
        }
    }

    fun getLocalParticipant(): LocalParticipant? {
        return _room?.localParticipant
    }

    companion object {
        private const val TAG = "RoomManager"
        private const val SCREEN_SHARE_CHANNEL_ID = "bedrud_screen_share"
        private const val SCREEN_SHARE_NOTIFICATION_ID = 1002

        /** Time a publication needs to settle before muting it keeps the capture chain alive. */
        private const val MutedCaptureSettleMillis = 400L

        /** Display name used when a participant has neither a name nor an identity. */
        const val UNKNOWN_PARTICIPANT_NAME = "Unknown"

        // Backoff schedules (ms) for the peer-to-peer stage gossip: newly joined peers re-ask for the
        // current stage, and stage owners re-broadcast their state, so a dropped packet self-heals.
    }
}
