package com.bedrud.app.core.auth

import android.content.SharedPreferences

/**
 * A [SharedPreferences] whose values are sealed by a [SecureCipher] before they reach the file
 * underneath.
 *
 * Only strings are stored, because only strings are kept here: an access token, a refresh token
 * and a serialised user record. The typed halves of the interface are refused rather than passed
 * through, so nothing can be written to this file unencrypted by reaching for the wrong putter.
 *
 * Key names are stored as they are. They are the three constants in [AuthPrefsKeys], compiled
 * into the APK and readable by anyone holding it, so encrypting them would hide nothing.
 *
 * A value that will not open is treated as absent and dropped from the file — see
 * [SecureCipher.decrypt] for when that happens. The user is signed out and can sign in again,
 * which is the outcome to aim for; a file that keeps handing back an unusable value would
 * otherwise be read on every launch.
 */
class EncryptedPrefs(
    private val file: SharedPreferences,
    private val cipher: SecureCipher,
) : SharedPreferences {

    private fun unsupported(type: String): Nothing =
        throw UnsupportedOperationException("EncryptedPrefs stores strings only, not $type")

    override fun getString(key: String?, defValue: String?): String? {
        val stored = file.getString(key, null) ?: return defValue
        val opened = cipher.decrypt(stored)
        if (opened == null) {
            file.edit().remove(key).apply()
            return defValue
        }
        return opened
    }

    override fun contains(key: String?): Boolean = getString(key, null) != null

    override fun getAll(): MutableMap<String, *> = unsupported("a whole-file read")

    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = unsupported("string sets")

    override fun getInt(key: String?, defValue: Int): Int = unsupported("ints")

    override fun getLong(key: String?, defValue: Long): Long = unsupported("longs")

    override fun getFloat(key: String?, defValue: Float): Float = unsupported("floats")

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = unsupported("booleans")

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = file.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = file.unregisterOnSharedPreferenceChangeListener(listener)

    override fun edit(): SharedPreferences.Editor = Editor(file.edit())

    private inner class Editor(private val editor: SharedPreferences.Editor) : SharedPreferences.Editor {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (value == null) editor.remove(key) else editor.putString(key, cipher.encrypt(value))
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?) = unsupported("string sets")

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = unsupported("ints")

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = unsupported("longs")

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = unsupported("floats")

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = unsupported("booleans")

        override fun remove(key: String?): SharedPreferences.Editor {
            editor.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            editor.clear()
            return this
        }

        override fun commit(): Boolean = editor.commit()

        override fun apply() = editor.apply()
    }
}
