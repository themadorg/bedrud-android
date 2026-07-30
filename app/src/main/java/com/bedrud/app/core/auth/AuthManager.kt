package com.bedrud.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.bedrud.app.models.AuthTokens
import com.bedrud.app.models.User
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(private val prefs: SharedPreferences) {

    constructor(context: Context, instanceId: String) : this(secureInstancePrefs(context, instanceId))

    private val gson = Gson()

    private val _isLoggedIn = MutableStateFlow(getAccessToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(loadUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun getAccessToken(): String? {
        return prefs.getString(AuthPrefsKeys.ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(AuthPrefsKeys.REFRESH_TOKEN, null)
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(AuthPrefsKeys.ACCESS_TOKEN, accessToken)
            .putString(AuthPrefsKeys.REFRESH_TOKEN, refreshToken)
            .apply()
        _isLoggedIn.value = true
    }

    fun saveTokens(tokens: AuthTokens) {
        saveTokens(tokens.accessToken, tokens.refreshToken)
    }

    fun saveUser(user: User) {
        val json = gson.toJson(user)
        prefs.edit()
            .putString(AuthPrefsKeys.USER, json)
            .apply()
        _currentUser.value = user
    }

    private fun loadUser(): User? {
        val json = prefs.getString(AuthPrefsKeys.USER, null) ?: return null
        return try {
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun logout() {
        prefs.edit()
            .remove(AuthPrefsKeys.ACCESS_TOKEN)
            .remove(AuthPrefsKeys.REFRESH_TOKEN)
            .remove(AuthPrefsKeys.USER)
            .apply()
        _isLoggedIn.value = false
        _currentUser.value = null
    }

    fun isAuthenticated(): Boolean {
        return getAccessToken() != null
    }

}
