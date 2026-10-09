package com.bedrud.app.ui.screens.instance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.models.Instance
import com.bedrud.app.ui.components.BedrudBadge
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.components.ConfirmDialog
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.OnInstanceColor
import com.bedrud.app.ui.theme.parseInstanceColor
import com.bedrud.app.ui.theme.rememberTypeCenteringOffset
import com.bedrud.app.ui.theme.typeCentered

@Composable
private fun SwitcherRow(
    instance: Instance,
    isActive: Boolean,
    isEditing: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Geometry mirrors BedrudSheetActionRow — two-line height, same horizontal inset, same gap —
    // so a server row and an action row are indistinguishable in rhythm.
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The action row's corners too, so both rows press the same shape.
            .clip(BedrudShapeTokens.card)
            // While editing, a row offers only its remove button: a tap beside it must not switch
            // servers and close the sheet.
            .then(if (isEditing) Modifier else Modifier.clickable(onClick = onSelect))
            .defaultMinSize(minHeight = Dimens.sheetRowHeightTwoLine)
            // A trailing 48dp button brings its own inset, so the row keeps less of its own, as a
            // room card with a trailing button does.
            .padding(start = Dimens.space12, end = if (isEditing) Dimens.space4 else Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            InitialsAvatar(
                name = instance.displayName,
                containerColor = parseInstanceColor(instance.iconColorHex),
                contentColor = OnInstanceColor,
            )

            Spacer(modifier = Modifier.width(Dimens.space16))

            // Centred on the avatar as one block, as BedrudSheetActionRow's lines are.
            val nameStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
            val urlStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .typeCentered(firstLine = nameStyle, lastLine = urlStyle)
            ) {
                // The server in use says so in words beside its name, the badge the add-server
                // screen marks an added server with, which leaves the row's trailing slot to its
                // remove button while editing.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = instance.displayName,
                        style = nameStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Gives up its width before the badge does, so a long name ellipsizes
                        // and the badge stays whole beside it.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(Dimens.space8))
                        // As the profile card's Admin badge: the block moved the name as it
                        // stands, so its letters still sit above its own box's centre. The badge
                        // is raised by the name's correction to meet them.
                        val nameCorrection = rememberTypeCenteringOffset(nameStyle)
                        BedrudBadge(
                            text = stringResource(R.string.instance_status_inUse),
                            modifier = Modifier.offset(y = -nameCorrection)
                        )
                    }
                }
                Text(
                    text = instance.serverURL,
                    style = urlStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isEditing) {
            // The same destructive icon button the admin lists delete with.
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.instance_contentDescription_removeServer,
                        instance.displayName
                    ),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Asks before [server] is removed, saying what goes with it and, when it is the server in use,
 * where the app goes next: [successor], or Add server when there is none.
 */
@Composable
private fun RemoveServerDialog(
    server: Instance,
    isActive: Boolean,
    successor: Instance?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when {
        !isActive -> stringResource(R.string.instance_dialog_removeMessage)
        successor != null -> stringResource(R.string.instance_dialog_removeActiveMessage, successor.displayName)
        else -> stringResource(R.string.instance_dialog_removeLastMessage)
    }
    ConfirmDialog(
        title = stringResource(R.string.instance_dialog_removeTitle, server.displayName),
        message = message,
        confirmLabel = stringResource(R.string.common_action_remove),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The switcher's content — its title, the saved servers and the add action — without the sheet. */
@Composable
internal fun InstanceSwitcherContent(
    instances: List<Instance>,
    activeId: String?,
    serverAfterRemoving: (String) -> Instance?,
    onSelect: (Instance) -> Unit,
    onRemove: (Instance) -> Unit,
    onAddInstance: () -> Unit,
) {
    // Both survive a rotation and end with the sheet: dismissing it leaves edit mode. The server
    // being asked about is held by id, so its dialog closes by itself if the server goes meanwhile.
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var removalAskedForId by rememberSaveable { mutableStateOf<String?>(null) }

    instances.firstOrNull { it.id == removalAskedForId }?.let { server ->
        RemoveServerDialog(
            server = server,
            isActive = server.id == activeId,
            successor = serverAfterRemoving(server.id),
            onConfirm = {
                removalAskedForId = null
                onRemove(server)
            },
            onDismiss = { removalAskedForId = null },
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BedrudSheetTitle(
            text = stringResource(R.string.instance_title_switchServer),
            modifier = Modifier.weight(1f)
        )
        BedrudButton(
            text = stringResource(if (isEditing) R.string.instance_button_done else R.string.instance_button_edit),
            onClick = { isEditing = !isEditing },
            variant = BedrudButtonVariant.GHOST,
        )
    }

    LazyColumn {
        items(instances, key = { it.id }) { instance ->
            SwitcherRow(
                instance = instance,
                isActive = instance.id == activeId,
                isEditing = isEditing,
                onSelect = { onSelect(instance) },
                onRemove = { removalAskedForId = instance.id },
                // A removed server's row slides out and the rows under it close the gap.
                modifier = Modifier.animateItem()
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
        onClick = onAddInstance
    )
}

@Composable
fun InstanceSwitcherSheet(
    instanceManager: InstanceManager,
    onDismiss: () -> Unit,
    onAddInstance: () -> Unit
) {
    val instances by instanceManager.store.instances.collectAsState()
    val activeId by instanceManager.store.activeInstanceId.collectAsState()

    BedrudBottomSheet(onDismiss = onDismiss) {
        InstanceSwitcherContent(
            instances = instances,
            activeId = activeId,
            serverAfterRemoving = instanceManager.store::serverAfterRemoving,
            onSelect = { instance ->
                instanceManager.switchTo(instance.id)
                onDismiss()
            },
            onRemove = { instance ->
                // Removing the server in use moves the app on, to the next server or to Add
                // server, so the sheet goes with it; removing another leaves it open for more.
                val wasActive = instance.id == activeId
                instanceManager.removeInstance(instance.id)
                if (wasActive) onDismiss()
            },
            onAddInstance = {
                onDismiss()
                onAddInstance()
            }
        )
    }
}
