package com.bedrud.app.core.auth

/** The server's name for an email-and-password account. */
private const val LOCAL_PROVIDER = "local"

/** The server's name for an account that signs in with a passkey. */
private const val PASSKEY_PROVIDER = "passkey"

/**
 * How an account signs in, read from the `provider` the server stores on the user.
 *
 * The server's own words are not for display: an email-and-password account is "local". The two
 * methods the app itself offers are named in the app's language; an identity provider keeps its
 * own name, which is a brand and reads the same in every language.
 */
sealed interface SignInMethod {
    /** Whether the account has a password the app can change. */
    val hasPassword: Boolean

    data object Email : SignInMethod {
        override val hasPassword = true
    }

    data object Passkey : SignInMethod {
        override val hasPassword = true
    }

    data class Provider(val name: String) : SignInMethod {
        override val hasPassword = false
    }
}

/**
 * Reads the server's `provider`: none, or "local", is email and password; anything else but
 * "passkey" is an identity provider, shown by its own name.
 */
fun signInMethodOf(provider: String?): SignInMethod = when (provider) {
    null, LOCAL_PROVIDER -> SignInMethod.Email
    PASSKEY_PROVIDER -> SignInMethod.Passkey
    else -> SignInMethod.Provider(provider.replaceFirstChar { it.uppercase() })
}
