package com.bedrud.app.models

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,
    @SerializedName("isAdmin")
    val isAdmin: Boolean = false,
    val provider: String? = null,
    /**
     * When the password was last set, as the server writes the time; null if it never was. Only
     * its presence is read: the server stamps it on every password it sets, which tells a passkey
     * account that has added a password from one that has not. An email account's is null until its
     * first change, since signing up does not stamp it.
     */
    @SerializedName("passwordChangedAt")
    val passwordChangedAt: String? = null
)

data class AuthTokens(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String
)
