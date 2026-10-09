package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.bedrud.app.ui.theme.Alpha
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.typeCentered

/**
 * Makes a whole row the switch, as Material's switch guidance asks: a tap on the label toggles it
 * like a tap on the switch, and a screen reader meets one switch named by its label rather than a
 * label beside an unnamed switch. The [Switch] inside such a row takes no click of its own.
 */
private fun Modifier.switchRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Modifier = toggleable(
    value = checked,
    enabled = enabled,
    role = Role.Switch,
    onValueChange = onCheckedChange,
)

/**
 * A labelled switch inside a form, such as a dialog or a sheet: the label at the content's own
 * inset, the switch at its end. [supportingContent], when given, sits under the label.
 *
 * [contentColor] lets a sheet drawn on the meeting's chrome palette colour the label; Unspecified
 * inherits the ambient colour.
 */
@Composable
fun BedrudSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    supportingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .switchRow(checked, enabled, onCheckedChange)
            .padding(vertical = Dimens.space4)
            // The row is the touch target now, so it keeps the height the switch's own 48dp target
            // used to give it; without a click of its own the switch no longer reserves one.
            .heightIn(min = Dimens.minTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val labelStyle = MaterialTheme.typography.bodyLarge
            // The switch dims itself when disabled; its label is the app's own text, so it takes
            // the same disabled opacity rather than staying full strength beside a greyed switch.
            Text(
                text = label,
                color = contentColor,
                style = labelStyle,
                modifier = Modifier
                    .alpha(if (enabled) 1f else Alpha.disabled)
                    .typeCentered(labelStyle)
            )
            if (supportingContent != null) {
                Spacer(Modifier.height(Dimens.space4))
                supportingContent()
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A labelled switch as a row of a card's list, such as a Settings section: Material's list item,
 * transparent on the card, with the switch as its trailing content.
 */
@Composable
fun BedrudSwitchListItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(label, modifier = Modifier.typeCentered(LocalTextStyle.current))
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.switchRow(checked, enabled, onCheckedChange),
    )
}
