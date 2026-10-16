package com.bedrud.app.core.livekit

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.meeting.chat.ChatWire
import com.bedrud.app.core.meeting.presence.PresenceWire
import com.bedrud.app.testutil.InMemorySharedPreferences
import com.bedrud.app.ui.screens.settings.SettingsStore
import io.livekit.android.LiveKit
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.EventListenable
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalTrackPublication
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LocalIdentity = "me"
private const val LocalName = "Me"
private const val OtherIdentity = "other"
private const val OtherName = "Other"
private const val Reaction = "👍"

/** A string resource as a mocked application hands it back, so no test reads a translation. */
private fun string(id: Int) = "string:$id"

private fun settingsStore(): SettingsStore {
    val preferences = InMemorySharedPreferences()
    val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns preferences
    }
    return SettingsStore(context)
}

private fun application() = mockk<Application>(relaxed = true) {
    every { getString(any()) } answers { string(firstArg()) }
}

/** Another participant, with one audio track to be made louder or quieter for this viewer. */
private fun remote(
    identity: String = OtherIdentity,
    name: String = OtherName,
    metadata: String? = null,
    audio: RemoteAudioTrack = mockk(relaxed = true),
) = mockk<RemoteParticipant>(relaxed = true) {
    every { this@mockk.identity } returns Participant.Identity(identity)
    every { this@mockk.name } returns name
    every { this@mockk.metadata } returns metadata
    every { audioTrackPublications } returns listOf(mockk<TrackPublication>() to audio)
}

/**
 * A call as these tests drive it: the manager, joined to a LiveKit room that is a stand-in, with
 * the room's events in the test's hands and every packet this device sent kept in [sent].
 */
private class Call(settings: SettingsStore, val remotes: MutableList<RemoteParticipant>) {
    val events = MutableSharedFlow<RoomEvent>()
    val sent = mutableListOf<Pair<String?, ByteArray>>()

    /** What the next packet this device sends comes back with. */
    var sendResult: Result<Unit> = Result.success(Unit)
    var localMetadata: String? = null
    var micPublished = false

    val local = mockk<LocalParticipant>(relaxed = true) {
        every { identity } returns Participant.Identity(LocalIdentity)
        every { name } returns LocalName
        every { metadata } answers { localMetadata }
        every { updateMetadata(any()) } answers { localMetadata = firstArg() }
        every { isMicrophoneEnabled } answers { micPublished }
        coEvery { setMicrophoneEnabled(any()) } answers { micPublished = firstArg(); true }
        coEvery { setCameraEnabled(any()) } returns true
        every { getTrackPublication(Track.Source.MICROPHONE) } returns mockk<LocalTrackPublication>(relaxed = true) {
            every { muted } answers { !micPublished }
        }
        coEvery { publishData(any(), any(), any(), any()) } answers {
            sent += thirdArg<String?>() to firstArg<ByteArray>()
            sendResult
        }
    }

    val room = mockk<Room>(relaxed = true) {
        every { localParticipant } returns local
        every { remoteParticipants } answers { remotes.associateBy { it.identity!! } }
        every { this@mockk.events } returns mockk<EventListenable<RoomEvent>> {
            every { events } returns this@Call.events
        }
        coEvery { connect(any(), any(), any()) } returns null
    }

    val manager = RoomManager(application(), settings).also {
        every { LiveKit.create(any(), any(), any()) } returns room
    }

    fun join() = runBlocking { manager.connect("wss://bedrud.example", "token", "room") }

    fun receive(event: RoomEvent) = runBlocking { events.emit(event) }

    // A stand-in event: the real one asks for an encryption type from LiveKit's protobuf classes,
    // which are not on the app's own classpath, and the manager reads none of it.
    fun receiveData(topic: String, data: ByteArray, from: RemoteParticipant? = remotes.firstOrNull()) =
        receive(
            mockk<RoomEvent.DataReceived> {
                every { this@mockk.data } returns data
                every { this@mockk.topic } returns topic
                every { participant } returns from
            }
        )

    /** The deafen state this device last announced on the presence topic, if it announced one. */
    fun lastAnnouncedDeafen(): Boolean? = sent.lastOrNull { it.first == PresenceWire.PRESENCE_DATA_TOPIC }
        ?.let { (topic, data) -> PresenceWire.parseDeafenState(data, topic)?.deafened }
}

/**
 * The call's own state and what it tells the room: joining and leaving, the microphone and deafen,
 * each participant's volume for this viewer, who is deafened, and the chat. LiveKit's room is a
 * stand-in whose events the tests deliver, so every rule here is checked without a server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomManagerTest {

    private fun call(
        settings: SettingsStore = settingsStore(),
        vararg remotes: RemoteParticipant,
    ) = Call(settings, remotes.toMutableList())

    private fun joined(settings: SettingsStore = settingsStore(), vararg remotes: RemoteParticipant) =
        call(settings, *remotes).also { it.join() }

    @Before
    fun setUp() {
        // The call's event loop runs on the main dispatcher, which a unit test has to provide.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        // Held at zero, which keeps the call inside its quiet start: no notification tone plays.
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        mockkObject(LiveKit)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    // ── Joining and leaving ─────────────────────────────────────────────────────

    @Test
    fun `should be connected with the mic the user left on`() {
        val call = joined()

        assertEquals(ConnectionState.CONNECTED, call.manager.connectionState.value)
        assertTrue(call.manager.isMicEnabled.value)
        assertEquals(LocalIdentity, call.manager.localIdentity)
    }

    @Test
    fun `should join muted in push-to-talk`() {
        val settings = settingsStore().apply { setInputMode(MeetingInputMode.PUSH_TO_TALK) }

        val call = joined(settings)

        assertFalse(call.manager.isMicEnabled.value)
    }

    @Test
    fun `should report a join the room refused`() {
        val call = call()
        coEvery { call.room.connect(any(), any(), any()) } throws IllegalStateException("refused")

        call.join()

        assertEquals(ConnectionState.FAILED, call.manager.connectionState.value)
        assertEquals("refused", call.manager.error.value)
    }

    @Test
    fun `should mark a removal by a moderator as a kick`() {
        val call = joined()
        var ended = false
        call.manager.onDisconnected = { ended = true }

        call.receive(RoomEvent.Disconnected(call.room, null, DisconnectReason.PARTICIPANT_REMOVED))

        assertTrue(call.manager.wasKicked.value)
        assertEquals(ConnectionState.DISCONNECTED, call.manager.connectionState.value)
        assertTrue(ended)
    }

    @Test
    fun `should not mark leaving of one's own accord as a kick`() {
        val call = joined()

        call.receive(RoomEvent.Disconnected(call.room, null, DisconnectReason.CLIENT_INITIATED))

        assertFalse(call.manager.wasKicked.value)
        assertEquals(ConnectionState.DISCONNECTED, call.manager.connectionState.value)
    }

    @Test
    fun `should follow the connection through a reconnect`() {
        val call = joined()

        call.receive(RoomEvent.Reconnecting(call.room))
        assertEquals(ConnectionState.RECONNECTING, call.manager.connectionState.value)
        call.receive(RoomEvent.Reconnected(call.room))
        assertEquals(ConnectionState.CONNECTED, call.manager.connectionState.value)
    }

    @Test
    fun `should leave nothing of the call behind on disconnect`() {
        val call = joined(settingsStore(), remote())
        call.receiveData(ChatWire.CHAT_DATA_TOPIC, ChatWire.encodeChat(OtherName, OtherIdentity, "hello", emptyList()).single())
        call.manager.setParticipantVolume(OtherIdentity, 0.5f)

        call.manager.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, call.manager.connectionState.value)
        assertTrue(call.manager.chatMessages.value.isEmpty())
        assertTrue(call.manager.participantVolumes.value.isEmpty())
        assertTrue(call.manager.participantNames.value.isEmpty())
        assertNull(call.manager.room)
        verify { call.room.disconnect() }
        verify { call.room.release() }
    }

    // ── Microphone and deafen ───────────────────────────────────────────────────

    /**
     * Deafening used to wait for the room to hear about it first, and that announcement can stall
     * for seconds while the outgoing connection is not up. The toggle now lands before anything
     * goes over the network.
     */
    @Test
    fun `should deafen at once when toggled`() {
        val settings = settingsStore()
        val manager = RoomManager(application(), settings)

        manager.toggleDeafen()

        assertTrue(manager.isDeafened.value)
        assertTrue(settings.getDeafened())
    }

    @Test
    fun `should end where it started after two quick toggles`() {
        val manager = RoomManager(application(), settingsStore())

        manager.toggleDeafen()
        manager.toggleDeafen()

        assertFalse(manager.isDeafened.value)
    }

    @Test
    fun `should close the mic on deafen, open it again after, and tell the room both times`() {
        val call = joined()

        call.manager.toggleDeafen()
        assertFalse(call.manager.isMicEnabled.value)
        assertEquals(true, call.lastAnnouncedDeafen())

        call.manager.toggleDeafen()
        assertTrue(call.manager.isMicEnabled.value)
        assertEquals(false, call.lastAnnouncedDeafen())
    }

    @Test
    fun `should leave a mic that was muted before deafen muted after it`() {
        val call = joined()
        runBlocking { call.manager.setMicrophoneEnabled(false) }

        call.manager.toggleDeafen()
        call.manager.toggleDeafen()

        assertFalse(call.manager.isMicEnabled.value)
    }

    /** Most call apps undeafen when the mic opens; the room has to hear that, or the badge stays. */
    @Test
    fun `should end deafen and tell the room when the mic is opened`() {
        val call = joined()
        call.manager.toggleDeafen()

        runBlocking { call.manager.setMicrophoneEnabled(true) }

        assertFalse(call.manager.isDeafened.value)
        assertTrue(call.manager.isMicEnabled.value)
        assertEquals(false, call.lastAnnouncedDeafen())
    }

    @Test
    fun `should end deafen when push-to-talk transmits`() {
        val call = joined(settingsStore().apply { setInputMode(MeetingInputMode.PUSH_TO_TALK) })
        call.manager.toggleDeafen()

        runBlocking { call.manager.setPushToTalkTransmitting(true) }

        assertFalse(call.manager.isDeafened.value)
        assertTrue(call.manager.isMicEnabled.value)
    }

    @Test
    fun `should ignore push-to-talk outside push-to-talk mode`() {
        val call = joined()
        runBlocking { call.manager.setMicrophoneEnabled(false) }

        runBlocking { call.manager.setPushToTalkTransmitting(true) }

        assertFalse(call.manager.isMicEnabled.value)
    }

    /** A mute applied from elsewhere, a moderator's or the system's, still shows on the button. */
    @Test
    fun `should show the mic muted when the room mutes it`() {
        val call = joined()
        val micPublication = mockk<TrackPublication> { every { source } returns Track.Source.MICROPHONE }

        call.receive(RoomEvent.TrackMuted(call.room, micPublication, call.local))

        assertFalse(call.manager.isMicEnabled.value)
    }

    // ── Volume, for this viewer only ────────────────────────────────────────────

    @Test
    fun `should silence everyone while deafened and give back their volume after`() {
        val audio = mockk<RemoteAudioTrack>(relaxed = true)
        val call = joined(settingsStore(), remote(audio = audio))
        call.manager.setParticipantVolume(OtherIdentity, 0.5f)

        call.manager.toggleDeafen()
        verify { audio.setVolume(0.0) }

        call.manager.toggleDeafen()
        verify(exactly = 2) { audio.setVolume(0.5) }
    }

    @Test
    fun `should silence a locally muted participant, and give their volume back after`() {
        val audio = mockk<RemoteAudioTrack>(relaxed = true)
        val call = joined(settingsStore(), remote(audio = audio))

        call.manager.toggleLocalMute(OtherIdentity)
        verify { audio.setVolume(0.0) }
        assertTrue(OtherIdentity in call.manager.locallyMutedIdentities.value)

        call.manager.toggleLocalMute(OtherIdentity)
        verify { audio.setVolume(1.0) }
        assertTrue(call.sent.isEmpty() || call.sent.none { it.first == ChatWire.CHAT_DATA_TOPIC })
    }

    @Test
    fun `should hold a participant's volume within 0 to 1`() {
        val call = joined(settingsStore(), remote())

        call.manager.setParticipantVolume(OtherIdentity, 1.5f)
        assertEquals(1f, call.manager.participantVolumes.value[OtherIdentity])
        call.manager.setParticipantVolume(OtherIdentity, -0.5f)
        assertEquals(0f, call.manager.participantVolumes.value[OtherIdentity])
    }

    // ── Who is deafened ─────────────────────────────────────────────────────────

    @Test
    fun `should mark a participant who announces deafen`() {
        val call = joined(settingsStore(), remote())

        call.receiveData(PresenceWire.PRESENCE_DATA_TOPIC, PresenceWire.encodeDeafenState(OtherIdentity, true))

        assertTrue(OtherIdentity in call.manager.deafenedIdentities.value)
    }

    @Test
    fun `should mark a participant whose metadata says deafened`() {
        val other = remote(metadata = ParticipantMetadata.withDeafened(null, true))
        val call = joined(settingsStore(), other)

        assertTrue(OtherIdentity in call.manager.deafenedIdentities.value)
    }

    /** The announcement is the more recent word, so it decides over metadata. */
    @Test
    fun `should let a live announcement override metadata`() {
        val other = remote(metadata = ParticipantMetadata.withDeafened(null, true))
        val call = joined(settingsStore(), other)

        call.receiveData(PresenceWire.PRESENCE_DATA_TOPIC, PresenceWire.encodeDeafenState(OtherIdentity, false))

        assertFalse(OtherIdentity in call.manager.deafenedIdentities.value)
    }

    /** Kept, it would badge the next person to arrive on the same identity. */
    @Test
    fun `should forget the announcement of a participant who left`() {
        val other = remote()
        val call = joined(settingsStore(), other)
        call.receiveData(PresenceWire.PRESENCE_DATA_TOPIC, PresenceWire.encodeDeafenState(OtherIdentity, true))

        call.remotes.remove(other)
        call.receive(RoomEvent.ParticipantDisconnected(call.room, other))

        assertFalse(OtherIdentity in call.manager.deafenedIdentities.value)
    }

    // ── Chat ────────────────────────────────────────────────────────────────────

    @Test
    fun `should add another participant's message under their name`() {
        val call = joined(settingsStore(), remote())

        call.receiveData(ChatWire.CHAT_DATA_TOPIC, ChatWire.encodeChat(OtherName, OtherIdentity, "hello", emptyList()).single())

        val message = call.manager.chatMessages.value.single()
        assertEquals("hello", message.text)
        assertEquals(OtherName, message.senderName)
        assertFalse(message.isLocal)
        assertEquals(OtherName, call.manager.participantNames.value[OtherIdentity])
    }

    @Test
    fun `should show a sent message as the user's own`() {
        val call = joined()

        runBlocking { call.manager.sendChatMessage("hi") }

        val message = call.manager.chatMessages.value.single()
        assertTrue(message.isLocal)
        assertEquals(LocalName, message.senderName)
        assertTrue(call.sent.any { it.first == ChatWire.CHAT_DATA_TOPIC })
    }

    @Test
    fun `should report a message whose sending threw`() {
        val call = joined()
        coEvery { call.local.publishData(any(), any(), any(), any()) } throws IllegalStateException("closed")

        runBlocking { call.manager.sendChatMessage("hi") }

        assertTrue(call.manager.chatMessages.value.isEmpty())
        assertEquals(string(R.string.meeting_error_messageSendFailed), call.manager.error.value)
    }

    /** A block has to hold whatever managed to call send, not only the composer the UI hides. */
    @Test
    fun `should send nothing while a moderator has blocked chat`() {
        val call = call()
        call.localMetadata = """{"chatBlocked":true}"""
        call.join()

        runBlocking { call.manager.sendChatMessage("hi") }

        assertTrue(call.manager.isChatBlocked.value)
        assertTrue(call.sent.none { it.first == ChatWire.CHAT_DATA_TOPIC })
        assertTrue(call.manager.chatMessages.value.isEmpty())
    }

    @Test
    fun `should show a reaction once it is sent`() {
        val call = joined(settingsStore(), remote())
        call.receiveData(ChatWire.CHAT_DATA_TOPIC, ChatWire.encodeChat(OtherName, OtherIdentity, "hello", emptyList(), id = "message").single())

        runBlocking { call.manager.reactToMessage("message", Reaction) }

        assertEquals(Reaction, call.manager.chatMessages.value.single().reactions[LocalIdentity])
    }

    @Test
    fun `should fold another participant's reaction into the message it names`() {
        val call = joined(settingsStore(), remote())
        call.receiveData(ChatWire.CHAT_DATA_TOPIC, ChatWire.encodeChat(OtherName, OtherIdentity, "hello", emptyList(), id = "message").single())

        call.receiveData(ChatWire.CHAT_DATA_TOPIC, ChatWire.encodeReaction("message", OtherIdentity, Reaction))
        call.receiveData(ChatWire.CHAT_DATA_TOPIC, ChatWire.encodeReaction("never-arrived", OtherIdentity, Reaction))

        assertEquals(mapOf(OtherIdentity to Reaction), call.manager.chatMessages.value.single().reactions)
    }

    // ── The chat's plain values ─────────────────────────────────────────────────

    @Test
    fun `should give a message the time it was made and mark it as someone else's by default`() {
        val message = ChatMessage(id = "m1", senderName = "Alice", senderIdentity = "u-1", text = "Hello")

        assertTrue(message.timestamp > 0)
        assertFalse(message.isLocal)
    }

    @Test
    fun `should tell two messages apart by any field`() {
        val first = ChatMessage("m1", "A", "u-1", "Hi", timestamp = 100L, isLocal = false)

        assertEquals(first, first.copy())
        assertNotEquals(first, first.copy(senderName = "B"))
    }
}
