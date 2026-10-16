package com.bedrud.app.core.call

import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.core.livekit.RoomManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ServerURL = "wss://bedrud.example"
private const val Token = "token"
private const val RoomName = "weekly-sync"
private const val AvatarURL = "https://bedrud.example/avatar.png"

/** Everything the session asked of the service, in the order it asked. */
private class RecordingHost : CallSession.Host {
    val calls = mutableListOf<String>()
    val shownMuted = mutableListOf<Boolean>()

    override fun stop() { calls += "stop" }
    override fun hangUp() { calls += "hangUp" }
    override fun showMuted(isMuted: Boolean) { shownMuted += isMuted }
    override fun reportRemoval() { calls += "reportRemoval" }
    override fun onConnected() { calls += "onConnected" }
}

/**
 * What a running call decides about its room, apart from the service that keeps it alive: when to
 * give up, what to say when a moderator removes the user, which mute to believe, and what leaving
 * does to the room. The room is a stand-in whose state each test sets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallSessionTest {

    private val connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    private val isMicEnabled = MutableStateFlow(true)
    private val wasKicked = MutableStateFlow(false)
    private val onDisconnected = slot<(() -> Unit)?>()

    private val roomManager = mockk<RoomManager>(relaxed = true) {
        every { connectionState } returns this@CallSessionTest.connectionState
        every { isMicEnabled } returns this@CallSessionTest.isMicEnabled
        every { wasKicked } returns this@CallSessionTest.wasKicked
        every { onDisconnected = captureNullable(this@CallSessionTest.onDisconnected) } answers { }
        coEvery { connectIfNeeded(any(), any(), any(), any()) } returns Unit
    }

    private val host = RecordingHost()

    private fun startedSession() =
        CallSession(roomManager, CoroutineScope(UnconfinedTestDispatcher()), host).apply {
            start(ServerURL, Token, RoomName, AvatarURL)
        }

    /** Ends the call from the room's side, as LiveKit does when the server closes it. */
    private fun roomEndsTheCall() = checkNotNull(onDisconnected.captured).invoke()

    @Test
    fun `should join the room it was started for`() {
        startedSession()

        coVerify { roomManager.connectIfNeeded(ServerURL, Token, RoomName, AvatarURL) }
    }

    /** Hanging up would reset the room, and the meeting screen still has to show why it failed. */
    @Test
    fun `should stop without hanging up when the join fails`() {
        startedSession()

        connectionState.value = ConnectionState.FAILED

        assertEquals(listOf("stop"), host.calls)
    }

    @Test
    fun `should hang up when the room ends the call`() {
        startedSession()

        roomEndsTheCall()

        assertEquals(listOf("hangUp"), host.calls)
    }

    /** Said where the user lands, before hanging up resets the flag it reads. */
    @Test
    fun `should say a moderator removed the user before hanging up`() {
        startedSession()
        wasKicked.value = true

        roomEndsTheCall()

        assertEquals(listOf("reportRemoval", "hangUp"), host.calls)
    }

    /**
     * Telecom reports an unmuted call while the room is still joining, and applying it then raced
     * the room's own microphone setup into a spurious "failed to enable microphone".
     */
    @Test
    fun `should leave the microphone alone for a headset's mute before the room is connected`() {
        val session = startedSession()

        session.onTelecomMute(true)

        coVerify(exactly = 0) { roomManager.setMicrophoneEnabled(any()) }
        assertEquals(true, host.shownMuted.last())
    }

    @Test
    fun `should apply a headset's mute once the room is connected`() {
        val session = startedSession()
        connectionState.value = ConnectionState.CONNECTED

        session.onTelecomMute(true)
        coVerify { roomManager.setMicrophoneEnabled(false) }
        session.onTelecomMute(false)
        coVerify { roomManager.setMicrophoneEnabled(true) }
    }

    @Test
    fun `should show the microphone's state on the notification as it changes`() {
        startedSession()

        isMicEnabled.value = false
        isMicEnabled.value = true

        assertEquals(listOf(false, true, false), host.shownMuted)
    }

    /** A reconnect reports connected again, and the audio route has to be followed again. */
    @Test
    fun `should follow the audio route once connected, and again after a reconnect`() {
        startedSession()

        connectionState.value = ConnectionState.CONNECTED
        connectionState.value = ConnectionState.RECONNECTING
        connectionState.value = ConnectionState.CONNECTED

        assertEquals(listOf("onConnected", "onConnected"), host.calls)
    }

    @Test
    fun `should turn the microphone over when asked`() {
        val session = startedSession()

        session.toggleMute()

        coVerify { roomManager.toggleMicrophone() }
    }

    @Test
    fun `should disconnect the room when the user hung up`() {
        val session = startedSession()

        session.end(userHungUp = true)

        verify { roomManager.disconnect() }
        assertNull(onDisconnected.captured)
    }

    /** A failed join stops the call without hanging up, and the room keeps the error to show. */
    @Test
    fun `should leave the room as it is when the call stopped on its own`() {
        val session = startedSession()

        session.end(userHungUp = false)

        verify(exactly = 0) { roomManager.disconnect() }
        assertNull(onDisconnected.captured)
    }
}
