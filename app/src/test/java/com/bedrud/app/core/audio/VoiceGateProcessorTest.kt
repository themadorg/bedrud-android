package com.bedrud.app.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGateProcessorTest {

    // WebRTC hands this stage 32-bit floats on a 16-bit scale (full scale is ±32768), so the
    // fixtures have to be built the same way or they test a format the processor never sees.
    private fun bufferOf(amplitude: Double, samples: Int = 480): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val value = amplitude * sin(2 * PI * i / 48.0) * VoiceGateProcessor.FullScaleSample
            buffer.putFloat(value.toFloat())
        }
        buffer.rewind()
        return buffer
    }

    // Gate mechanics assume the room is allowed to hear; the mute guard is exercised separately.
    private fun unmutedGate() = VoiceGateProcessor().apply { roomMayHear = { true } }

    private fun isSilent(buffer: ByteBuffer): Boolean {
        val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        for (i in 0 until samples.remaining()) {
            if (samples.get(i) != 0f) return false
        }
        return true
    }

    // ── The soft-mute guarantee ────────────────────────────────────────────────
    // Mute keeps the microphone running so talking into it can still be noticed, which makes
    // these the only thing stopping a muted button sitting over a live microphone.

    @Test
    fun `transmits nothing until something says the room may hear`() {
        val processor = VoiceGateProcessor()
        val buffer = bufferOf(0.8)

        // Default state, nothing wired up yet.
        processor.processAudio(1, 480, buffer)

        assertTrue(isSilent(buffer))
    }

    @Test
    fun `a muted room hears silence however loud the speech`() {
        val processor = VoiceGateProcessor()
        processor.roomMayHear = { false }
        val buffer = bufferOf(0.9)

        processor.processAudio(1, 480, buffer)

        assertTrue(isSilent(buffer))
    }

    @Test
    fun `the gate's own rules cannot re-open a muted microphone`() {
        val processor = VoiceGateProcessor()
        processor.roomMayHear = { false }
        // Gate disabled is the "pass everything through" configuration.
        processor.gateEnabled = false
        val buffer = bufferOf(0.9)

        processor.processAudio(1, 480, buffer)

        assertTrue(isSilent(buffer))
    }

    @Test
    fun `a mute check that throws is treated as muted`() {
        val processor = VoiceGateProcessor()
        processor.roomMayHear = { error("state unavailable") }
        val buffer = bufferOf(0.9)

        processor.processAudio(1, 480, buffer)

        assertTrue(isSilent(buffer))
    }

    @Test
    fun `the override silences even when the room would otherwise be allowed to hear`() {
        val processor = VoiceGateProcessor()
        processor.roomMayHear = { true }
        processor.forceSilence = true
        val buffer = bufferOf(0.9)

        processor.processAudio(1, 480, buffer)

        assertTrue(isSilent(buffer))
    }

    @Test
    fun `an unmuted room still hears the speech`() {
        val processor = VoiceGateProcessor()
        processor.roomMayHear = { true }
        val buffer = bufferOf(0.9)

        processor.processAudio(1, 480, buffer)

        assertFalse(isSilent(buffer))
    }

    @Test
    fun `the level is still measured while muted`() {
        val processor = VoiceGateProcessor()
        processor.roomMayHear = { false }

        processor.processAudio(1, 480, bufferOf(0.9))

        // The whole point of the soft mute: silence goes out, but the voice is still heard here.
        assertTrue(processor.level > 0.5f)
    }

    @Test
    fun `threshold maps sensitivity linearly between the bounds`() {
        assertEquals(VoiceGateProcessor.ThresholdMinDb, VoiceGateProcessor.thresholdDb(0f), 0.001)
        assertEquals(VoiceGateProcessor.ThresholdMaxDb, VoiceGateProcessor.thresholdDb(1f), 0.001)
        assertEquals(-50.0, VoiceGateProcessor.thresholdDb(0.5f), 0.001)
        // Out-of-range input clamps instead of extrapolating
        assertEquals(VoiceGateProcessor.ThresholdMinDb, VoiceGateProcessor.thresholdDb(-1f), 0.001)
        assertEquals(VoiceGateProcessor.ThresholdMaxDb, VoiceGateProcessor.thresholdDb(2f), 0.001)
    }

    @Test
    fun `rms of a full-scale sine is about minus three dbfs`() {
        val level = VoiceGateProcessor.rmsDbfs(bufferOf(amplitude = 1.0))
        assertTrue("expected ~-3dB, got $level", abs(level - (-3.0)) < 0.2)
    }

    @Test
    fun `rms of silence hits the floor`() {
        assertEquals(
            VoiceGateProcessor.SilenceFloorDb,
            VoiceGateProcessor.rmsDbfs(bufferOf(amplitude = 0.0)),
            0.001,
        )
    }

    @Test
    fun `meter level tracks loudness even when gating is off`() {
        val gate = unmutedGate()
        gate.gateEnabled = false

        gate.processAudio(1, 480, bufferOf(amplitude = 0.0))
        assertEquals(0f, gate.level, 0.001f)

        gate.processAudio(1, 480, bufferOf(amplitude = 1.0)) // ~-3 dBFS
        val loud = gate.level
        assertTrue("expected a near-full meter, got $loud", loud > 0.9f)

        gate.processAudio(1, 480, bufferOf(amplitude = 0.02)) // ~-37 dBFS
        val quiet = gate.level
        assertTrue("expected a mid meter, got $quiet", quiet in 0.1f..0.7f)
        // Gating stays off, so nothing is ever held back.
        assertTrue(gate.gateOpen)
    }

    @Test
    fun `gate open flag reports when the manual gate holds audio back`() {
        val gate = unmutedGate()
        gate.gateEnabled = true
        gate.sensitivity = 0.5f // threshold -50 dBFS

        gate.processAudio(1, 480, bufferOf(amplitude = 0.5))
        assertTrue(gate.gateOpen)

        repeat(VoiceGateProcessor.HangoverFrames + 1) {
            gate.processAudio(1, 480, bufferOf(amplitude = 0.0005))
        }
        assertFalse(gate.gateOpen)
    }

    @Test
    fun `meter normalization clamps at the floor and full scale`() {
        assertEquals(0f, VoiceGateProcessor.normalizedLevel(VoiceGateProcessor.MeterFloorDb), 0.001f)
        assertEquals(0f, VoiceGateProcessor.normalizedLevel(-120.0), 0.001f)
        assertEquals(1f, VoiceGateProcessor.normalizedLevel(0.0), 0.001f)
        assertEquals(0.5f, VoiceGateProcessor.normalizedLevel(VoiceGateProcessor.MeterFloorDb / 2), 0.001f)
    }

    @Test
    fun `disabled gate leaves audio untouched`() {
        val gate = unmutedGate()
        gate.gateEnabled = false
        val quiet = bufferOf(amplitude = 0.001)
        gate.processAudio(1, 480, quiet)
        assertFalse(isSilent(quiet))
    }

    @Test
    fun `loud frames pass and quiet frames mute once the hangover drains`() {
        val gate = unmutedGate()
        gate.gateEnabled = true
        gate.sensitivity = 0.5f // threshold -50 dBFS

        val loud = bufferOf(amplitude = 0.5) // ~-9 dBFS
        gate.processAudio(1, 480, loud)
        assertFalse(isSilent(loud))

        // ~-66 dBFS, below the -50 threshold: passes only while the hangover lasts
        repeat(VoiceGateProcessor.HangoverFrames) {
            val inHangover = bufferOf(amplitude = 0.0005)
            gate.processAudio(1, 480, inHangover)
            assertFalse("frame $it should still pass", isSilent(inHangover))
        }

        val afterHangover = bufferOf(amplitude = 0.0005)
        gate.processAudio(1, 480, afterHangover)
        assertTrue(isSilent(afterHangover))
    }

    @Test
    fun `quiet frames pass at maximum sensitivity`() {
        val gate = unmutedGate()
        gate.gateEnabled = true
        gate.sensitivity = 1f // threshold -70 dBFS

        val quiet = bufferOf(amplitude = 0.0005) // ~-66 dBFS, above -70
        gate.processAudio(1, 480, quiet)
        assertFalse(isSilent(quiet))
    }
}
