package com.bedrud.app.core.auth

/**
 * Shared password rules, so every screen that sets or checks a password agrees on the policy.
 *
 * [MIN_LENGTH] is the minimum accepted length — enforced when creating an account (sign-up),
 * changing a password (settings), and signing in (email login). Keep this the single source of
 * truth: reference it, never inline the number in a screen.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 12

    /** The one length check every screen uses — sign-up, sign-in, and change-password. */
    fun meetsMinLength(password: String): Boolean = password.length >= MIN_LENGTH
}
