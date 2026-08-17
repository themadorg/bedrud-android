package com.bedrud.app.ui.screens.meeting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.meeting.chat.findChatLinks

/**
 * A message's words, with any link in them tappable.
 *
 * The link is styled by weight and an underline rather than by colour. A bubble is one of two
 * colours depending on whose message it is, and a single accent that reads on both without
 * fighting either does not exist in a rose-on-warm-neutral palette — where an underline reads as a
 * link on any background, which is the whole job.
 *
 * The literal text is what becomes the link: there is no anchor text to point somewhere other than
 * where it says, so a message from a stranger cannot dress one address up as another.
 */
@Composable
fun rememberChatMessageText(
    text: String,
    contentColor: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString = remember(text, contentColor) {
    // BidiFormatter isolates the message by adding marks around it, so an offset into the raw text
    // is not an offset into what is drawn. The marks only ever bracket the content, so finding
    // where the content starts is enough to move the ranges onto it; if that ever stops being true
    // the links are dropped rather than pinned to the wrong words.
    val wrapped = BidiUtils.wrap(text)
    val shift = wrapped.indexOf(text)

    buildAnnotatedString {
        append(wrapped)
        if (shift < 0) return@buildAnnotatedString
        findChatLinks(text).forEach { link ->
            addLink(
                url = LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = contentColor,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = { onLinkClick(link.url) },
                ),
                start = link.range.first + shift,
                end = link.range.last + 1 + shift,
            )
        }
    }
}
