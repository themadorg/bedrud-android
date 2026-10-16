package com.bedrud.app.core.prefs

import com.bedrud.app.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Key = "key"

private enum class Fruit { APPLE, PEAR }

private data class Entry(val name: String, val count: Int)

/**
 * Reading back what the app stored. A file written by another version, or damaged, must read as
 * nothing stored rather than throw on launch.
 */
class PrefsExtensionsTest {

    private val prefs = InMemorySharedPreferences()

    @Test
    fun `should read back a stored list`() {
        prefs.putJsonList(Key, listOf(Entry("a", 1), Entry("b", 2)))

        assertEquals(listOf(Entry("a", 1), Entry("b", 2)), prefs.getJsonList<Entry>(Key))
    }

    @Test
    fun `should read an absent list as empty`() {
        assertTrue(prefs.getJsonList<Entry>(Key).isEmpty())
    }

    @Test
    fun `should read a damaged list as empty`() {
        prefs.edit().putString(Key, "[{\"name\": ").apply()

        assertTrue(prefs.getJsonList<Entry>(Key).isEmpty())
    }

    @Test
    fun `should read a stored null as empty`() {
        prefs.edit().putString(Key, "null").apply()

        assertTrue(prefs.getJsonList<Entry>(Key).isEmpty())
    }

    @Test
    fun `should read back a stored enum`() {
        prefs.putEnum(Key, Fruit.PEAR)

        assertEquals(Fruit.PEAR, prefs.getEnum(Key, Fruit.APPLE))
    }

    @Test
    fun `should read an absent enum as the default`() {
        assertEquals(Fruit.APPLE, prefs.getEnum(Key, Fruit.APPLE))
    }

    /** A value a later version stored, or one since renamed, is not a reason to fail. */
    @Test
    fun `should read an unknown enum name as the default`() {
        prefs.edit().putString(Key, "BANANA").apply()

        assertEquals(Fruit.APPLE, prefs.getEnum(Key, Fruit.APPLE))
    }

    @Test
    fun `should apply blocking edits and report them written`() {
        val written = prefs.editBlocking { putString(Key, "value") }

        assertTrue(written)
        assertEquals("value", prefs.getString(Key, null))
    }
}
