package com.bedrud.app.models

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/** Accesses as the server sends them for each level an account can hold. */
private val SUPERADMIN_ACCESSES = listOf("user", "superadmin")
private val ADMIN_ACCESSES = listOf("user", "admin")
private val MODERATOR_ACCESSES = listOf("user", "moderator")

class UserTest {

    private val gson = Gson()

    @Test
    fun `init with all fields`() {
        val user = User(
            id = "u1",
            email = "a@b.com",
            name = "Alice",
            avatarUrl = "https://img.com/a.png",
            accesses = SUPERADMIN_ACCESSES,
            provider = "google"
        )
        assertEquals("u1", user.id)
        assertEquals("a@b.com", user.email)
        assertEquals("Alice", user.name)
        assertEquals("https://img.com/a.png", user.avatarUrl)
        assertEquals(SUPERADMIN_ACCESSES, user.accesses)
        assertEquals("google", user.provider)
    }

    @Test
    fun `default values`() {
        val user = User(id = "u1", email = "a@b.com", name = "Alice")
        assertNull(user.avatarUrl)
        assertNull(user.accesses)
        assertFalse(user.isAdmin)
        assertNull(user.provider)
    }

    @Test
    fun `isAdmin is true for a superadmin, the only level the admin API admits`() {
        assertTrue(User(id = "u1", email = "a@b.com", name = "Alice", accesses = SUPERADMIN_ACCESSES).isAdmin)
    }

    @Test
    fun `isAdmin is false for an admin below superadmin, whom the admin API turns away`() {
        // Every admin endpoint requires superadmin; a tab full of refusals is worse than none.
        assertFalse(User(id = "u1", email = "a@b.com", name = "Alice", accesses = ADMIN_ACCESSES).isAdmin)
        assertFalse(User(id = "u1", email = "a@b.com", name = "Alice", accesses = MODERATOR_ACCESSES).isAdmin)
    }

    @Test
    fun `isAdmin reads the accesses the server sends, not an isAdmin field it never sends`() {
        val superadmin = gson.fromJson(
            """{"id":"u1","email":"a@b.com","name":"Alice","accesses":["user","superadmin"]}""",
            User::class.java
        )
        // A record stored before the app read accesses carries only its own isAdmin, always false.
        val storedBefore = gson.fromJson(
            """{"id":"u1","email":"a@b.com","name":"Alice","isAdmin":false}""",
            User::class.java
        )

        assertTrue(superadmin.isAdmin)
        assertFalse(storedBefore.isAdmin)
    }

    @Test
    fun `Gson serialization round-trip with SerializedName annotations`() {
        val user = User(
            id = "u1",
            email = "a@b.com",
            name = "Alice",
            avatarUrl = "https://img.com/a.png",
            accesses = SUPERADMIN_ACCESSES,
            provider = "google"
        )
        val json = gson.toJson(user)
        val deserialized = gson.fromJson(json, User::class.java)
        assertEquals(user.id, deserialized.id)
        assertEquals(user.email, deserialized.email)
        assertEquals(user.name, deserialized.name)
        assertEquals(user.avatarUrl, deserialized.avatarUrl)
        assertEquals(user.accesses, deserialized.accesses)
        assertEquals(user.isAdmin, deserialized.isAdmin)
        assertEquals(user.provider, deserialized.provider)
    }

    @Test
    fun `AuthTokens Gson round-trip`() {
        val tokens = AuthTokens(accessToken = "acc123", refreshToken = "ref456")
        val json = gson.toJson(tokens)
        val deserialized = gson.fromJson(json, AuthTokens::class.java)
        assertEquals("acc123", deserialized.accessToken)
        assertEquals("ref456", deserialized.refreshToken)
    }
}
