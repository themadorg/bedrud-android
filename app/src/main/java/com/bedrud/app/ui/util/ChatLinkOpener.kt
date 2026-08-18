package com.bedrud.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.bedrud.app.MainActivity
import com.bedrud.app.core.deeplink.BedrudURLParser
import java.net.URI

/**
 * Opens a link somebody sent in the chat.
 *
 * A link to a room on a server this person already uses goes back into this app rather than out to
 * a browser. The alternative is a round trip the reader watches happen — browser opens, redirects,
 * app comes back — to reach a screen that was one activity away the whole time.
 *
 * [knownHosts] is what keeps that from being a hijack: `BedrudURLParser` accepts any host with an
 * `/m/` path, so without it a link to someone else's site with that shape would be swallowed by
 * this app. Only a server the reader has actually added counts as ours.
 *
 * [canJoinAnotherRoom] is false while a call is already running, which today is whenever chat is on
 * screen at all. Telecom refuses to place a second call over an unholdable one — the deep link
 * arrives, the platform puts up "Cannot place a call as there is an unholdable call", and the tap
 * reads as broken. Leaving the current call to follow the link is a real feature and a destructive
 * one; until it exists, a room link opens the page like any other and nothing dead-ends.
 *
 * The in-app hop names [MainActivity] outright rather than letting the URL be matched against the
 * manifest's filters. The filters cover one hardcoded domain, while the reader may have added any
 * number of instances — and an explicit component reaches the same `handleDeepLink` either way, so
 * there is still one join path rather than two.
 *
 * Everything else opens in a Custom Tab, the way the OAuth flow does: the reader stays in the
 * meeting's task, and closing the tab returns them to the call rather than to whatever the browser
 * had open before.
 *
 * Returns false when nothing could open it — a device with no browser at all — so the caller can
 * say so rather than let the tap do nothing.
 */
fun Context.openChatLink(
    url: String,
    knownHosts: Set<String>,
    canJoinAnotherRoom: Boolean,
): Boolean {
    val withScheme = if (url.contains("://")) url else "https://$url"
    val uri = Uri.parse(withScheme)

    val room = BedrudURLParser.parse(url)
    if (room != null && canJoinAnotherRoom && hostOf(withScheme) in knownHosts) {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                },
            )
            return true
        } catch (_: ActivityNotFoundException) {
            // Fall through to the browser: a room this app cannot reach is still a page.
        }
    }

    return try {
        CustomTabsIntent.Builder()
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(this, uri)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** The host of a server URL, lowercased, so `HTTPS://Bedrud.xyz/` and `bedrud.xyz` compare equal. */
fun hostOf(url: String): String? = try {
    URI(if (url.contains("://")) url else "https://$url").host?.lowercase()
} catch (_: Exception) {
    null
}
