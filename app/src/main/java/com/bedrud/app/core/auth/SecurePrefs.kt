package com.bedrud.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bedrud.app.core.prefs.editBlocking
import java.io.File

private const val TAG = "SecurePrefs"

/** Keystore alias for the key that seals this app's credential files. */
private const val KEY_ALIAS = "bedrud_secure_prefs"

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

    /** Every key an instance's auth prefs file can hold, for the code that has to copy them all. */
    val ALL = listOf(ACCESS_TOKEN, REFRESH_TOKEN, USER)
}

/**
 * Opens the app's own encrypted key/value file, sealed by an Android Keystore key.
 *
 * This replaces `EncryptedSharedPreferences`, which androidx deprecated without shipping a
 * successor — the guidance being to encrypt with the Keystore directly, which is what
 * [KeystoreCipher] does. The shape stays a [SharedPreferences] so everything that reads
 * credentials is unchanged.
 */
fun securePrefs(context: Context, fileName: String): SharedPreferences =
    EncryptedPrefs(
        file = context.getSharedPreferences(fileName, Context.MODE_PRIVATE),
        cipher = KeystoreCipher(KEY_ALIAS),
    )

/**
 * Opens the file [EncryptedSharedPreferences] wrote, for reading what it left behind.
 *
 * Kept only so an install that predates the Keystore store can be carried across. It is not
 * written to any more, and once the migration has run everywhere it goes, along with the
 * androidx.security-crypto dependency.
 */
@Suppress("DEPRECATION")
fun legacySecurePrefs(context: Context, fileName: String): SharedPreferences =
    EncryptedSharedPreferences.create(
        context,
        fileName,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

/** The file name [EncryptedSharedPreferences] used for an instance's credentials. */
fun legacyInstancePrefsName(instanceId: String) = "bedrud_secure_$instanceId"

/**
 * The encrypted per-instance auth prefs file — the single file name convention [AuthManager]
 * reads from and migration writes into.
 *
 * Opening it carries across whatever the deprecated store still holds for that instance, so a
 * user who upgrades stays signed in. That runs at most once per instance: the old file is deleted
 * as soon as everything in it has been read back out of the new one, and a run that is killed
 * part-way simply happens again on the next open.
 */
fun secureInstancePrefs(context: Context, instanceId: String): SharedPreferences {
    val prefs = securePrefs(context, "bedrud_keystore_$instanceId")
    migrateLegacyInstancePrefs(context, instanceId, prefs)
    return prefs
}

/**
 * Moves an instance's credentials out of the deprecated store into [prefs], and removes the file
 * they came from once they are safely readable there.
 */
private fun migrateLegacyInstancePrefs(
    context: Context,
    instanceId: String,
    prefs: SharedPreferences,
) {
    val legacyName = legacyInstancePrefsName(instanceId)
    if (!sharedPrefsFileExists(context, legacyName)) return

    val legacy = try {
        legacySecurePrefs(context, legacyName)
    } catch (e: Exception) {
        // The old file cannot be opened at all, so there is nothing in it to save. Dropping it
        // stops every later launch from trying again.
        Log.w(TAG, "Legacy credentials for $instanceId could not be opened; discarding them", e)
        context.deleteSharedPreferences(legacyName)
        return
    }

    if (SecurePrefsMigration.migrate(from = legacy, to = prefs)) {
        legacy.editBlocking { clear() }
        context.deleteSharedPreferences(legacyName)
        Log.d(TAG, "Carried credentials for $instanceId into the Keystore store")
    } else {
        Log.w(TAG, "Credentials for $instanceId did not read back; keeping the old file")
    }
}

/** Whether a shared preferences file has been written yet, without creating it by asking. */
private fun sharedPrefsFileExists(context: Context, fileName: String): Boolean =
    File(File(context.applicationInfo.dataDir, "shared_prefs"), "$fileName.xml").exists()
