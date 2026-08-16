package com.bedrud.app.core.livekit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakingTrackerTest {

    private val hold = SpeakingTracker.HoldMillis

    @Test
    fun `reports the level the server sent`() {
        val tracker = SpeakingTracker()

        val levels = tracker.onSpeakers(mapOf("alice" to 0.7f), nowMillis = 0)

        assertEquals(0.7f, levels["alice"])
    }

    @Test
    fun `holds a speaker the server has stopped mentioning`() {
        val tracker = SpeakingTracker()
        tracker.onSpeakers(mapOf("alice" to 0.7f), nowMillis = 0)

        // The server announces a different speaker and simply omits alice — the usual way a
        // speaker goes quiet. She stays lit until the hold runs out.
        val duringHold = tracker.onSpeakers(mapOf("bob" to 0.2f), nowMillis = hold - 1)

        assertTrue(duringHold.containsKey("alice"))
        assertEquals(0.7f, duringHold["alice"])
    }

    @Test
    fun `drops a speaker once the hold expires`() {
        val tracker = SpeakingTracker()
        tracker.onSpeakers(mapOf("alice" to 0.7f), nowMillis = 0)

        assertEquals(emptyMap<String, Float>(), tracker.prune(nowMillis = hold))
    }

    @Test
    fun `a fresh mention restarts the hold`() {
        val tracker = SpeakingTracker()
        tracker.onSpeakers(mapOf("alice" to 0.7f), nowMillis = 0)
        tracker.onSpeakers(mapOf("alice" to 0.3f), nowMillis = hold - 1)

        val stillHeld = tracker.prune(nowMillis = hold)

        assertEquals(0.3f, stillHeld["alice"])
        assertEquals(emptyMap<String, Float>(), tracker.prune(nowMillis = 2 * hold))
    }

    @Test
    fun `clear forgets everyone`() {
        val tracker = SpeakingTracker()
        tracker.onSpeakers(mapOf("alice" to 0.7f), nowMillis = 0)

        tracker.clear()

        assertEquals(emptyMap<String, Float>(), tracker.prune(nowMillis = 0))
    }
}
