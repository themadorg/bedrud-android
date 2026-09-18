package com.bedrud.app.core.auth

import android.content.SharedPreferences
import com.bedrud.app.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePrefsMigrationTest {

    private val old = InMemorySharedPreferences()
    private val new = InMemorySharedPreferences()

    private fun signedIn(prefs: SharedPreferences) {
        prefs.edit()
            .putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1")
            .putString(AuthPrefsKeys.REFRESH_TOKEN, "refresh-1")
            .putString(AuthPrefsKeys.USER, """{"id":"u1"}""")
            .apply()
    }

    @Test
    fun `should carry every stored credential across`() {
        signedIn(old)

        val migrated = SecurePrefsMigration.migrate(from = old, to = new)

        assertTrue(migrated)
        assertEquals("access-1", new.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
        assertEquals("refresh-1", new.getString(AuthPrefsKeys.REFRESH_TOKEN, null))
        assertEquals("""{"id":"u1"}""", new.getString(AuthPrefsKeys.USER, null))
    }

    @Test
    fun `should report success when the old store holds nothing`() {
        // A server the user added but never signed in to. There is nothing to lose, so the old
        // file is as done with as one that was copied.
        assertTrue(SecurePrefsMigration.migrate(from = old, to = new))
        assertNull(new.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
    }

    @Test
    fun `should write nothing for a credential the old store never held`() {
        old.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1").apply()

        SecurePrefsMigration.migrate(from = old, to = new)

        assertFalse(new.contains(AuthPrefsKeys.REFRESH_TOKEN))
        assertFalse(new.contains(AuthPrefsKeys.USER))
    }

    @Test
    fun `should leave a credential the new store already holds`() {
        // The migration was interrupted after writing, and the user signed in again before it ran
        // a second time. The session they have now is the one that counts.
        signedIn(old)
        new.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-2").apply()

        SecurePrefsMigration.migrate(from = old, to = new)

        assertEquals("access-2", new.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
    }

    @Test
    fun `should report failure when a credential does not read back`() {
        // The new store's key is unusable, so every write is lost. Reporting failure is what keeps
        // the old file from being deleted while it is still the only copy.
        signedIn(old)

        val migrated = SecurePrefsMigration.migrate(from = old, to = DiscardingPreferences())

        assertFalse(migrated)
    }

    /** Accepts every write and keeps none, the way a store with an unusable key behaves. */
    private class DiscardingPreferences : SharedPreferences by InMemorySharedPreferences() {
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = InMemorySharedPreferences().edit()
    }
}
