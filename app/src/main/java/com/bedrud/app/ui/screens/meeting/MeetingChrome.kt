package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import com.bedrud.app.ui.components.BedrudBottomSheet
import androidx.compose.ui.unit.dp
import com.bedrud.app.ui.theme.Dimens

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
    val dragHandle: Color,
    val sheet: Color,
    val selected: Color,
    val accent: Color,
    val warning: Color,
    val onWarning: Color,
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
        dragHandle = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        sheet = scheme.surface,
        selected = scheme.secondary,
        accent = scheme.primary,
        warning = scheme.error,
        onWarning = scheme.onError,
        endCall = scheme.error,
        onEndCall = scheme.onError,
    )
}

/**
 * The in-meeting sheets, on the app's shared [BedrudBottomSheet].
 *
 * All this adds is the meeting chrome's own palette — the call UI sits on a dark overlay, so its
 * sheets are darker than the app default. Everything else (shape, drag handle, insets, gutter)
 * comes from the shared component so these sheets match the rest of the app.
 */
@Composable
fun MeetingBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = meetingChromeColors()
    BedrudBottomSheet(
        onDismiss = onDismiss,
        containerColor = colors.sheet,
        dragHandleColor = colors.dragHandle,
        content = content,
    )
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
