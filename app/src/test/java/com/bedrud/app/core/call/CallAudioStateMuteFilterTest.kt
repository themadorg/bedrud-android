package com.bedrud.app.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallAudioStateMuteFilterTest {

    private fun legacyFilter() = CallAudioStateMuteFilter(reportsMuteSeparately = false)

    @Test
    fun `should pass on the first mute state Telecom reports`() {
        assertEquals(false, legacyFilter().onAudioStateChanged(isMuted = false))
    }

    @Test
    fun `should pass on a mute that differs from the last one seen`() {
        val filter = legacyFilter()
        filter.onAudioStateChanged(isMuted = false)

        assertEquals(true, filter.onAudioStateChanged(isMuted = true))
        assertEquals(false, filter.onAudioStateChanged(isMuted = false))
    }

    @Test
    fun `should not pass on a mute state that has not changed`() {
        // A headset connecting or the call moving to the speaker sends a new audio state too,
        // carrying the same mute it carried before.
        val filter = legacyFilter()
        filter.onAudioStateChanged(isMuted = true)

        assertNull(filter.onAudioStateChanged(isMuted = true))
    }

    @Test
    fun `should pass on nothing where Telecom reports mute separately`() {
        // From API 34 onMuteStateChanged carries every mute change, and passing the audio
        // state's copy on as well would apply each change twice.
        val filter = CallAudioStateMuteFilter(reportsMuteSeparately = true)

        assertNull(filter.onAudioStateChanged(isMuted = false))
        assertNull(filter.onAudioStateChanged(isMuted = true))
    }
}
