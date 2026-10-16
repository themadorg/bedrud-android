package com.bedrud.app.ui.screens.meeting

import com.twilio.audioswitch.AudioDevice
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order the call's output picker lists devices in: the phone, then the speaker, then whatever
 * is plugged in or paired, in the order the system reported it. It does not move with the device in
 * use, so a row is where it was the last time the picker opened.
 */
class MeetingAudioStateTest {

    // The library keeps its device constructors internal; a stand-in of each kind is all the order reads.
    private val earpiece = mockk<AudioDevice.Earpiece>()
    private val speaker = mockk<AudioDevice.Speakerphone>()
    private val wired = mockk<AudioDevice.WiredHeadset>()
    private val buds = mockk<AudioDevice.BluetoothHeadset>()
    private val car = mockk<AudioDevice.BluetoothHeadset>()

    @Test
    fun `should list the phone, then the speaker, then the rest`() {
        val listed = listOf(buds, speaker, wired, earpiece).sortedForDisplay()

        assertEquals(listOf(earpiece, speaker, buds, wired), listed)
    }

    @Test
    fun `should keep plugged-in and paired devices in the order they were reported`() {
        assertEquals(listOf(car, wired, buds), listOf(car, wired, buds).sortedForDisplay())
        assertEquals(listOf(buds, wired, car), listOf(buds, wired, car).sortedForDisplay())
    }
}
