package com.bedrud.app.ui.components

import com.bedrud.app.models.RoomSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSettingsFormTest {

    @Test
    fun `withFormEdits keeps chat off when the switch is off`() {
        val saved = RoomSettings(allowChat = true).withFormEdits(allowChat = false)

        assertFalse(saved.allowChat)
    }

    @Test
    fun `withFormEdits keeps chat on when the switch is on`() {
        val saved = RoomSettings(allowChat = false).withFormEdits(allowChat = true)

        assertTrue(saved.allowChat)
    }

    @Test
    fun `withFormEdits keeps every setting the form does not edit as the room has it`() {
        val room = RoomSettings(
            allowVideo = false,
            allowAudio = false,
            requireApproval = true,
            e2ee = true,
            isPersistent = true,
            recordingsAllowed = true,
        )

        val saved = room.withFormEdits(allowChat = room.allowChat)

        assertEquals(room, saved)
    }
}
