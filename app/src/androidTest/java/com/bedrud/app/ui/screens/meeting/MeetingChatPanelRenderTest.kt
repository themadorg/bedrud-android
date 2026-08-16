package com.bedrud.app.ui.screens.meeting

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.ui.theme.BedrudTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * Renders the chat panel against fixed data and writes what it drew to the device.
 *
 * Not an assertion test: composing at all is the check, and the images are what a reviewer looks
 * at. Every name here is invented, so the captures carry nothing belonging to a real account.
 */
class MeetingChatPanelRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun messages(): List<ChatMessage> {
        val start = 1_700_000_000_000L
        var id = 0
        fun at(minutes: Long) = start + minutes * 60_000
        fun message(name: String, identity: String, text: String, minutes: Long, local: Boolean) =
            ChatMessage(
                id = "m${id++}",
                senderName = name,
                senderIdentity = identity,
                text = text,
                timestamp = at(minutes),
                isLocal = local,
            )
        return listOf(
            message("سارا احمدی", "u-1", "سلام، صدای من رو دارید؟", 0, false),
            message("سارا احمدی", "u-1", "دوربینم رو الان روشن می‌کنم", 0, false),
            message("You", "u-me", "Yes, coming through clearly.", 1, true),
            message("You", "u-me", "Give me a second to share the deck.", 1, true),
            message("Miriam Okonkwo", "u-2", "Take your time 🙂", 2, false),
            message("Miriam Okonkwo", "u-2", "I have the numbers from last week open too", 2, false),
            message("Miriam Okonkwo", "u-2", "so shout if you want them on screen", 2, false),
            message("You", "u-me", "Perfect, thanks.", 9, true),
        )
    }

    @Test
    fun chatPanel() {
        capture("chat-panel") {
            MeetingChatPanel(
                messages = messages(),
                input = "",
                onInputChange = {},
                onSend = {},
                onSendAttachment = { _, _ -> },
                onClose = {},
                imageContext = null,
                sendDisabledReason = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun chatPanelWithSendingBlocked() {
        capture("chat-panel-blocked") {
            MeetingChatPanel(
                messages = messages(),
                input = "",
                onInputChange = {},
                onSend = {},
                onSendAttachment = { _, _ -> },
                onClose = {},
                imageContext = null,
                sendDisabledReason = R.string.meeting_chat_blockedByModerator,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            BedrudTheme(darkTheme = true) { content() }
        }
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
