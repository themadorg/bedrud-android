package com.bedrud.app.core.api

import com.bedrud.app.models.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoomApiTest : MockApiTest() {

    private lateinit var roomApi: RoomApi

    @Before
    fun setUp() {
        roomApi = api()
    }

    @Test
    fun `createRoom sends POST to room-create`() = runBlocking {
        val room = Room(id = "r1", name = "Room 1", createdBy = "u1")
        server.enqueue(MockResponse().setBody(gson.toJson(room)).setResponseCode(200))

        val response = roomApi.createRoom(CreateRoomRequest(name = "Room 1"))

        assertRequest("POST", "/room/create")
        assertTrue(response.isSuccessful)
        assertEquals("r1", response.body()!!.id)
    }

    @Test
    fun `listRooms sends GET to room-list and parses list`() = runBlocking {
        val rooms = listOf(
            UserRoomResponse(
                id = "r1", name = "Room 1", createdBy = "u1",
                isActive = true, maxParticipants = 50, expiresAt = "",
                settings = RoomSettings(), relationship = "owner", mode = "meeting"
            )
        )
        server.enqueue(MockResponse().setBody(gson.toJson(rooms)).setResponseCode(200))

        val response = roomApi.listRooms()

        assertRequest("GET", "/room/list")
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()!!.size)
        assertEquals("r1", response.body()!![0].id)
    }

    @Test
    fun `joinRoom sends POST to room-join and parses JoinRoomResponse`() = runBlocking {
        val joinResp = JoinRoomResponse(
            id = "r1", name = "my-room", token = "tok123",
            livekitHost = "wss://lk.example.com", createdBy = "u1",
            adminId = "u1", isActive = true, isPublic = true,
            maxParticipants = 50, expiresAt = "",
            settings = RoomSettings(), mode = "meeting"
        )
        server.enqueue(MockResponse().setBody(gson.toJson(joinResp)).setResponseCode(200))

        val response = roomApi.joinRoom(JoinRoomRequest(roomName = "my-room"))

        assertRequest("POST", "/room/join")
        assertTrue(response.isSuccessful)
        assertEquals("tok123", response.body()!!.token)
        assertEquals("wss://lk.example.com", response.body()!!.livekitHost)
    }

    @Test
    fun `kickParticipant sends POST to correct path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = roomApi.kickParticipant("room123", "user456")

        assertRequest("POST", "/room/room123/kick/user456")
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `updateRoomSettings sends PUT to room settings path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val updateRequest = UpdateRoomSettingsRequest(
            isPublic = true,
            settings = RoomSettings(allowChat = false, e2ee = true)
        )
        val response = roomApi.updateRoomSettings("room123", updateRequest)

        assertRequest(
            "PUT",
            "/room/room123/settings",
            listOf("\"isPublic\":true", "\"allowChat\":false", "\"e2ee\":true"),
        )
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `banParticipant sends POST to correct path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = roomApi.banParticipant("room123", "user456")

        assertRequest("POST", "/room/room123/ban/user456")
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `deleteRoom sends DELETE to correct path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = roomApi.deleteRoom("room999")

        assertRequest("DELETE", "/room/room999")
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `banParticipant returns error on 404 when room not found`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val response = roomApi.banParticipant("nonexistent", "user1")

        assertFalse(response.isSuccessful)
        assertEquals(404, response.code())
    }

    @Test
    fun `deleteRoom returns error on 403 when not authorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        val response = roomApi.deleteRoom("room123")

        assertFalse(response.isSuccessful)
        assertEquals(403, response.code())
    }
}
