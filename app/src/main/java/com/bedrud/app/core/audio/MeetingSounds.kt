package com.bedrud.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays the meeting notification tones over a call.
 *
 * They go out as [AudioAttributes.USAGE_VOICE_COMMUNICATION] so they follow the call itself: the
 * same earpiece, speaker or headset [com.bedrud.app.core.livekit.CallAudioSwitch] picked, at the
 * in-call volume the user already set for the voices. Routing them as media instead would put a
 * chime in the earpiece the moment someone switched the call to speaker — or worse, out loud while
 * the phone was held to an ear.
 *
 * Each tone is rendered once and kept; the buffers are tens of kilobytes and the alternative is
 * re-synthesizing on every join.
 */
class MeetingSounds {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val pcm: Map<MeetingSound, ShortArray> by lazy {
        MeetingSound.entries.associateWith { MeetingToneRenderer.render(it.tone) }
    }

    /** Fire-and-forget: overlapping calls simply overlap, as two people arriving at once should. */
    fun play(sound: MeetingSound) {
        scope.launch {
            val track = open(sound) ?: return@launch
            try {
                Log.d(TAG, "Playing ${sound.name}")
                track.play()
                delay(sound.tone.durationMillis + DrainMillis)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not play ${sound.name}", e)
            } finally {
                // Runs on cancellation too, so a call that ends mid-chime still frees the track.
                withContext(NonCancellable) { close(track) }
            }
        }
    }

    /**
     * Silences anything still sounding and frees its track. Call when the call ends.
     *
     * The rendered buffers stay — they are the cheap part, and the same manager is reused across
     * calls, so throwing them away would only mean synthesizing them again on the next join.
     */
    fun stopAll() {
        scope.coroutineContext.cancelChildren()
    }

    private fun open(sound: MeetingSound): AudioTrack? {
        val samples = pcm.getValue(sound)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MeetingToneRenderer.SampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                // The whole tone is written before playback starts, so there is no streaming
                // buffer to keep fed and no underrun to hear if the device is busy.
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .build()
                .also { it.write(samples, 0, samples.size) }
        } catch (e: Exception) {
            // A tone is never worth taking the call down for — every failure here is survivable.
            Log.w(TAG, "Could not open an audio track for ${sound.name}", e)
            null
        }
    }

    private fun close(track: AudioTrack) {
        try {
            track.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not stop an audio track", e)
        }
        track.release()
    }

    private companion object {
        const val TAG = "MeetingSounds"

        /**
         * Held past the tone's own length before the track is torn down.
         *
         * Playback starts a beat after [AudioTrack.play] returns, and releasing a track that is
         * still sounding truncates it — a clipped chime is the one artefact people notice.
         */
        const val DrainMillis = 150L
    }
}
