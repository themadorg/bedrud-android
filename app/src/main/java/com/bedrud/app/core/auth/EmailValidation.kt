package com.bedrud.app.core.auth

import android.util.Patterns

/**
 * The one email shape check used everywhere an address is typed — sign-in, sign-up, and password
 * recovery — so no form is stricter or looser than the others. Trims before matching, like every
 * caller submits trimmed addresses.
 */
fun isValidEmail(raw: String): Boolean =
    Patterns.EMAIL_ADDRESS.matcher(raw.trim()).matches()
