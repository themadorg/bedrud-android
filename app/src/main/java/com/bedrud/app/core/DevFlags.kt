package com.bedrud.app.core

import com.bedrud.app.BuildConfig

/**
 * Central switch for dev-only UI affordances — "coming soon" hints for not-yet-wired features,
 * debug captions, and similar developer/QA aids.
 *
 * Backed by [BuildConfig.DEV_HINTS]: `true` on debug + `dev` (PR) builds, `false` on release
 * (beta/stable), so nothing here ever ships to end users. Gate UI with [com.bedrud.app.ui.components.DevOnly].
 */
object DevFlags {
    val hintsEnabled: Boolean = BuildConfig.DEV_HINTS
}
