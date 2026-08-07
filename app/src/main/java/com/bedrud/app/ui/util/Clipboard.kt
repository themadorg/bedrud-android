package com.bedrud.app.ui.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

/**
 * Copy plain text to the system clipboard.
 *
 * The Compose clipboard API is suspending (writing can block on the system clipboard
 * service), so every caller needs a coroutine and the same three-line `ClipData` →
 * `ClipEntry` → `setClipEntry` dance. This wraps it once.
 *
 * [label] is what Android may show as the clip's description in system UI. Pass a string
 * resource, never a literal — callers use `R.string.app_name`, which is already per-build-type
 * ("Bedrud" vs "Bedrud Dev") and needs no separate translation, rather than adding a
 * user-facing string in ten locales for a descriptor this app never displays itself.
 */
suspend fun Clipboard.setPlainText(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}
