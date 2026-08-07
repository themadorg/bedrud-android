package com.bedrud.app.core.api

import com.bedrud.app.models.*
import com.bedrud.app.models.CreateInviteTokenRequest
import com.bedrud.app.models.SetAccessesRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdminApiTest : MockApiTest() {

    private lateinit var adminApi: AdminApi

    @Before
    fun setUp() {
        adminApi = api()
    }

    // MARK: - Users

    @Test
    fun `listUsers sends GET to admin-users and parses list`() = runBlocking {
        val users = listOf(
            AdminUser(id = "u1", email = "a@b.com", name = "Alice", isActive = true,
                isAdmin = false, provider = "local", createdAt = "2025-01-01")
        )
        server.enqueue(MockResponse().setBody(gson.toJson(UserListResponse(users))).setResponseCode(200))

        val response = adminApi.listUsers()

        assertRequest("GET", "/admin/users")
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()!!.users.size)
        assertEquals("u1", response.body()!!.users[0].id)
        assertEquals("Alice", response.body()!!.users[0].name)
    }

    @Test
    fun `listUsers returns empty list on empty response`() = runBlocking {
        server.enqueue(MockResponse().setBody(gson.toJson(UserListResponse(emptyList()))).setResponseCode(200))
        val response = adminApi.listUsers()
        assertTrue(response.isSuccessful)
        assertEquals(0, response.body()!!.users.size)
    }

    @Test
    fun `setUserStatus sends PUT to admin-users-id-status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = adminApi.setUserStatus("user123", mapOf("active" to false))

        assertRequest("PUT", "/admin/users/user123/status", listOf("\"active\":false"))
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `setUserStatus activate sends active true`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        adminApi.setUserStatus("u1", mapOf("active" to true))

        assertRequest("PUT", "/admin/users/u1/status", listOf("\"active\":true"))
    }

    @Test
    fun `setUserAccesses sends PUT with accesses array`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = adminApi.setUserAccesses("user123", SetAccessesRequest(listOf("admin", "moderator")))

        assertRequest("PUT", "/admin/users/user123/accesses", listOf("admin", "moderator"))
        assertTrue(response.isSuccessful)
    }

    // MARK: - Rooms

    @Test
    fun `listRooms sends GET to admin-rooms and parses list`() = runBlocking {
        val rooms = listOf(
            AdminRoom(id = "r1", name = "Main Room", isActive = true,
                isPublic = true, maxParticipants = 50, createdAt = "2025-01-01")
        )
        server.enqueue(MockResponse().setBody(gson.toJson(RoomListResponse(rooms))).setResponseCode(200))

        val response = adminApi.listRooms()

        assertRequest("GET", "/admin/rooms")
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()!!.rooms.size)
        assertEquals("r1", response.body()!!.rooms[0].id)
        assertTrue(response.body()!!.rooms[0].isActive)
    }

    @Test
    fun `deleteRoom sends DELETE to admin-rooms-id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = adminApi.deleteRoom("room456")

        assertRequest("DELETE", "/admin/rooms/room456")
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `updateRoom sends PUT with maxParticipants`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = adminApi.updateRoom("room789", mapOf("maxParticipants" to 100))

        assertRequest("PUT", "/admin/rooms/room789", listOf("\"maxParticipants\":100"))
        assertTrue(response.isSuccessful)
    }

    // MARK: - Settings

    @Test
    fun `getSettings sends GET to admin-settings and parses response`() = runBlocking {
        val settings = AdminSettings(registrationEnabled = true, tokenRegistrationOnly = false)
        server.enqueue(MockResponse().setBody(gson.toJson(settings)).setResponseCode(200))

        val response = adminApi.getSettings()

        assertRequest("GET", "/admin/settings")
        assertTrue(response.isSuccessful)
        assertTrue(response.body()!!.registrationEnabled)
        assertFalse(response.body()!!.tokenRegistrationOnly)
    }

    @Test
    fun `updateSettings sends PUT with all fields`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val settings = AdminSettings(registrationEnabled = false, tokenRegistrationOnly = true)
        val response = adminApi.updateSettings(settings)

        assertRequest(
            "PUT",
            "/admin/settings",
            listOf("\"registrationEnabled\":false", "\"tokenRegistrationOnly\":true"),
        )
        assertTrue(response.isSuccessful)
    }

    // MARK: - Invite Tokens

    @Test
    fun `listInviteTokens sends GET to admin-invite-tokens`() = runBlocking {
        val tokens = listOf(
            InviteToken(id = "t1", token = "tok-abc123", email = "x@y.com",
                expiresAt = "2025-12-31", usedAt = null, used = false)
        )
        server.enqueue(MockResponse().setBody(gson.toJson(TokenListResponse(tokens))).setResponseCode(200))

        val response = adminApi.listInviteTokens()

        assertRequest("GET", "/admin/invite-tokens")
        assertTrue(response.isSuccessful)
        assertEquals("t1", response.body()!!.tokens[0].id)
        assertEquals("tok-abc123", response.body()!!.tokens[0].token)
    }

    @Test
    fun `createInviteToken sends POST with email and expiresInHours`() = runBlocking {
        val token = InviteToken(id = "t2", token = "new-token", email = "new@user.com",
            expiresAt = "2025-06-01", usedAt = null, used = false)
        server.enqueue(MockResponse().setBody(gson.toJson(token)).setResponseCode(200))

        val response = adminApi.createInviteToken(CreateInviteTokenRequest(email = "new@user.com", expiresInHours = 48))

        assertRequest("POST", "/admin/invite-tokens", listOf("new@user.com", "48"))
        assertTrue(response.isSuccessful)
        assertEquals("t2", response.body()!!.id)
    }

    @Test
    fun `createInviteToken without email sends null email`(): Unit = runBlocking {
        val token = InviteToken(id = "t3", token = "anon-token", email = null,
            expiresAt = null, usedAt = null, used = false)
        server.enqueue(MockResponse().setBody(gson.toJson(token)).setResponseCode(200))

        adminApi.createInviteToken(CreateInviteTokenRequest(email = null, expiresInHours = 24))

        assertRequest("POST", "/admin/invite-tokens", listOf("24"))
    }

    @Test
    fun `deleteInviteToken sends DELETE to admin-invite-tokens-id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = adminApi.deleteInviteToken("token-id-999")

        assertRequest("DELETE", "/admin/invite-tokens/token-id-999")
        assertTrue(response.isSuccessful)
    }

    // MARK: - Online Count

    @Test
    fun `getOnlineCount sends GET to admin-online-count and parses count`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"count\":42}").setResponseCode(200))

        val response = adminApi.getOnlineCount()

        assertRequest("GET", "/admin/online-count")
        assertTrue(response.isSuccessful)
        assertEquals(42, response.body()!!["count"])
    }

    @Test
    fun `getOnlineCount returns 0 when count is zero`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"count\":0}").setResponseCode(200))

        val response = adminApi.getOnlineCount()
        assertEquals(0, response.body()!!["count"])
    }

    // MARK: - Error Responses

    @Test
    fun `listUsers returns error on 403`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val response = adminApi.listUsers()
        assertFalse(response.isSuccessful)
        assertEquals(403, response.code())
    }

    @Test
    fun `deleteRoom returns error on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val response = adminApi.deleteRoom("nonexistent")
        assertFalse(response.isSuccessful)
        assertEquals(404, response.code())
    }

    @Test
    fun `updateSettings returns error on 500`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val response = adminApi.updateSettings(AdminSettings(registrationEnabled = true, tokenRegistrationOnly = false))
        assertFalse(response.isSuccessful)
        assertEquals(500, response.code())
    }
}
