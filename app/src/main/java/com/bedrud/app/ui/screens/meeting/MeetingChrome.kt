package com.bedrud.app.ui.screens.meeting

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation
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
    val onAccent: Color,
    val mediaError: Color,
    val onMediaError: Color,
    val endCall: Color,
    val onEndCall: Color,
)

/**
 * The floating bottom bar's shell: the surface every bar over the call stands on.
 *
 * The controls bar and the chat composer are the same object with different contents — they sit in
 * the same place on screen and swap with each other — so their margin, corner, fill, hairline and
 * lift come from this one composable rather than from values each keeps matched by hand. When the
 * two drifted apart (12dp lower, 16dp shorter, a different elevation), every drifted value was a
 * separate token doing the same job; this is the fix that makes the next drift impossible rather
 * than merely repaired.
 *
 * Contents supply their own inner padding — [Dimens.meetingBarPaddingH] beside,
 * [Dimens.meetingBarPaddingV] below (and above, for a bar without a handle) — so a 48dp control
 * band lands identically in every bar built on this.
 */
@Composable
fun MeetingBarSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = meetingChromeColors()
    Surface(
        modifier = modifier
            .padding(horizontal = Dimens.meetingScreenMargin)
            .fillMaxWidth(),
        shape = BedrudShapeTokens.controlsBar,
        color = colors.bar,
        shadowElevation = Elevation.controlsBarShadow,
        tonalElevation = Elevation.controlsBarTonal,
        border = BorderStroke(Dimens.borderThin, colors.divider),
        content = content,
    )
}

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
        onAccent = scheme.onPrimary,
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
        animationSpec = tween(Motion.meetingSpeakingFadeMs, easing = Motion.standardEasing),
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
        border(
            width,
            MaterialTheme.colorScheme.primary.copy(alpha = alpha * SpeakingRingPeakAlpha),
            shape,
        )
    }
}

/**
 * How solid the ring gets at its loudest.
 *
 * The in-call chrome is otherwise restrained — tonal surfaces and thin outlines — and a
 * fully-saturated brand border around a whole tile shouts over all of it. Held short of solid, the
 * ring still reads instantly as "this person is talking" without becoming the loudest thing on the
 * screen.
 */
private const val SpeakingRingPeakAlpha = 0.7f

/**
 * The speaking badge inside a participant's name chip — [speakingRing]'s signal in a second form,
 * because colour alone cannot carry it.
 *
 * It is always in the row and is never added or removed. Speech is bursty: people pause between
 * sentences, and an icon that came and went would flicker through every one of those pauses and
 * shove the name sideways each time. So the badge calms instead of leaving — it brightens to the
 * accent while the room hears this person and settles back to a quiet outline when it stops
 * hearing them, on the same [Motion.meetingSpeakingFadeMs] as the ring.
 *
 * [level] is that participant's entry in `RoomManager.speakingLevels`. Only its presence is used:
 * the ring already carries loudness in its thickness, and a badge this small cannot show a
 * magnitude legibly enough to be worth the motion.
 */
@Composable
fun SpeakingBadge(level: Float, modifier: Modifier = Modifier) {
    val isSpeaking = level.coerceIn(0f, 1f) > 0f
    val progress by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else 0f,
        animationSpec = tween(Motion.meetingSpeakingFadeMs, easing = Motion.standardEasing),
        label = "speakingBadge",
    )
    Icon(
        imageVector = Icons.Default.GraphicEq,
        // Silence is the resting state, not an event: only speech is worth announcing.
        contentDescription = if (isSpeaking) {
            stringResource(R.string.meeting_contentDescription_speaking)
        } else {
            null
        },
        // One interpolation carries both the hue and the fade, so they can never drift apart.
        tint = lerp(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SpeakingBadgeCalmAlpha),
            MaterialTheme.colorScheme.primary,
            progress,
        ),
        modifier = modifier.size(Dimens.meetingBadgeIcon),
    )
}

/**
 * How faint the badge goes once the room stops hearing someone.
 *
 * Low enough to read as off at a glance across a grid of tiles, but still visible — it has to
 * stay legible as the same control that lights up, or its return looks like something appearing
 * rather than something waking.
 */
private const val SpeakingBadgeCalmAlpha = 0.35f

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
