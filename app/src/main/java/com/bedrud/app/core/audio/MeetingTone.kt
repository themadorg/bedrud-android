package com.bedrud.app.core.audio

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One sine oscillator inside a notification tone.
 *
 * The shape mirrors what the Web Audio API gives the web client for free — a sine whose pitch may
 * glide and whose gain holds flat before ramping to silence — so the same tone spec describes both
 * clients and neither has to own audio files.
 *
 * Offsets are all relative to the partial's own [startMillis], which is itself relative to the
 * start of the tone.
 */
data class TonePartial(
    val startMillis: Int,
    val durationMillis: Int,
    val fromHz: Float,
    val toHz: Float = fromHz,
    /** How long the pitch takes to reach [toHz]; it holds there for the rest of the partial. */
    val glideMillis: Int = durationMillis,
    val amplitude: Float,
    /** When the gain leaves its flat top. */
    val fadeOutStartMillis: Int = durationMillis - DefaultFadeOutMillis,
    /** When the gain reaches zero — earlier than [durationMillis] leaves a silent tail. */
    val fadeOutEndMillis: Int = durationMillis,
) {
    companion object {
        /**
         * Default fall time, matching the web client's `rampDown`.
         *
         * A partial shorter than this simply starts fading immediately, which is what the web's
         * scheduled gain ramp does too — the short message pops rely on it.
         */
        const val DefaultFadeOutMillis = 60
    }
}

/** A complete notification tone: one or more partials laid out on a shared timeline. */
data class MeetingTone(val partials: List<TonePartial>) {

    /** Total length, including any silent tail a partial's early fade leaves behind. */
    val durationMillis: Int = partials.maxOf { it.startMillis + it.durationMillis }
}

/**
 * The three sounds a meeting makes, transcribed from the web client's `meeting-sounds.ts` so both
 * clients answer the same event with the same pitch, length and loudness.
 *
 * Amplitudes are deliberately low: these play over a live call on the same route as everyone's
 * voice, and a notification that talks over the room is worse than no notification.
 */
enum class MeetingSound(val tone: MeetingTone) {

    /** Two notes rising — someone joined. */
    Join(
        MeetingTone(
            listOf(
                TonePartial(startMillis = 0, durationMillis = 120, fromHz = 660f, amplitude = 0.09f),
                TonePartial(startMillis = 100, durationMillis = 150, fromHz = 880f, amplitude = 0.09f),
            )
        )
    ),

    /** One note falling away — someone left. */
    Leave(
        MeetingTone(
            listOf(
                TonePartial(
                    startMillis = 0,
                    durationMillis = 220,
                    fromHz = 660f,
                    toHz = 440f,
                    glideMillis = 180,
                    amplitude = 0.09f,
                    fadeOutStartMillis = 120,
                    fadeOutEndMillis = 200,
                ),
            )
        )
    ),

    /** A soft two-note pop — a message arrived. */
    Message(
        MeetingTone(
            listOf(
                TonePartial(startMillis = 0, durationMillis = 70, fromHz = 1200f, amplitude = 0.07f),
                TonePartial(startMillis = 55, durationMillis = 60, fromHz = 1500f, amplitude = 0.05f),
            )
        )
    ),
}

/**
 * Renders a [MeetingTone] to signed 16-bit mono PCM.
 *
 * Pure arithmetic on purpose — it holds no Android type, so the tones can be checked in a unit test
 * instead of by ear on a device.
 */
object MeetingToneRenderer {

    /** Capture and playback both run at this rate for a call, so nothing has to resample. */
    const val SampleRateHz = 48_000

    /**
     * Rise time applied to every partial.
     *
     * The web client starts its oscillators at full gain and accepts the click that produces. PCM
     * is less forgiving — a hard step on the first sample is an audible tick in front of every
     * chime — and a few milliseconds of rise is inaudible as a change to the tone itself.
     */
    const val AttackMillis = 3

    fun render(tone: MeetingTone): ShortArray {
        val samples = ShortArray(millisToSamples(tone.durationMillis))
        tone.partials.forEach { partial -> mix(partial, samples) }
        return samples
    }

    /** Adds one partial into [samples]; partials that overlap sum, exactly as oscillators do. */
    private fun mix(partial: TonePartial, samples: ShortArray) {
        val start = millisToSamples(partial.startMillis)
        val length = millisToSamples(partial.durationMillis)
        val glide = millisToSamples(partial.glideMillis)
        val attack = millisToSamples(AttackMillis)
        val fadeFrom = millisToSamples(partial.fadeOutStartMillis)
        val fadeTo = millisToSamples(partial.fadeOutEndMillis)

        // The pitch glides, so the phase has to be integrated sample by sample. Evaluating
        // sin(2*pi*f*t) against the instantaneous frequency instead would jump the waveform every
        // time that frequency moved.
        var phase = 0.0
        for (i in 0 until length) {
            val index = start + i
            if (index >= samples.size) break
            val hz = frequencyAt(partial, i, glide)
            val level = partial.amplitude * attackGain(i, attack) * fadeGain(i, fadeFrom, fadeTo)
            val mixed = samples[index] + sin(phase) * level * Short.MAX_VALUE
            // Summed partials stay far below full scale by design, but a clamp is what keeps a
            // later amplitude change from wrapping the sample and turning a chime into a rasp.
            samples[index] = mixed.coerceIn(ShortFloor, ShortCeiling).roundToInt().toShort()
            phase += TwoPi * hz / SampleRateHz
        }
    }

    private fun frequencyAt(partial: TonePartial, sample: Int, glideSamples: Int): Double {
        if (partial.toHz == partial.fromHz || glideSamples <= 0) return partial.fromHz.toDouble()
        val progress = (sample.toDouble() / glideSamples).coerceAtMost(1.0)
        return partial.fromHz + (partial.toHz - partial.fromHz) * progress
    }

    private fun attackGain(sample: Int, attackSamples: Int): Double =
        if (sample >= attackSamples || attackSamples <= 0) 1.0 else sample.toDouble() / attackSamples

    private fun fadeGain(sample: Int, fadeFrom: Int, fadeTo: Int): Double = when {
        sample <= fadeFrom -> 1.0
        sample >= fadeTo -> 0.0
        else -> 1.0 - (sample - fadeFrom).toDouble() / (fadeTo - fadeFrom)
    }

    private fun millisToSamples(millis: Int): Int =
        (millis / MillisPerSecond * SampleRateHz).roundToInt()

    private const val MillisPerSecond = 1000.0
    private const val TwoPi = 2.0 * PI
    private val ShortFloor = Short.MIN_VALUE.toDouble()
    private val ShortCeiling = Short.MAX_VALUE.toDouble()
}
