package com.bedrud.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.isPaint
import com.bedrud.app.testutil.setThemedContentAt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What [BedrudLoadingIndicator] tells a screen reader, and what it draws, measured on its pixels. */
class BedrudLoadingIndicatorTest {

    @get:Rule
    val compose = createComposeRule()

    private var primary = Color.Unspecified

    private fun showIndicator() {
        compose.setThemedContentAt(FontScales.Default) {
            primary = MaterialTheme.colorScheme.primary
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                BedrudLoadingIndicator(modifier = Modifier.testTag(IndicatorTag))
            }
        }
    }

    /** A screen reader announces a wait of unknown length, as it does for Material's own indicators. */
    @Test
    fun shouldReportProgressOfUnknownLength() {
        showIndicator()

        compose.onNodeWithTag(IndicatorTag).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
        )
    }

    /** A filled shape, where Material's circular indicator draws a thin arc that covers far less of its box. */
    @Test
    fun shouldFillItsShapeWithPrimary() {
        showIndicator()

        val pixels = compose.onNodeWithTag(IndicatorTag).captureToImage().toPixelMap()
        val filled = (0 until pixels.width).sumOf { x ->
            (0 until pixels.height).count { y -> pixels[x, y].isPaint(primary) }
        }
        val share = filled.toFloat() / (pixels.width * pixels.height)

        assertTrue("primary covers ${share * 100}% of the indicator", share >= FilledShareMin)
    }

    private companion object {
        const val IndicatorTag = "loadingIndicator"

        /**
         * The smallest shape it morphs through covers about a third of its box, a 38dp shape in a
         * 48dp box; Material's circular indicator measured about 3% here.
         */
        const val FilledShareMin = 0.3f
    }
}
