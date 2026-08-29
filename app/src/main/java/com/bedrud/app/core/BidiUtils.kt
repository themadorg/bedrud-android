package com.bedrud.app.core

import android.text.BidiFormatter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

object BidiUtils {

    private val formatter: BidiFormatter = BidiFormatter.getInstance()

    // Mirrors web/excalidraw RTL detection: first strong char in RTL script range.
    private val startsRtlPattern = Regex(
        "^[^A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF" +
            "\u2C00-\uFB1C\uFDFE-\uFE6F\uFEFD-\uFFFF]*" +
            "[\u0591-\u07FF\uFB1D-\uFDFD\uFE70-\uFEFC]"
    )

    fun startsRtl(text: String): Boolean = startsRtlPattern.containsMatchIn(text)

    fun textDirection(text: String): TextDirection =
        if (startsRtl(text)) TextDirection.Rtl else TextDirection.Ltr

    /**
     * Which edge a paragraph of [text] should sit against.
     *
     * [textDirection] reorders the runs within a line but does not move the line itself: Compose
     * resolves an unspecified alignment as `TextAlign.Start`, and `Start` is answered by the
     * *layout* direction rather than by the paragraph's own. In an English-language app that is
     * always the left edge, so a Persian message came out with its words correctly ordered and
     * then pinned to the left of the field, with the empty space on the side the reader starts
     * from. Alignment has to follow the same first-strong-character decision the direction does.
     *
     * Deliberately [TextAlign.Right] and [TextAlign.Left] rather than `Start`/`End`, which would
     * be resolved against the layout direction again and change nothing.
     */
    fun textAlign(text: String): TextAlign =
        if (startsRtl(text)) TextAlign.Right else TextAlign.Left

    fun wrap(text: String): String = formatter.unicodeWrap(text)
}