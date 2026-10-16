package com.bedrud.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val QuarterTurn = 90f
private const val QuarterTurnsPerTurn = 4
private const val FullTurn = 360f

/**
 * The shapes the indicator morphs through, in order: a scalloped cookie, a pentagon, a pill, a
 * sunburst, a four-lobed cookie and a circle. Each is drawn around its centre within radius 1, so
 * any of them can turn without reaching past the indicator's box. Their proportions keep their
 * areas close, so the indicator changes shape without seeming to swell and shrink: a full-radius
 * circle covered more than twice what a deeper-lobed cookie did, and read as a pulse.
 */
internal val LoadingIndicatorShapes: List<RoundedPolygon> = listOf(
    RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = 0.8f, rounding = CornerRounding(0.2f)),
    RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.3f)),
    RoundedPolygon.pill(width = 2f, height = 1.4f),
    RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.82f, rounding = CornerRounding(0.15f)),
    RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.8f, rounding = CornerRounding(0.35f)),
    RoundedPolygon.circle(radius = 0.85f),
)

/** Each shape's morph into the one after it, the last back into the first. */
private val LoadingIndicatorMorphs: List<Morph> = LoadingIndicatorShapes.indices.map { index ->
    Morph(LoadingIndicatorShapes[index], LoadingIndicatorShapes[(index + 1) % LoadingIndicatorShapes.size])
}

/**
 * The turn, in degrees, the morphs have added so far: a quarter for each, followed through its
 * spring, so the shape twists as it changes. [morphCount] wraps after four, a whole turn, which
 * keeps the angle small without a jump.
 */
internal fun loadingIndicatorMorphTurn(morphCount: Int, morphProgress: Float): Float =
    (morphCount % QuarterTurnsPerTurn + morphProgress) * QuarterTurn

/**
 * A wait of unknown length: a shape in the primary colour that morphs into the next every
 * [Motion.loadingMorphIntervalMs] while slowly turning. For a whole screen or panel that waits on
 * its content; a control waiting on its own action (a button, an upload) keeps Material's small
 * circular indicator.
 *
 * Material 3's expressive loading indicator, built to its sizes and colour role with graphics-shapes,
 * the library it is drawn with: Material's own composable is not in a stable release yet.
 */
@Composable
fun BedrudLoadingIndicator(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    var morphCount by remember { mutableIntStateOf(0) }
    val morphProgress = remember { Animatable(0f) }
    val turn by rememberInfiniteTransition(label = "loadingTurn").animateFloat(
        initialValue = 0f,
        targetValue = FullTurn,
        animationSpec = infiniteRepeatable(tween(Motion.loadingTurnMs, easing = LinearEasing)),
        label = "loadingTurnAngle",
    )
    // One path, refilled for every frame, rather than a new one each time.
    val path = remember { Path() }

    LaunchedEffect(Unit) {
        while (true) {
            val morph = launch { morphProgress.animateTo(1f, Motion.loadingMorphSpring) }
            delay(Motion.loadingMorphIntervalMs)
            morph.join()
            // The next morph starts from the shape this one ended on, so the hand-over is invisible.
            morphCount++
            morphProgress.snapTo(0f)
        }
    }

    // TODO(#289): Draw with Material's own LoadingIndicator once a stable Material 3 release has it.
    Canvas(
        modifier
            .progressSemantics()
            .size(Dimens.loadingIndicator)
    ) {
        val morph = LoadingIndicatorMorphs[morphCount % LoadingIndicatorMorphs.size]
        // The spring overshoots: the turn follows it past the end, but a shape past its target is
        // no shape at all.
        morph.toPath(morphProgress.value.coerceIn(0f, 1f), path.asAndroidPath().apply { rewind() })
        val radius = Dimens.loadingIndicatorShape.toPx() / 2
        withTransform({
            translate(center.x, center.y)
            rotate(turn + loadingIndicatorMorphTurn(morphCount, morphProgress.value), pivot = Offset.Zero)
            scale(radius, radius, pivot = Offset.Zero)
        }) {
            drawPath(path, color)
        }
    }
}
