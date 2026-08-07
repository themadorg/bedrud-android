package com.bedrud.app.core.instance

import android.content.Context
import android.content.SharedPreferences
import com.bedrud.app.core.auth.AuthPrefsKeys
import com.bedrud.app.core.auth.secureInstancePrefs
import com.bedrud.app.core.auth.securePrefs
import com.bedrud.app.models.Instance

object MigrationHelper {

    private const val PREFS_FILE = "bedrud_migration"
    private const val MIGRATION_DONE_KEY = "bedrud_migration_v1_done"
    private const val OLD_PREFS_FILE = "bedrud_secure_prefs"

    // Seed instance created when upgrading from the single-server app that stored one token globally.
    private const val DEFAULT_SERVER_URL = "https://bedrud.com"
    private const val DEFAULT_DISPLAY_NAME = "Bedrud"

    fun migrateIfNeeded(context: Context, store: InstanceStore) {
        val mainPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (mainPrefs.getBoolean(MIGRATION_DONE_KEY, false)) return

        mainPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()

        // Try to read old prefs
        val oldPrefs: SharedPreferences = try {
            securePrefs(context, OLD_PREFS_FILE)
        } catch (e: Exception) {
            return
        }

        val accessToken = oldPrefs.getString(AuthPrefsKeys.ACCESS_TOKEN, null)
        if (accessToken.isNullOrBlank()) return

        // Create default instance
        val instance = Instance(
            serverURL = DEFAULT_SERVER_URL,
            displayName = DEFAULT_DISPLAY_NAME
        )
        store.addInstance(instance)
        store.setActive(instance.id)

        // Copy tokens to new per-instance prefs file
        val newPrefs: SharedPreferences = secureInstancePrefs(context, instance.id)

        newPrefs.edit()
            .putString(AuthPrefsKeys.ACCESS_TOKEN, accessToken)
            .putString(AuthPrefsKeys.REFRESH_TOKEN, oldPrefs.getString(AuthPrefsKeys.REFRESH_TOKEN, null))
            .putString(AuthPrefsKeys.USER, oldPrefs.getString(AuthPrefsKeys.USER, null))
            .apply()

        // Clear old prefs
        oldPrefs.edit().clear().apply()
    }
}
