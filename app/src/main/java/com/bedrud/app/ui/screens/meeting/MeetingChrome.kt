package com.bedrud.app.ui.screens.meeting

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion

/**
 * The in-call chrome's palette.
 *
 * [mediaError] is the error role, not the amber warning one — it marks a camera or microphone that
 * failed to start, which is a failure rather than a caution. The genuine warning role lives at
 * `MaterialTheme.bedrudColors.warning` and is what the mic pill's status ring uses; naming this
 * one "warning" put two different colours under the same word on the same screen.
 */
@Immutable
data class MeetingChromeColors(
    val bar: Color,
    val button: Color,
    val buttonActive: Color,
    val buttonMediaOff: Color,
    val onButtonMediaOff: Color,
    val onButton: Color,
    val onButtonVariant: Color,
    val divider: Color,
    val selected: Color,
    val accent: Color,
    val mediaError: Color,
    val onMediaError: Color,
    val endCall: Color,
    val onEndCall: Color,
)

@Composable
fun meetingChromeColors(): MeetingChromeColors {
    val scheme = MaterialTheme.colorScheme
    return MeetingChromeColors(
        bar = scheme.surfaceContainerHigh,
        button = scheme.surfaceVariant,
        buttonActive = scheme.secondary,
        buttonMediaOff = scheme.surfaceContainerHighest,
        onButtonMediaOff = scheme.onSurfaceVariant,
        onButton = scheme.onSurface,
        onButtonVariant = scheme.onSurfaceVariant,
        divider = scheme.outline.copy(alpha = 0.45f),
        selected = scheme.secondary,
        accent = scheme.primary,
        mediaError = scheme.error,
        onMediaError = scheme.onError,
        endCall = scheme.error,
        onEndCall = scheme.onError,
    )
}

/**
 * The room-hears-you ring, drawn inside [shape] on any surface that stands for a participant.
 *
 * [level] is that participant's entry in `RoomManager.speakingLevels` — the room's own report, not
 * a local microphone reading — so seeing your own tile ring is confirmation that your audio is
 * arriving, which is the whole reason it is drawn on the local tile too. The ring thickens with
 * the reported level and fades in and out rather than blinking, because the server announces
 * speakers in bursts and never announces silence.
 */
@Composable
fun Modifier.speakingRing(level: Float, shape: Shape): Modifier {
    val clamped = level.coerceIn(0f, 1f)
    val alpha by animateFloatAsState(
        targetValue = if (clamped > 0f) 1f else 0f,
        animationSpec = tween(Motion.meetingSpeakingRingFadeMs, easing = Motion.standardEasing),
        label = "speakingRingAlpha",
    )
    val width by animateDpAsState(
        targetValue = Dimens.meetingSpeakingRingMin +
            (Dimens.meetingSpeakingRingMax - Dimens.meetingSpeakingRingMin) * clamped,
        animationSpec = tween(Motion.meetingSpeakingRingLevelMs, easing = Motion.standardEasing),
        label = "speakingRingWidth",
    )
    return if (alpha <= 0f) {
        this
    } else {
        border(width, MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)
    }
}

/**
 * The in-call slider: a small round thumb on a slim track instead of M3's tall-bar thumb, shared
 * by the participant volume, output volume and sensitivity sliders so they all read the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingCompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = meetingChromeColors()
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = colors.accent.copy(alpha = SliderInactiveTrackAlpha),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
        colors = sliderColors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = sliderColors,
                thumbSize = DpSize(Dimens.meetingSliderThumb, Dimens.meetingSliderThumb),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(Dimens.meetingSliderTrack),
                colors = sliderColors,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
}

private const val SliderInactiveTrackAlpha = 0.24f
