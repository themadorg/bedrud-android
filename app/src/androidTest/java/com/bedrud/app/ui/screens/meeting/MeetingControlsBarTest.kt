package com.bedrud.app.ui.screens.meeting

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The five call controls: that each one does its own job, that push-to-talk transmits only while it
 * is held, and what the mic key and the chat badge say about the call.
 */
class MeetingControlsBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** What the controls asked for, in the order they asked. */
    private val requests = mutableListOf<String>()

    private fun string(resource: Int): String = context.getString(resource)

    private fun showControls(
        inputMode: MeetingInputMode = MeetingInputMode.VOICE_ACTIVITY,
        isMicEnabled: Boolean = true,
        micHasError: Boolean = false,
        unreadCount: Int = 0,
        connectionState: ConnectionState = ConnectionState.CONNECTED,
        voiceAlert: MeetingVoiceAlert = MeetingVoiceAlert.None,
    ) {
        compose.setThemedContentAt(FontScales.Default) {
            MeetingCallControlsRow(
                isMicEnabled = isMicEnabled,
                isCameraEnabled = true,
                micHasError = micHasError,
                cameraHasError = false,
                isScreenShareEnabled = false,
                showChat = false,
                unreadCount = unreadCount,
                inputMode = inputMode,
                connectionState = connectionState,
                voiceAlert = voiceAlert,
                onPushToTalkChange = { held -> requests += "pushToTalk:$held" },
                onToggleMic = { requests += "mic" },
                onToggleCamera = { requests += "camera" },
                onToggleScreenShare = { requests += "screenShare" },
                onToggleChat = { requests += "chat" },
                onEndCall = { requests += "endCall" },
            )
        }
    }

    private fun tap(description: Int) {
        compose.onNodeWithContentDescription(string(description)).performClick()
        compose.waitForIdle()
    }

    private fun hasStateDescription(description: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)

    @Test
    fun shouldSendEachControlsTapToItsOwnAction() {
        showControls()

        tap(R.string.meeting_contentDescription_toggleCamera)
        tap(R.string.meeting_contentDescription_toggleScreenShare)
        tap(R.string.meeting_contentDescription_toggleMic)
        tap(R.string.meeting_contentDescription_toggleChat)
        tap(R.string.meeting_contentDescription_leaveCall)

        assertEquals(listOf("camera", "screenShare", "mic", "chat", "endCall"), requests)
    }

    @Test
    fun shouldTransmitOnlyWhilePushToTalkIsHeld() {
        showControls(inputMode = MeetingInputMode.PUSH_TO_TALK, isMicEnabled = false)
        val key = compose.onAllNodesWithText(string(R.string.meeting_audio_mode_pushToTalk))[0]

        key.performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(listOf("pushToTalk:true"), requests)

        key.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("pushToTalk:true", "pushToTalk:false"), requests)
    }

    /** Push to talk is held, never toggled: a tap must not leave the microphone open. */
    @Test
    fun shouldNeverToggleTheMicrophoneInPushToTalk() {
        showControls(inputMode = MeetingInputMode.PUSH_TO_TALK, isMicEnabled = false)

        compose.onAllNodesWithText(string(R.string.meeting_audio_mode_pushToTalk))[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf("pushToTalk:true", "pushToTalk:false"), requests)
    }

    @Test
    fun shouldCountUnreadMessagesUpToNine() {
        showControls(unreadCount = 9)

        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("9+").assertDoesNotExist()
    }

    @Test
    fun shouldShowMoreThanNineUnreadMessagesAsNinePlus() {
        showControls(unreadCount = 10)

        compose.onNodeWithText("9+").assertExists()
        compose.onNodeWithText("10").assertDoesNotExist()
    }

    @Test
    fun shouldShowNoBadgeWithNothingUnread() {
        showControls(unreadCount = 0)

        compose.onNodeWithText("0").assertDoesNotExist()
    }

    /** A reconnect stops the voice reaching the room whatever else is wrong, so it is what the key says. */
    @Test
    fun shouldSayTheCallIsReconnectingAboveAnyVoiceAlert() {
        showControls(connectionState = ConnectionState.RECONNECTING, voiceAlert = MeetingVoiceAlert.Muted)

        compose.onNode(hasStateDescription(string(R.string.meeting_status_reconnecting))).assertExists()
    }

    @Test
    fun shouldSayWhyTheVoiceIsNotReachingTheRoom() {
        showControls(isMicEnabled = false, voiceAlert = MeetingVoiceAlert.Muted)

        compose.onNode(hasStateDescription(string(R.string.meeting_voiceAlert_muted))).assertExists()
    }

    @Test
    fun shouldSayNothingAboutAVoiceThatReachesTheRoom() {
        showControls()

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertDoesNotExist()
    }

    /** Only the label the key shows is read out; the others are laid out only to hold its width. */
    @Test
    fun shouldReadOutOnlyTheMicKeysOwnLabel() {
        showControls(isMicEnabled = false)

        val key = compose.onNode(hasContentDescription(string(R.string.meeting_contentDescription_toggleMic))).fetchSemanticsNode()
        assertEquals(listOf(string(R.string.meeting_ptt_muted)), key.config[SemanticsProperties.Text].map { label -> label.text })
        assertEquals(listOf(string(R.string.meeting_contentDescription_toggleMic)), key.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun shouldReadOutOnlyThePushToTalkKeysOwnLabel() {
        showControls(inputMode = MeetingInputMode.PUSH_TO_TALK, isMicEnabled = false)

        assertEquals(1, compose.onAllNodesWithText(string(R.string.meeting_audio_mode_pushToTalk)).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.meeting_ptt_speak)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.meeting_ptt_talking)).assertDoesNotExist()
    }
}
