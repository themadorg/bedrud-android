package com.bedrud.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens a page somebody linked in the chat.
 *
 * Only pages come here. A link to a room on one of the reader's own servers is resolved first, by
 * `resolveChatLink`, and joined in this app instead, after the reader agrees to leave the call they
 * are in: telecom refuses to place a second call over an unholdable one, so a room hop that skipped
 * that step would dead-end on "Cannot place a call as there is an unholdable call".
 *
 * A page opens in a Custom Tab, the way the OAuth flow does: the reader stays in the meeting's task,
 * and closing the tab returns them to the call rather than to whatever the browser had open before.
 *
 * Returns false when nothing could open it — a device with no browser at all — so the caller can
 * say so rather than let the tap do nothing.
 */
fun Context.openChatPage(url: String): Boolean {
    val withScheme = if (url.contains("://")) url else "https://$url"

    return try {
        CustomTabsIntent.Builder()
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(this, withScheme.toUri())
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
