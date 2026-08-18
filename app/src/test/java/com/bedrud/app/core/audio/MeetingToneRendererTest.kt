package com.bedrud.app.core.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingToneRendererTest {

    private val rate = MeetingToneRenderer.SampleRateHz

    private fun samplesFor(millis: Int) = millis * rate / 1000

    /** Counts zero crossings over a window — 2 per cycle, so: the pitch, without an FFT. */
    private fun hzBetween(pcm: ShortArray, fromMillis: Int, toMillis: Int): Float {
        val from = samplesFor(fromMillis)
        val to = samplesFor(toMillis)
        var crossings = 0
        for (i in from + 1 until to) {
            if ((pcm[i - 1] < 0) != (pcm[i] < 0)) crossings++
        }
        return crossings * rate / (2f * (to - from))
    }

    private fun peakBetween(pcm: ShortArray, fromMillis: Int, toMillis: Int): Int =
        (samplesFor(fromMillis) until samplesFor(toMillis)).maxOf { abs(pcm[it].toInt()) }

    @Test
    fun `a tone is exactly as long as its timeline says`() {
        assertEquals(250, MeetingSound.Join.tone.durationMillis)
        assertEquals(samplesFor(250), MeetingToneRenderer.render(MeetingSound.Join.tone).size)
    }

    @Test
    fun `the join chime rises from its first note to its second`() {
        val pcm = MeetingToneRenderer.render(MeetingSound.Join.tone)

        // Read before the notes overlap at 100ms, then after the first one has ended at 120ms.
        assertEquals(660f, hzBetween(pcm, 10, 90), PitchToleranceHz)
        assertEquals(880f, hzBetween(pcm, 130, 200), PitchToleranceHz)
    }

    @Test
    fun `the leave note glides down and holds there`() {
        val pcm = MeetingToneRenderer.render(MeetingSound.Leave.tone)

        assertEquals(660f, hzBetween(pcm, 5, 30), PitchToleranceHz)
        // The glide ends at 180ms; what follows is level until the note stops.
        assertEquals(440f, hzBetween(pcm, 160, 195), PitchToleranceHz)
    }

    @Test
    fun `every tone starts and ends at silence`() {
        MeetingSound.entries.forEach { sound ->
            val pcm = MeetingToneRenderer.render(sound.tone)

            // A hard step at either edge is the click the attack and the fade exist to remove.
            assertEquals(0, pcm.first().toInt())
            assertTrue("${sound.name} ends at ${pcm.last()}", abs(pcm.last().toInt()) <= SilentLsb)
        }
    }

    @Test
    fun `the leave note is already silent before its tail ends`() {
        val pcm = MeetingToneRenderer.render(MeetingSound.Leave.tone)

        // Its gain reaches zero at 200ms while the partial itself runs to 220ms.
        assertEquals(0, peakBetween(pcm, 205, 220))
    }

    @Test
    fun `no tone plays loud enough to talk over the room`() {
        val ceiling = (LoudestAmplitude * Short.MAX_VALUE).toInt()

        MeetingSound.entries.forEach { sound ->
            val pcm = MeetingToneRenderer.render(sound.tone)

            assertTrue("${sound.name} peaks at ${pcm.maxOf { abs(it.toInt()) }}", pcm.maxOf { abs(it.toInt()) } <= ceiling)
        }
    }

    private companion object {
        /** A window of a few dozen cycles resolves the pitch to well inside this. */
        const val PitchToleranceHz = 20f

        /** The loudest any tone may peak: the join notes' 0.09 each, plus their brief overlap. */
        const val LoudestAmplitude = 0.19f

        /** A fade that ends one sample short of zero lands here, which is below hearing. */
        const val SilentLsb = 1
    }
}
