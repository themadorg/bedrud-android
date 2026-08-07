package com.bedrud.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keys inside an instance's encrypted auth prefs file. Shared between [AuthManager] (which reads
 * and writes them in normal operation) and the one-time migration (which seeds the same file from
 * the pre-multi-instance layout) — renaming a key on one side without the other silently breaks
 * migrated sign-ins, so both must reference these constants.
 */
object AuthPrefsKeys {
    const val ACCESS_TOKEN = "access_token"
    const val REFRESH_TOKEN = "refresh_token"
    const val USER = "user"
}

/**
 * Opens an [EncryptedSharedPreferences] file with the app's standard key/value schemes.
 *
 * androidx.security-crypto is deprecated, and Google shipped no successor — the guidance is to
 * encrypt with the Android Keystore yourself. That is not a swap at this call site, it is
 * hand-writing the crypto that currently protects every instance's access token, refresh token
 * and user record, plus a migration that reads the old files and rewrites them in the new
 * format. Get either half wrong and users are silently signed out at best.
 *
 * The library still works and still uses the Keystore-backed AES-GCM it always did, so the
 * deprecation is a maintenance signal, not a vulnerability. Keeping the whole decision behind
 * this one function is the point: when the replacement is written, this is the only body that
 * changes, and [AuthPrefsKeys] already pins the key names a migration would have to preserve.
 */
@Suppress("DEPRECATION")
fun securePrefs(context: Context, fileName: String): SharedPreferences =
    EncryptedSharedPreferences.create(
        context,
        fileName,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

/**
 * The encrypted per-instance auth prefs file — the single file name convention [AuthManager]
 * reads from and migration writes into.
 */
fun secureInstancePrefs(context: Context, instanceId: String): SharedPreferences =
    securePrefs(context, "bedrud_secure_$instanceId")
