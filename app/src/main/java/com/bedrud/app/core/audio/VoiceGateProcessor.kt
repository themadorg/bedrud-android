package com.bedrud.app.core.audio

import io.livekit.android.audio.AudioProcessorInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * A capture post-processor that mutes outgoing audio below a loudness threshold — the "manual
 * sensitivity" of voice-activity mode. With auto sensitivity the gate stays bypassed and the
 * platform's own processing decides (that is today's behavior, so auto never regresses a call);
 * the gate only engages when the user picks a manual threshold.
 *
 * The threshold maps [sensitivity] 0..1 onto [ThresholdMinDb]..[ThresholdMaxDb] (dBFS): higher
 * sensitivity opens the gate for quieter speech. A hangover keeps the gate open briefly after
 * each loud frame so words aren't clipped mid-syllable.
 */
class VoiceGateProcessor : AudioProcessorInterface {

    @Volatile
    var gateEnabled: Boolean = false

    @Volatile
    var sensitivity: Float = DefaultSensitivity

    private var hangoverFramesLeft = 0

    override fun isEnabled(): Boolean = gateEnabled

    override fun getName(): String = NAME

    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) {
        hangoverFramesLeft = 0
    }

    override fun resetAudioProcessing(newRate: Int) {
        hangoverFramesLeft = 0
    }

    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!gateEnabled) return
        val levelDb = rmsDbfs(buffer)
        if (levelDb >= thresholdDb(sensitivity)) {
            hangoverFramesLeft = HangoverFrames
            return
        }
        if (hangoverFramesLeft > 0) {
            hangoverFramesLeft--
            return
        }
        silence(buffer)
    }

    companion object {
        private const val NAME = "bedrud-voice-gate"

        const val DefaultSensitivity = 0.5f

        /** Gate threshold at sensitivity 0 — only loud speech passes. */
        const val ThresholdMinDb = -30.0

        /** Gate threshold at sensitivity 1 — nearly everything passes. */
        const val ThresholdMaxDb = -70.0

        /** 10ms frames the gate stays open after the last frame above threshold (~300ms). */
        const val HangoverFrames = 30

        /** dBFS floor reported for an all-zero frame. */
        const val SilenceFloorDb = -120.0

        /** Threshold in dBFS for a 0..1 sensitivity — linear between min and max. */
        fun thresholdDb(sensitivity: Float): Double {
            val clamped = sensitivity.coerceIn(0f, 1f).toDouble()
            return ThresholdMinDb + (ThresholdMaxDb - ThresholdMinDb) * clamped
        }

        /** RMS level of a little-endian 16-bit PCM buffer, in dBFS. */
        fun rmsDbfs(buffer: ByteBuffer): Double {
            val shorts = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val count = shorts.remaining()
            if (count == 0) return SilenceFloorDb
            var sumSquares = 0.0
            for (i in 0 until count) {
                val sample = shorts.get(i) / Short.MAX_VALUE.toDouble()
                sumSquares += sample * sample
            }
            val rms = sqrt(sumSquares / count)
            if (rms <= 0.0) return SilenceFloorDb
            return 20.0 * log10(rms)
        }

        /** Zeroes the frame in place so the gate transmits silence instead of noise. */
        fun silence(buffer: ByteBuffer) {
            val target = buffer.duplicate()
            while (target.hasRemaining()) {
                target.put(0)
            }
        }
    }
}
