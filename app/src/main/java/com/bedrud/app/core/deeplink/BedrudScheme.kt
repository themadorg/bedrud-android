package com.bedrud.app.core.deeplink

/**
 * The app's custom URI scheme, used for OAuth callbacks (`bedrud://oauth`) and self-managed
 * telecom call handles (`bedrud://room/...`). Kept in one place so every producer/consumer agrees.
 *
 * Note: the `AndroidManifest.xml` intent filters hardcode this same value in XML and cannot
 * reference this constant — keep them in sync by hand.
 */
object BedrudScheme {
    const val SCHEME = "bedrud"
}
