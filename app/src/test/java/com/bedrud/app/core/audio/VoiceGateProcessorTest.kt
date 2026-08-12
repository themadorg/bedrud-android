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

    private fun bufferOf(amplitude: Double, samples: Int = 480): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val value = amplitude * sin(2 * PI * i / 48.0) * Short.MAX_VALUE
            buffer.putShort(value.toInt().toShort())
        }
        buffer.rewind()
        return buffer
    }

    private fun isSilent(buffer: ByteBuffer): Boolean {
        val shorts = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until shorts.remaining()) {
            if (shorts.get(i) != 0.toShort()) return false
        }
        return true
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
    fun `disabled gate leaves audio untouched`() {
        val gate = VoiceGateProcessor()
        gate.gateEnabled = false
        val quiet = bufferOf(amplitude = 0.001)
        gate.processAudio(1, 480, quiet)
        assertFalse(isSilent(quiet))
    }

    @Test
    fun `loud frames pass and quiet frames mute once the hangover drains`() {
        val gate = VoiceGateProcessor()
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
        val gate = VoiceGateProcessor()
        gate.gateEnabled = true
        gate.sensitivity = 1f // threshold -70 dBFS

        val quiet = bufferOf(amplitude = 0.0005) // ~-66 dBFS, above -70
        gate.processAudio(1, 480, quiet)
        assertFalse(isSilent(quiet))
    }
}
