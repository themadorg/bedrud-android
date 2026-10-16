package com.bedrud.app.ui.screens.settings

import android.content.Context
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.NoiseSuppressionMode
import com.bedrud.app.core.audio.VoiceGateProcessor
import com.bedrud.app.testutil.InMemorySharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The name `SettingsStore` stores under; any other file would read as a fresh install. */
private const val SettingsFile = "bedrud_settings"

/**
 * The settings that outlive the app: what a fresh install starts with, that each one reads back,
 * and that a value the app can no longer use falls back rather than failing.
 */
class SettingsStoreTest {

    private val prefs = InMemorySharedPreferences()

    private val context = mockk<Context> {
        every { getSharedPreferences(SettingsFile, Context.MODE_PRIVATE) } returns prefs
    }

    private fun store() = SettingsStore(context)

    @Test
    fun `should start a fresh install on the system's appearance and language`() {
        val store = store()

        assertEquals(AppAppearance.SYSTEM, store.appearance.value)
        assertEquals(AppLanguage.SYSTEM, store.language.value)
        assertEquals("", store.getLanguageTag())
    }

    @Test
    fun `should start a fresh install with notifications, an open mic and nothing deafened`() {
        val store = store()

        assertTrue(store.notificationsEnabled.value)
        assertTrue(store.getMicEnabled())
        assertFalse(store.getDeafened())
        assertEquals(0, store.getLastTab())
    }

    @Test
    fun `should start a fresh install on voice activity with automatic sensitivity`() {
        val store = store()

        assertEquals(MeetingInputMode.VOICE_ACTIVITY, store.getInputMode())
        assertTrue(store.getAutoSensitivity())
        assertEquals(VoiceGateProcessor.DefaultSensitivity, store.getVoiceSensitivity())
        assertEquals(NoiseSuppressionMode.DEVICE, store.getNoiseSuppression())
    }

    @Test
    fun `should publish a new appearance and keep it for the next launch`() {
        store().setAppearance(AppAppearance.DARK)

        assertEquals(AppAppearance.DARK, store().appearance.value)
    }

    @Test
    fun `should publish a new language and keep it for the next launch`() {
        val store = store()
        store.setLanguage(AppLanguage.PERSIAN)

        assertEquals(AppLanguage.PERSIAN, store.language.value)
        assertEquals("fa", store().getLanguageTag())
    }

    @Test
    fun `should publish notifications turned off and keep it for the next launch`() {
        val store = store()
        store.setNotificationsEnabled(false)

        assertFalse(store.notificationsEnabled.value)
        assertFalse(store().notificationsEnabled.value)
    }

    @Test
    fun `should keep the call's own settings for the next call`() {
        store().apply {
            setMicEnabled(false)
            setDeafened(true)
            setLastTab(2)
            setInputMode(MeetingInputMode.PUSH_TO_TALK)
            setAutoSensitivity(false)
            setVoiceSensitivity(0.25f)
            setNoiseSuppression(NoiseSuppressionMode.OFF)
        }

        val next = store()
        assertFalse(next.getMicEnabled())
        assertTrue(next.getDeafened())
        assertEquals(2, next.getLastTab())
        assertEquals(MeetingInputMode.PUSH_TO_TALK, next.getInputMode())
        assertFalse(next.getAutoSensitivity())
        assertEquals(0.25f, next.getVoiceSensitivity())
        assertEquals(NoiseSuppressionMode.OFF, next.getNoiseSuppression())
    }

    @Test
    fun `should keep a sensitivity at either end of its range`() {
        val store = store()

        store.setVoiceSensitivity(0f)
        assertEquals(0f, store.getVoiceSensitivity())
        store.setVoiceSensitivity(1f)
        assertEquals(1f, store.getVoiceSensitivity())
    }

    @Test
    fun `should hold a sensitivity past either end of its range at that end`() {
        val store = store()

        store.setVoiceSensitivity(-0.1f)
        assertEquals(0f, store.getVoiceSensitivity())
        store.setVoiceSensitivity(1.1f)
        assertEquals(1f, store.getVoiceSensitivity())
    }

    /**
     * A language or mode a later version stored, or one since removed, is not a reason to fail.
     * The key names are written out on purpose: renaming one would reset that setting for everyone.
     */
    @Test
    fun `should fall back from a stored value this version does not know`() {
        prefs.edit()
            .putString("bedrud_appearance", "SEPIA")
            .putString("bedrud_language", "KLINGON")
            .putString("bedrud_input_mode", "ALWAYS_ON")
            .apply()

        val store = store()
        assertEquals(AppAppearance.SYSTEM, store.appearance.value)
        assertEquals(AppLanguage.SYSTEM, store.language.value)
        assertEquals(MeetingInputMode.VOICE_ACTIVITY, store.getInputMode())
    }
}
