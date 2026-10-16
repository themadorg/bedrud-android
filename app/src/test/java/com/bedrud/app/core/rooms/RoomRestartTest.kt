package com.bedrud.app.core.rooms

import com.bedrud.app.models.JoinRoomResponse
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.models.UserRoomResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRestartTest {

    @Test
    fun `should read a join answered with an ended room of your own as one to start again`() {
        assertTrue(isEndedOwnRoom(endedAnswer()))
    }

    @Test
    fun `should not read a join answered with a call to connect to as an ended room`() {
        val joinable = Gson().fromJson(
            """{"id":"r1","name":"$RoomName","token":"tok","livekitHost":"wss://lk.example.com","mode":"meeting"}""",
            JoinRoomResponse::class.java,
        )

        assertFalse(isEndedOwnRoom(joinable))
    }

    @Test
    fun `should start the room again under its own name, mode and settings`() {
        val request = restartRequest(endedAnswer(), ownRooms = emptyList())

        assertEquals(RoomName, request.name)
        assertEquals(Mode, request.mode)
        assertEquals(RoomSettings(allowChat = false, e2ee = true), request.settings)
    }

    @Test
    fun `should keep a public room public`() {
        val request = restartRequest(endedAnswer(), listOf(ownRoom(isPublic = true)))

        assertEquals(true, request.isPublic)
    }

    /** Sent rather than left out, so the room never rests on whatever the server defaults to. */
    @Test
    fun `should keep a private room private`() {
        val request = restartRequest(endedAnswer(), listOf(ownRoom(isPublic = false)))

        assertEquals(false, request.isPublic)
    }

    @Test
    fun `should keep the room's participant limit`() {
        val request = restartRequest(endedAnswer(), listOf(ownRoom(maxParticipants = 12)))

        assertEquals(12, request.maxParticipants)
    }

    @Test
    fun `should leave the public setting and limit to the server for a room missing from your list`() {
        val request = restartRequest(endedAnswer(), listOf(ownRoom(name = "another-room", isPublic = true)))

        assertNull(request.isPublic)
        assertNull(request.maxParticipants)
    }

    /** The server keeps a deleted room in the list under the same name as the one that replaced it. */
    @Test
    fun `should not take the public setting of a deleted room with the same name`() {
        val deleted = ownRoom(isPublic = true, maxParticipants = 50, deletedAt = "2026-10-01T10:00:00Z")
        val ended = ownRoom(isPublic = false, maxParticipants = 8)

        val request = restartRequest(endedAnswer(), listOf(deleted, ended))

        assertEquals(false, request.isPublic)
        assertEquals(8, request.maxParticipants)
    }

    private fun endedAnswer(): JoinRoomResponse = Gson().fromJson(
        """{"status":"archived_owned","name":"$RoomName","mode":"$Mode","settings":{"allowChat":false,"e2ee":true}}""",
        JoinRoomResponse::class.java,
    )

    private fun ownRoom(
        name: String = RoomName,
        isPublic: Boolean = true,
        maxParticipants: Int = 20,
        deletedAt: String? = null,
    ) = UserRoomResponse(
        id = "id-$name-$isPublic-$maxParticipants",
        name = name,
        createdBy = "u1",
        isActive = false,
        isPublic = isPublic,
        maxParticipants = maxParticipants,
        expiresAt = "",
        settings = RoomSettings(),
        mode = Mode,
        deletedAt = deletedAt,
    )

    private companion object {
        const val RoomName = "weekly-sync"
        const val Mode = "meeting"
    }
}
