package com.bedrud.app.core.api

/** HTTP header names and value formats shared across the API and image-loading clients. */
object ApiHeaders {
    const val AUTHORIZATION = "Authorization"
    const val BEARER_PREFIX = "Bearer "

    /** Formats [token] as a `Bearer` authorization header value. */
    fun bearer(token: String): String = "$BEARER_PREFIX$token"
}
