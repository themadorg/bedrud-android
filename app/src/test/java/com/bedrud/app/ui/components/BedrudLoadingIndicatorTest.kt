package com.bedrud.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.RoundedPolygon
import org.junit.Test
import kotlin.math.abs

/** Steps each curve is walked in to measure the area it encloses; more change only the fourth decimal. */
private const val AreaSamplesPerCurve = 16

/** The point [fraction] of the way along this curve. */
private fun Cubic.pointAt(fraction: Float): Pair<Float, Float> {
    val rest = 1 - fraction
    val start = rest * rest * rest
    val firstControl = 3 * rest * rest * fraction
    val secondControl = 3 * rest * fraction * fraction
    val end = fraction * fraction * fraction
    return Pair(
        start * anchor0X + firstControl * control0X + secondControl * control1X + end * anchor1X,
        start * anchor0Y + firstControl * control0Y + secondControl * control1Y + end * anchor1Y,
    )
}

/** The area this shape covers, from points along its outline by the shoelace formula. */
private fun RoundedPolygon.area(): Float {
    val outline = cubics.flatMap { cubic ->
        (0 until AreaSamplesPerCurve).map { step -> cubic.pointAt(step.toFloat() / AreaSamplesPerCurve) }
    }
    val doubled = outline.indices.sumOf { index ->
        val (x, y) = outline[index]
        val (nextX, nextY) = outline[(index + 1) % outline.size]
        (x * nextY - nextX * y).toDouble()
    }
    return abs(doubled / 2).toFloat()
}

/**
 * The shapes [BedrudLoadingIndicator] morphs between, and the turn it adds with each morph. The
 * shapes turn as they morph, so one reaching past the circle its box is sized for would sweep
 * outside the indicator; and the turn must carry on unbroken where one morph hands over to the next.
 */
class BedrudLoadingIndicatorTest {

    @Test
    fun `should keep every shape inside the circle it turns in`() {
        LoadingIndicatorShapes.forEachIndexed { index, shape ->
            // The square that holds the shape at any angle: twice its farthest reach from its centre.
            val (left, top, right, bottom) = shape.calculateMaxBounds().toList()
            val reach = (right - left) / 2

            assertEquals("shape $index's centre", 0f, (left + right) / 2, RadiusTolerance)
            assertEquals("shape $index's centre", 0f, (top + bottom) / 2, RadiusTolerance)
            assertTrue("shape $index reaches $reach from its centre", reach <= 1f + RadiusTolerance)
        }
    }

    @Test
    fun `should morph between more than one shape`() {
        assertTrue(LoadingIndicatorShapes.distinct().size > 1)
    }

    /** Shapes of very different areas read as the indicator swelling and shrinking, not morphing. */
    @Test
    fun `should keep the shapes close in area`() {
        val areas = LoadingIndicatorShapes.map { it.area() }

        assertTrue("areas $areas", areas.max() / areas.min() <= MaxAreaRatio)
    }

    @Test
    fun `should turn a quarter over each morph`() {
        assertEquals(0f, loadingIndicatorMorphTurn(morphCount = 0, morphProgress = 0f), AngleTolerance)
        assertEquals(45f, loadingIndicatorMorphTurn(morphCount = 0, morphProgress = 0.5f), AngleTolerance)
        assertEquals(90f, loadingIndicatorMorphTurn(morphCount = 0, morphProgress = 1f), AngleTolerance)
    }

    /** One morph ends at progress 1 and the next starts at progress 0, with the count one higher. */
    @Test
    fun `should not jump where one morph hands over to the next`() {
        val endOfMorph = loadingIndicatorMorphTurn(morphCount = 2, morphProgress = 1f)
        val startOfNext = loadingIndicatorMorphTurn(morphCount = 3, morphProgress = 0f)

        assertEquals(endOfMorph, startOfNext, AngleTolerance)
    }

    /** The count wraps after four quarter turns, which is a whole turn, so wrapping cannot jump either. */
    @Test
    fun `should not jump where the count wraps to a whole turn`() {
        val endOfLastQuarter = loadingIndicatorMorphTurn(morphCount = 3, morphProgress = 1f)
        val startOfFirstQuarter = loadingIndicatorMorphTurn(morphCount = 4, morphProgress = 0f)

        assertEquals(endOfLastQuarter % FullTurn, startOfFirstQuarter % FullTurn, AngleTolerance)
    }

    /** The morph springs past its end before settling, and the turn follows it there. */
    @Test
    fun `should follow the spring past the end of a morph`() {
        assertTrue(loadingIndicatorMorphTurn(morphCount = 0, morphProgress = 1.1f) > 90f)
    }

    private companion object {
        const val FullTurn = 360f
        const val AngleTolerance = 0.001f

        /** How much more the largest shape may cover than the smallest before the change reads as a size. */
        const val MaxAreaRatio = 1.2f

        /**
         * Rounding a corner pulls it inwards, never out, so only the curves' own approximation of a
         * circle can reach past the radius, and by far less than this.
         */
        const val RadiusTolerance = 0.01f
    }
}
