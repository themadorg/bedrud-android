package com.bedrud.app.ui.theme

/**
 * Opacity tokens. Material 3 expresses a disabled control as its normal colors at reduced
 * opacity rather than a separate grey palette, so anything the app dims itself — a whole
 * unavailable card, a logo behind a disabled sign-in method — uses [disabled] instead of
 * inventing its own float.
 */
object Alpha {
    /** Material 3's disabled-content opacity. */
    const val disabled = 0.38f
}
