package com.bedrud.app.core.auth

import android.content.SharedPreferences
import com.bedrud.app.core.prefs.editBlocking

/**
 * Carries an instance's stored credentials from one secure store into another.
 *
 * Written for the move off `EncryptedSharedPreferences`, where the old and new stores are two
 * different encryption schemes over the same three keys. It reads through whatever
 * [SharedPreferences] it is handed, so neither scheme is named here.
 *
 * It runs on a device that may be killed at any point, so it is written to be run again: a
 * credential the destination already holds is left alone — an interrupted run followed by a fresh
 * sign-in must not be overwritten by the older token — and the result says whether every copied
 * value read back. Only a caller that gets `true` may delete the source, because until then the
 * source is the one copy of a session that exists.
 */
object SecurePrefsMigration {

    /**
     * Copies the credentials in [from] that [to] does not already hold, and returns whether [to]
     * now holds every one of them.
     */
    fun migrate(from: SharedPreferences, to: SharedPreferences): Boolean {
        val carried = AuthPrefsKeys.ALL.mapNotNull { key ->
            if (to.contains(key)) return@mapNotNull null
            val value = from.getString(key, null) ?: return@mapNotNull null
            key to value
        }
        if (carried.isEmpty()) return true

        to.editBlocking {
            carried.forEach { (key, value) -> putString(key, value) }
        }

        return carried.all { (key, value) -> to.getString(key, null) == value }
    }
}
