package com.bedrud.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.centre
import com.bedrud.app.testutil.nearestOf
import com.bedrud.app.testutil.rowsWhere
import com.bedrud.app.testutil.setThemedContentAt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Where [BedrudBadge] draws its label's letters inside the pill, measured on the rendered pixels. */
class BedrudBadgeTest {

    @get:Rule
    val compose = createComposeRule()

    private var containerColor = Color.Unspecified
    private var labelColor = Color.Unspecified

    private fun showBadge(label: String) {
        compose.setThemedContentAt(FontScales.Default) {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
            // The pill's own colour behind it, so the corners its rounded ends leave unpainted can
            // never pass for letters.
            Box(modifier = Modifier.background(containerColor)) {
                BedrudBadge(text = label, modifier = Modifier.testTag(BadgeTag))
            }
        }
    }

    /** Asserts the label's letters are centred on the pill's middle row, measured in pixels. */
    private fun assertLabelCentredInPill() {
        val pill = compose.onNodeWithTag(BadgeTag).captureToImage().toPixelMap()
        val letters = pill.rowsWhere { it.nearestOf(listOf(labelColor, containerColor)) == labelColor }

        assertEquals(
            "letters at rows $letters in a pill ${pill.height}px tall",
            (pill.height - 1) / 2f,
            letters.centre,
            PositionTolerancePx,
        )
    }

    @Test
    fun shouldCentreEnglishLabelInPill() {
        showBadge(EnglishLabel)

        assertLabelCentredInPill()
    }

    /** Persian's tails hang below the line, so its letters are not where a Latin capital's are. */
    @Test
    fun shouldCentrePersianLabelInPill() {
        showBadge(PersianLabel)

        assertLabelCentredInPill()
    }

    private companion object {
        const val BadgeTag = "badge"
        const val EnglishLabel = "In use"
        const val PersianLabel = "در حال استفاده"

        /** A pixel and a half: the ink's own bounds and the offset applied to it each round to a pixel. */
        const val PositionTolerancePx = 1.5f
    }
}
