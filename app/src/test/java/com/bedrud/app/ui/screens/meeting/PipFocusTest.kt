package com.bedrud.app.ui.screens.meeting

import org.junit.Assert.assertEquals
import org.junit.Test

/** Who the picture-in-picture window shows. */
class PipFocusTest {

    private val others = listOf(Ada, Grace)

    @Test
    fun `should show the stream being watched`() {
        val focus = pipFocusIdentity(
            watchedStreamIdentity = Grace,
            speakingLevels = mapOf(Ada to Loud),
            previousFocus = null,
            remoteIdentities = others,
            localIdentity = You,
        )

        assertEquals(Grace, focus)
    }

    @Test
    fun `should show whoever among the others is speaking loudest`() {
        val focus = pipFocusIdentity(
            watchedStreamIdentity = null,
            speakingLevels = mapOf(Ada to Quiet, Grace to Loud),
            previousFocus = Ada,
            remoteIdentities = others,
            localIdentity = You,
        )

        assertEquals(Grace, focus)
    }

    /** A silence between sentences must not send the window back to whoever joined first. */
    @Test
    fun `should stay on the last speaker through a silence`() {
        val focus = pipFocusIdentity(
            watchedStreamIdentity = null,
            speakingLevels = emptyMap(),
            previousFocus = Grace,
            remoteIdentities = others,
            localIdentity = You,
        )

        assertEquals(Grace, focus)
    }

    /** The window shows the call, not a mirror: your own voice does not take it over. */
    @Test
    fun `should not follow yourself while others are in the call`() {
        val focus = pipFocusIdentity(
            watchedStreamIdentity = null,
            speakingLevels = mapOf(You to Loud),
            previousFocus = Ada,
            remoteIdentities = others,
            localIdentity = You,
        )

        assertEquals(Ada, focus)
    }

    @Test
    fun `should move to the first other participant once the last speaker leaves`() {
        val focus = pipFocusIdentity(
            watchedStreamIdentity = null,
            speakingLevels = emptyMap(),
            previousFocus = Linus,
            remoteIdentities = others,
            localIdentity = You,
        )

        assertEquals(Ada, focus)
    }

    @Test
    fun `should show yourself when nobody else is in the call`() {
        val focus = pipFocusIdentity(
            watchedStreamIdentity = null,
            speakingLevels = emptyMap(),
            previousFocus = Ada,
            remoteIdentities = emptyList(),
            localIdentity = You,
        )

        assertEquals(You, focus)
    }

    private companion object {
        const val You = "you"
        const val Ada = "ada"
        const val Grace = "grace"
        const val Linus = "linus"
        const val Quiet = 0.2f
        const val Loud = 0.8f
    }
}
