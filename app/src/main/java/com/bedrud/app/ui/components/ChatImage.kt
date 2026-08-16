package com.bedrud.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.bedrud.app.core.chat.ChatImageDecoder
import com.bedrud.app.core.chat.ChatImageUtils

/**
 * A picture that arrived in a chat message, from either of the two forms one can take.
 *
 * The server answers a chat upload with the image inlined as a `data:` URI, and older clients sent
 * their attachments the same way — but the image loader has no fetcher for that scheme, so those
 * have to be decoded here. Everything else is an ordinary URL, fetched with the caller's token.
 *
 * Both forms go through this one composable so a caller cannot accidentally support only the one it
 * happened to be tested with. The lightbox used to hand `data:` URIs straight to the loader and drew
 * nothing at all for them.
 */
@Composable
fun ChatImage(
    url: String,
    serverURL: String,
    accessToken: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (ChatImageDecoder.isInlineImage(url)) {
        val bitmap = remember(url) { ChatImageDecoder.decodeInlineImage(url) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
        }
        return
    }

    val context = LocalContext.current
    AsyncImage(
        model = ChatImageUtils.imageRequest(context, serverURL, url, accessToken),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
