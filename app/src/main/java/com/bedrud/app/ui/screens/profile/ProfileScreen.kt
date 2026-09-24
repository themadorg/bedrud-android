package com.bedrud.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults

import com.bedrud.app.ui.components.BedrudOutlinedCard
import com.bedrud.app.ui.components.CardSectionHeader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.screens.instance.InstanceSwitcherSheet
import com.bedrud.app.ui.theme.BedrudRadius
import com.bedrud.app.ui.theme.BedrudShapeTokens
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            // User section
            BedrudOutlinedCard(shape = BedrudShapeTokens.card) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!user?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user?.avatarUrl,
                            contentDescription = stringResource(R.string.profile_contentDescription_profilePicture),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        InitialsAvatar(
                            name = user?.name,
                            size = 56.dp,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                user?.name ?: stringResource(R.string.profile_fallback_name),
                                style = nameStyle
                            )
                            if (user?.isAdmin == true) {
                                val badgeStyle = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                                // The block moved the name as it stands, so its letters still sit
                                // above its own box's centre. The badge is raised by the name's
                                // correction to meet them, rather than the name lowered to meet it.
                                val nameCorrection = rememberTypeCenteringOffset(nameStyle)
                                Text(
                                    stringResource(R.string.profile_badge_admin),
                                    style = badgeStyle,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .offset(y = -nameCorrection)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(BedrudRadius.xs)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        // After the background, so the letters move inside the badge.
                                        .typeCentered(badgeStyle)
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
            BedrudOutlinedCard(shape = BedrudShapeTokens.card) {
                Column {
                    CardSectionHeader(
                        stringResource(R.string.profile_section_server),
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
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

            // Account section
            BedrudOutlinedCard(shape = BedrudShapeTokens.card) {
                Column {
                    CardSectionHeader(
                        stringResource(R.string.profile_section_account),
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                    )

                    if (user != null) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.profile_label_userId),
                                    modifier = Modifier.typeCentered(LocalTextStyle.current)
                                )
                            },
                            trailingContent = {
                                val valueStyle = MaterialTheme.typography.bodyMedium
                                Text(
                                    user!!.id.take(8) + "...",
                                    style = valueStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.typeCentered(valueStyle)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        if (user?.provider != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.profile_label_provider),
                                        modifier = Modifier.typeCentered(LocalTextStyle.current)
                                    )
                                },
                                trailingContent = {
                                    val valueStyle = MaterialTheme.typography.bodyMedium
                                    Text(
                                        user!!.provider!!.replaceFirstChar { it.uppercase() },
                                        style = valueStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.typeCentered(valueStyle)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
