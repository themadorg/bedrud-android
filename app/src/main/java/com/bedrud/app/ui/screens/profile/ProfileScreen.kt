package com.bedrud.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults

import com.bedrud.app.ui.components.BedrudBadge
import com.bedrud.app.ui.components.BedrudOutlinedCard
import com.bedrud.app.ui.components.CardSectionHeader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import com.bedrud.app.ui.components.BedrudCompactTopBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.screens.instance.InstanceSwitcherSheet
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.OnInstanceColor
import com.bedrud.app.ui.theme.parseInstanceColor
import com.bedrud.app.ui.theme.rememberTypeCenteringOffset
import com.bedrud.app.ui.theme.typeCentered
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onNavigateToAddInstance: () -> Unit,
    instanceManager: InstanceManager = koinInject()
) {
    val authManager = instanceManager.authManager.collectAsState().value
    val user by (authManager?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val activeInstance = instanceManager.store.activeInstance
    var showInstanceSwitcher by remember { mutableStateOf(false) }

    if (showInstanceSwitcher) {
        InstanceSwitcherSheet(
            instanceManager = instanceManager,
            onDismiss = { showInstanceSwitcher = false },
            onAddInstance = {
                showInstanceSwitcher = false
                onNavigateToAddInstance()
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        BedrudCompactTopBar(title = stringResource(R.string.profile_title))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // The same page padding and card gap on every tab.
                .padding(Dimens.screenPaddingCompact),
            verticalArrangement = Arrangement.spacedBy(Dimens.space16)
        ) {
            // User section
            BedrudOutlinedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.cardPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!user?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user?.avatarUrl,
                            contentDescription = stringResource(R.string.profile_contentDescription_profilePicture),
                            modifier = Modifier
                                .size(Dimens.avatarXl)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        InitialsAvatar(
                            name = user?.name,
                            size = Dimens.avatarXl,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(Dimens.space16))
                    // Name and email are centred on the avatar as one block. The name's own
                    // correction would not do: at 22sp over 14sp, the two lines' corrections added
                    // up put the block 4px low.
                    val nameStyle = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content)
                    val emailStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .typeCentered(firstLine = nameStyle, lastLine = emailStyle)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.space8)
                        ) {
                            Text(
                                user?.name ?: stringResource(R.string.profile_fallback_name),
                                style = nameStyle
                            )
                            if (user?.isAdmin == true) {
                                // The block moved the name as it stands, so its letters still sit
                                // above its own box's centre. The badge is raised by the name's
                                // correction to meet them, rather than the name lowered to meet it.
                                val nameCorrection = rememberTypeCenteringOffset(nameStyle)
                                BedrudBadge(
                                    text = stringResource(R.string.profile_badge_admin),
                                    modifier = Modifier.offset(y = -nameCorrection),
                                )
                            }
                        }
                        Text(
                            user?.email ?: "",
                            style = emailStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Server section
            BedrudOutlinedCard {
                Column {
                    CardSectionHeader(
                        stringResource(R.string.profile_section_server),
                        modifier = Modifier.padding(start = Dimens.cardPadding, top = Dimens.cardPadding, end = Dimens.cardPadding, bottom = Dimens.space8)
                    )

                    if (activeInstance != null) {
                        // Both lines take the block's correction, so they move as one.
                        val serverNameStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
                        val urlStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
                        ListItem(
                            headlineContent = {
                                Text(
                                    activeInstance.displayName,
                                    style = serverNameStyle,
                                    modifier = Modifier.typeCentered(firstLine = serverNameStyle, lastLine = urlStyle)
                                )
                            },
                            supportingContent = {
                                Text(
                                    activeInstance.serverURL,
                                    style = urlStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.typeCentered(firstLine = serverNameStyle, lastLine = urlStyle)
                                )
                            },
                            leadingContent = {
                                InitialsAvatar(
                                    name = activeInstance.displayName,
                                    containerColor = parseInstanceColor(activeInstance.iconColorHex),
                                    contentColor = OnInstanceColor,
                                )
                            },
                            trailingContent = {
                                FilledTonalButton(onClick = { showInstanceSwitcher = true }) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val switchStyle = LocalTextStyle.current
                                    Text(
                                        stringResource(R.string.profile_button_switch),
                                        modifier = Modifier.typeCentered(switchStyle),
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            // The account's details (ID, sign-in method, role) live in Settings' Account card
            // alone. Profile showed the same ID and method a second time, under other labels and
            // in another format.

            // Sign Out
            TextButton(
                onClick = onLogout,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Centred against the icon beside it, like every other label paired with one.
                val signOutStyle = MaterialTheme.typography.labelLarge
                Text(
                    stringResource(R.string.profile_button_signOut),
                    style = signOutStyle,
                    modifier = Modifier.typeCentered(signOutStyle),
                )
            }
        }
    }
}
