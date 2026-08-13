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

    /**
     * Latest capture level, 0..1, for the in-call mic meter. Written on the audio thread and
     * sampled by the UI per animation frame.
     */
    @Volatile
    var level: Float = 0f
        private set

    /** False only while the manual gate is actively holding audio back. */
    @Volatile
    var gateOpen: Boolean = true
        private set

    private var hangoverFramesLeft = 0

    // Always on: the meter needs every frame. Gating stays conditional on [gateEnabled], so
    // measuring here never changes what is transmitted.
    override fun isEnabled(): Boolean = true

    override fun getName(): String = NAME

    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) {
        hangoverFramesLeft = 0
        level = 0f
        gateOpen = true
    }

    override fun resetAudioProcessing(newRate: Int) {
        hangoverFramesLeft = 0
        level = 0f
        gateOpen = true
    }

    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        val levelDb = rmsDbfs(buffer)
        level = normalizedLevel(levelDb)

        if (!gateEnabled) {
            gateOpen = true
            return
        }
        if (levelDb >= thresholdDb(sensitivity)) {
            hangoverFramesLeft = HangoverFrames
            gateOpen = true
            return
        }
        if (hangoverFramesLeft > 0) {
            hangoverFramesLeft--
            gateOpen = true
            return
        }
        gateOpen = false
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

        /** Quietest level the meter shows; anything at or below reads as zero. */
        const val MeterFloorDb = -60.0

        /** Maps a dBFS reading onto the meter's 0..1 range. */
        fun normalizedLevel(levelDb: Double): Float =
            ((levelDb - MeterFloorDb) / -MeterFloorDb).coerceIn(0.0, 1.0).toFloat()

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
