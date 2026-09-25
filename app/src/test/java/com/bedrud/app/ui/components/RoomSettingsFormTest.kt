package com.bedrud.app.ui.components

import com.bedrud.app.models.RoomSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSettingsFormTest {

    @Test
    fun `withLockedToggles keeps chat off when the room has turned it off`() {
        val saved = RoomSettings(allowChat = false).withLockedToggles()

        assertFalse(saved.allowChat)
    }

    @Test
    fun `withLockedToggles keeps chat on when the room has it on`() {
        val saved = RoomSettings(allowChat = true).withLockedToggles()

        assertTrue(saved.allowChat)
    }

    @Test
    fun `withLockedToggles still submits the locked toggles as the form shows them`() {
        val saved = RoomSettings(
            requireApproval = true,
            e2ee = true,
            recordingsAllowed = true,
        ).withLockedToggles()

        assertFalse(saved.requireApproval)
        assertFalse(saved.e2ee)
        assertFalse(saved.recordingsAllowed)
    }
}
