package com.bedrud.app.core.auth

/** The server's name for an email-and-password account. */
private const val LOCAL_PROVIDER = "local"

/** The server's name for an account that signs in with a passkey. */
private const val PASSKEY_PROVIDER = "passkey"

/** The server's name for an account made by continuing as a guest. */
private const val GUEST_PROVIDER = "guest"

/**
 * How an account signs in, read from the `provider` the server stores on the user.
 *
 * The server's own words are not for display: an email-and-password account is "local". The
 * methods the app itself offers are named in the app's language; an identity provider keeps its
 * own name, which is a brand and reads the same in every language.
 */
sealed interface SignInMethod {
    /**
     * Whether the account can sign in with a password, and so whether Settings offers a form to
     * set or change one.
     */
    val canHavePassword: Boolean

    /** Whether the account has a password now, which the server asks for before replacing it. */
    val hasPassword: Boolean

    data object Email : SignInMethod {
        override val canHavePassword = true
        override val hasPassword = true
    }

    /** An account made with a passkey, which starts without a password and may set one later. */
    data class Passkey(override val hasPassword: Boolean) : SignInMethod {
        override val canHavePassword = true
    }

    data object Guest : SignInMethod {
        override val canHavePassword = false
        override val hasPassword = false
    }

    data class Provider(val name: String) : SignInMethod {
        override val canHavePassword = false
        override val hasPassword = false
    }
}

/**
 * Reads the server's `provider`: none, or "local", is email and password; anything else but
 * "passkey" or "guest" is an identity provider, shown by its own name.
 *
 * [passwordChangedAt] tells a passkey account that has set a password from one that has not: the
 * server stamps it on every password it sets. It says nothing about an email account, which always
 * has a password but is not stamped until the first time it changes one.
 */
fun signInMethodOf(provider: String?, passwordChangedAt: String?): SignInMethod = when (provider) {
    null, LOCAL_PROVIDER -> SignInMethod.Email
    PASSKEY_PROVIDER -> SignInMethod.Passkey(hasPassword = passwordChangedAt != null)
    GUEST_PROVIDER -> SignInMethod.Guest
    else -> SignInMethod.Provider(provider.replaceFirstChar { it.uppercase() })
}
