package com.bedrud.app.ui.screens.meeting

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudTheme
import com.twilio.audioswitch.AudioDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Builds an AudioSwitch device the way the library does. Its constructors are Kotlin-internal, which
 * hides them from Kotlin callers only; they are public in the bytecode.
 */
private inline fun <reified T : AudioDevice> audioDevice(name: String): T =
    T::class.java.getConstructor(String::class.java).newInstance(name)

/** What the output picker does once an output is picked. */
class MeetingAudioSourceSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Like the input-mode and noise-suppression pickers, it has nothing left to ask after a pick. */
    @Test
    fun shouldCloseOnceAnOutputIsPicked() {
        var dismissCount = 0
        val earpiece = audioDevice<AudioDevice.Earpiece>(EarpieceName)
        val speaker = audioDevice<AudioDevice.Speakerphone>(SpeakerName)
        compose.setContent {
            BedrudTheme {
                MeetingAudioSourceSheet(
                    audioHandler = null,
                    audioState = MeetingAudioState(
                        availableDevices = listOf(earpiece, speaker),
                        selectedDevice = earpiece,
                    ),
                    onDismiss = { dismissCount++ },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.meeting_audio_device_speaker)).performClick()
        compose.waitForIdle()

        assertEquals(1, dismissCount)
    }

    private companion object {
        const val EarpieceName = "Earpiece"
        const val SpeakerName = "Speakerphone"
    }
}
