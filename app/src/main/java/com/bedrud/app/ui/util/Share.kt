package com.bedrud.app.ui.util

import android.content.Context
import android.content.Intent

/**
 * Hand some text to whatever the reader wants to send it with.
 *
 * The room link and a chat message both leave the app the same way, so the intent is built in one
 * place rather than once per caller — the MIME type in particular is the kind of literal that
 * drifts when it is written twice.
 *
 * [chooserTitle] is what the system sheet is titled; Android ignores it on versions that title the
 * sheet themselves, which is why it stays a plain argument rather than something callers reason
 * about.
 */
fun Context.sharePlainText(text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = PlainTextMimeType
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/** What `ACTION_SEND` is told it is carrying. */
const val PlainTextMimeType = "text/plain"
