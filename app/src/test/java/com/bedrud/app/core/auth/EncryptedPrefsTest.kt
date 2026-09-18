package com.bedrud.app.core.auth

import com.bedrud.app.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedPrefsTest {

    /** Stands in for the Keystore: reversible, and refuses anything it did not write. */
    private class FakeCipher(var readable: Boolean = true) : SecureCipher {
        override fun encrypt(plaintext: String): String = "sealed:$plaintext"

        override fun decrypt(stored: String): String? =
            if (readable) stored.removePrefix("sealed:") else null
    }

    private val file = InMemorySharedPreferences()
    private val cipher = FakeCipher()
    private val prefs = EncryptedPrefs(file, cipher)

    @Test
    fun `should read back the value it stored`() {
        prefs.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1").apply()

        assertEquals("access-1", prefs.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
    }

    @Test
    fun `should keep the plaintext out of the file it writes to`() {
        prefs.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1").apply()

        assertNotEquals("access-1", file.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
    }

    @Test
    fun `should report a value it cannot decrypt as absent`() {
        // What a restored backup or an invalidated Keystore key looks like from here: the file is
        // there and the value is not readable. Reading it as absent is what signs the user out
        // instead of crashing them out.
        prefs.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1").apply()
        cipher.readable = false

        assertNull(prefs.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
        assertFalse(prefs.contains(AuthPrefsKeys.ACCESS_TOKEN))
    }

    @Test
    fun `should forget a value it cannot decrypt`() {
        prefs.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1").apply()
        cipher.readable = false

        prefs.getString(AuthPrefsKeys.ACCESS_TOKEN, null)

        assertFalse(file.contains(AuthPrefsKeys.ACCESS_TOKEN))
    }

    @Test
    fun `should remove a value from the file it writes to`() {
        prefs.edit().putString(AuthPrefsKeys.ACCESS_TOKEN, "access-1").apply()

        prefs.edit().remove(AuthPrefsKeys.ACCESS_TOKEN).apply()

        assertFalse(file.contains(AuthPrefsKeys.ACCESS_TOKEN))
    }

    @Test
    fun `should refuse a type it does not encrypt`() {
        // Only strings are stored here. A silently unencrypted int would be a hole, so the
        // unsupported half of the interface says so instead of delegating.
        assertThrows(UnsupportedOperationException::class.java) {
            prefs.getInt("anything", 0)
        }
    }
}
