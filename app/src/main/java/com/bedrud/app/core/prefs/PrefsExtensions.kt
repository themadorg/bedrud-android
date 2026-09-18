package com.bedrud.app.core.prefs

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@PublishedApi
internal val prefsGson = Gson()

/** Reads a JSON-encoded list from [key], returning an empty list when it's absent or malformed. */
inline fun <reified T> SharedPreferences.getJsonList(key: String): List<T> {
    val json = getString(key, null) ?: return emptyList()
    return try {
        prefsGson.fromJson<List<T>>(json, object : TypeToken<List<T>>() {}.type).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

/** Persists [value] as a JSON string under [key]. */
fun SharedPreferences.putJsonList(key: String, value: List<*>) {
    edit().putString(key, prefsGson.toJson(value)).apply()
}

/** Reads an enum constant by name from [key], returning [default] when absent or unrecognized. */
inline fun <reified T : Enum<T>> SharedPreferences.getEnum(key: String, default: T): T {
    val raw = getString(key, null) ?: return default
    return try {
        enumValueOf<T>(raw)
    } catch (_: Exception) {
        default
    }
}

/** Persists [value] by its enum constant name under [key]. */
fun <T : Enum<T>> SharedPreferences.putEnum(key: String, value: T) {
    edit().putString(key, value.name).apply()
}

/**
 * Applies [edits] and returns once they are on disk, rather than on a background thread.
 *
 * For the writes whose next step depends on them having landed — reading a value back to check it
 * arrived, or deleting the file it was copied from. Everything else should use `apply()`.
 */
@Suppress("ApplySharedPref", "UseKtx")
fun SharedPreferences.editBlocking(edits: SharedPreferences.Editor.() -> Unit): Boolean {
    val editor = edit()
    editor.edits()
    return editor.commit()
}
