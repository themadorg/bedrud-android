package com.bedrud.app.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.view.View
import androidx.annotation.StringRes
import com.bedrud.app.R
import com.bedrud.app.core.prefs.getEnum
import com.bedrud.app.core.prefs.putEnum
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppAppearance(val label: String, @param:StringRes val stringResId: Int) {
    SYSTEM("System", R.string.settings_theme_system),
    LIGHT("Light", R.string.settings_theme_light),
    DARK("Dark", R.string.settings_theme_dark);
}

enum class AppLanguage(val localeTag: String, val label: String, val isRtl: Boolean = false) {
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    PERSIAN("fa", "فارسی", isRtl = true),
    ARABIC("ar", "العربية", isRtl = true),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    JAPANESE("ja", "日本語"),
    RUSSIAN("ru", "Русский"),
    SPANISH("es", "Español"),
    TURKISH("tr", "Türkçe"),
    CHINESE("zh", "中文");

    fun resolveIsRtl(): Boolean {
        if (this != SYSTEM) return isRtl
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
    }

    fun usesShabnam(): Boolean {
        if (this == PERSIAN) return true
        if (this == SYSTEM) return Locale.getDefault().language == "fa"
        return false
    }
}

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(loadAppearance())
    val appearance: StateFlow<AppAppearance> = _appearance.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS, true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setAppearance(value: AppAppearance) {
        prefs.putEnum(KEY_APPEARANCE, value)
        _appearance.value = value
    }

    fun setNotificationsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
        _notificationsEnabled.value = value
    }

    fun setLanguage(value: AppLanguage) {
        prefs.putEnum(KEY_LANGUAGE, value)
        _language.value = value
    }

    fun getLanguageTag(): String =
        prefs.getEnum(KEY_LANGUAGE, AppLanguage.SYSTEM).localeTag

    fun getLastTab(): Int = prefs.getInt(KEY_LAST_TAB, 0)

    fun setLastTab(index: Int) {
        prefs.edit().putInt(KEY_LAST_TAB, index).apply()
    }

    fun getMicEnabled(): Boolean = prefs.getBoolean(KEY_MIC_ENABLED, true)

    fun setMicEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_MIC_ENABLED, value).apply()
    }

    fun getDeafened(): Boolean = prefs.getBoolean(KEY_DEAFENED, false)

    fun setDeafened(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEAFENED, value).apply()
    }

    private fun loadAppearance(): AppAppearance =
        prefs.getEnum(KEY_APPEARANCE, AppAppearance.SYSTEM)

    private fun loadLanguage(): AppLanguage =
        prefs.getEnum(KEY_LANGUAGE, AppLanguage.SYSTEM)

    companion object {
        private const val PREFS_NAME = "bedrud_settings"
        private const val KEY_APPEARANCE = "bedrud_appearance"
        private const val KEY_NOTIFICATIONS = "bedrud_notifications_enabled"
        private const val KEY_LANGUAGE = "bedrud_language"
        private const val KEY_LAST_TAB = "bedrud_last_tab"
        private const val KEY_MIC_ENABLED = "bedrud_mic_enabled"
        private const val KEY_DEAFENED = "bedrud_deafened"
    }
}
