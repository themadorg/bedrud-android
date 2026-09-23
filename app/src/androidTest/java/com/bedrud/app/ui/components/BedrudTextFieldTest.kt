package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.testutil.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** How [BedrudTextField] lays out a placeholder too long for one line at the reader's font size. */
class BedrudTextFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showEmptyField(singleLine: Boolean) {
        compose.setThemedContentAt(FontScales.Largest) {
            Box(modifier = Modifier.width(FieldWidth)) {
                BedrudTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = LongPlaceholder,
                    singleLine = singleLine,
                )
            }
        }
    }

    private fun placeholderLineCount(): Int =
        compose.onNodeWithText(LongPlaceholder, useUnmergedTree = true).textLayout().lineCount

    /** A hint that wrapped would make the field taller than the one line of text it takes. */
    @Test
    fun shouldKeepPlaceholderOnOneLineInSingleLineField() {
        showEmptyField(singleLine = true)

        assertEquals(1, placeholderLineCount())
    }

    @Test
    fun shouldLetPlaceholderWrapInMultiLineField() {
        showEmptyField(singleLine = false)

        assertTrue("multi-line hint kept to one line", placeholderLineCount() > 1)
    }

    private companion object {
        /** Narrow enough that the placeholder cannot fit one line at the largest font scale. */
        val FieldWidth = 240.dp

        const val LongPlaceholder = "Enter a room name or paste a link to it"
    }
}
