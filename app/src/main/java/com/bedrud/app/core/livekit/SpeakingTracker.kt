package com.bedrud.app.core.livekit

/**
 * Turns the server's active-speaker bursts into a steady per-identity speaking level.
 *
 * The room reports its speakers roughly twice a second and simply omits whoever has gone quiet,
 * so feeding those bursts straight to the UI makes an indicator strobe through every pause
 * between words. Each identity is instead held at its last reported level for [holdMillis] after
 * its final mention, and only then drops out.
 *
 * The local participant appears in the server's list like anyone else, which is the whole point:
 * an indicator driven from here proves the room is *receiving* that audio, where a local capture
 * meter only proves the microphone works.
 */
class SpeakingTracker(private val holdMillis: Long = HoldMillis) {

    private val lastHeardAt = mutableMapOf<String, Long>()
    private val levels = mutableMapOf<String, Float>()

    /** Records a server update and returns the levels visible at [nowMillis]. */
    fun onSpeakers(speakers: Map<String, Float>, nowMillis: Long): Map<String, Float> {
        speakers.forEach { (identity, level) ->
            lastHeardAt[identity] = nowMillis
            levels[identity] = level
        }
        return prune(nowMillis)
    }

    /** Drops identities whose hold has expired; call on a timer so the last one still fades out. */
    fun prune(nowMillis: Long): Map<String, Float> {
        val expired = lastHeardAt.filterValues { nowMillis - it >= holdMillis }.keys.toList()
        expired.forEach {
            lastHeardAt.remove(it)
            levels.remove(it)
        }
        return levels.toMap()
    }

    fun clear() {
        lastHeardAt.clear()
        levels.clear()
    }

    companion object {
        /** Covers the gap between two server bursts plus a normal pause between words. */
        const val HoldMillis = 600L

        /** How often the hold is re-checked while anyone is speaking. */
        const val PruneIntervalMillis = 150L
    }
}
