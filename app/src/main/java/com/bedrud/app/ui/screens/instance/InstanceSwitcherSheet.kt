package com.bedrud.app.ui.screens.instance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.models.Instance
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.parseInstanceColor

@Composable
fun InstanceSwitcherSheet(
    instanceManager: InstanceManager,
    onDismiss: () -> Unit,
    onAddInstance: () -> Unit
) {
    val instances by instanceManager.store.instances.collectAsState()
    val activeId by instanceManager.store.activeInstanceId.collectAsState()

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(text = stringResource(R.string.instance_title_switchServer))

        LazyColumn {
            items(instances, key = { it.id }) { instance ->
                SwitcherRow(
                    instance = instance,
                    isActive = instance.id == activeId,
                    onSelect = {
                        instanceManager.switchTo(instance.id)
                        onDismiss()
                    }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.space8))

        // "Add server" is a plain icon + label action, so it is the standard row rather than a
        // hand-rolled one — same height, same inset, same icon size as every other sheet action.
        BedrudSheetActionRow(
            icon = Icons.Default.Add,
            title = stringResource(R.string.instance_button_addServer),
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = {
                onDismiss()
                onAddInstance()
            }
        )
    }
}

@Composable
private fun SwitcherRow(
    instance: Instance,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    // Geometry mirrors BedrudSheetActionRow — two-line height, same horizontal inset, same gap —
    // so a server row and an action row are indistinguishable in rhythm.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .defaultMinSize(minHeight = Dimens.sheetRowHeightTwoLine)
            .padding(horizontal = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            InitialsAvatar(
                name = instance.displayName,
                size = SwitcherAvatar,
                containerColor = parseInstanceColor(instance.iconColorHex)
            )

            Spacer(modifier = Modifier.width(Dimens.space16))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instance.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = instance.serverURL,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isActive) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.instance_status_active),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

// The server avatar is this sheet's own leading element, not a shared size.
private val SwitcherAvatar = 32.dp
