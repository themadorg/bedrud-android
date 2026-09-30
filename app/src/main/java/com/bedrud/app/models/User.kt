package com.bedrud.app.models

import com.google.gson.annotations.SerializedName

/** The access level of the server's operators, and the only one its admin API admits. */
private const val SUPERADMIN_ACCESS = "superadmin"

data class User(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,
    /**
     * The server's access levels for the account: "user", "moderator", "admin", "superadmin" or
     * "guest". Null in a record stored before the app read them.
     */
    @SerializedName("accesses")
    val accesses: List<String>? = null,
    val provider: String? = null,
    /**
     * When the password was last set, as the server writes the time; null if it never was. Only
     * its presence is read: the server stamps it on every password it sets, which tells a passkey
     * account that has added a password from one that has not. An email account's is null until its
     * first change, since signing up does not stamp it.
     */
    @SerializedName("passwordChangedAt")
    val passwordChangedAt: String? = null
) {
    /**
     * Whether the account can use the server's admin API, and so gets the Admin tab and is shown as
     * an admin. The server sends no such flag; it admits only `superadmin` to every admin endpoint,
     * so a plain "admin" would find nothing there but refusals.
     */
    val isAdmin: Boolean
        get() = accesses?.contains(SUPERADMIN_ACCESS) == true
}

data class AuthTokens(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String
)
