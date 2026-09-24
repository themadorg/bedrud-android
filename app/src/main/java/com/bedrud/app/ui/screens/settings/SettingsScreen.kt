package com.bedrud.app.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudOutlinedCard
import com.bedrud.app.ui.components.BedrudPasswordField
import com.bedrud.app.ui.components.BedrudTextField
import com.bedrud.app.core.auth.PasswordPolicy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.bedrud.app.ui.components.BedrudCompactTopBar
import com.bedrud.app.ui.components.BedrudSnackbarHost
import com.bedrud.app.ui.components.BedrudTabScaffoldContentInsets
import com.bedrud.app.ui.components.CardSectionHeader
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.bedrud.app.R
import com.bedrud.app.core.auth.SignInMethod
import com.bedrud.app.core.auth.signInMethodOf
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation
import com.bedrud.app.models.ChangePasswordRequest
import com.bedrud.app.core.api.apiAction
import com.bedrud.app.ui.theme.typeCentered
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The sign-in method in the app's language, or an identity provider by its own name. */
@Composable
private fun signInMethodLabel(method: SignInMethod): String = when (method) {
    SignInMethod.Email -> stringResource(R.string.settings_provider_email)
    SignInMethod.Passkey -> stringResource(R.string.settings_provider_passkey)
    is SignInMethod.Provider -> method.name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    settingsStore: SettingsStore = koinInject(),
    instanceManager: InstanceManager = koinInject()
) {
    val appearance by settingsStore.appearance.collectAsState()
    val notificationsEnabled by settingsStore.notificationsEnabled.collectAsState()
    val language by settingsStore.language.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authApi = instanceManager.authApi.collectAsState().value
    val authManager = instanceManager.authManager.collectAsState().value
    val currentUser by (authManager?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val signInMethod = signInMethodOf(currentUser?.provider)

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = BedrudTabScaffoldContentInsets,
        topBar = { BedrudCompactTopBar(title = stringResource(R.string.settings_title)) },
        snackbarHost = { BedrudSnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // The same page padding and card gap on every tab.
                .padding(Dimens.screenPaddingCompact),
            verticalArrangement = Arrangement.spacedBy(Dimens.space16)
        ) {
            // Appearance
            BedrudOutlinedCard {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                    CardSectionHeader(stringResource(R.string.settings_section_appearance))
                    Spacer(modifier = Modifier.height(Dimens.space12))
                    Text(
                        stringResource(R.string.settings_label_theme),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(Dimens.space8))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppAppearance.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = appearance == option,
                                onClick = { settingsStore.setAppearance(option) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AppAppearance.entries.size
                                )
                            ) {
                                Text(
                                    stringResource(option.stringResId),
                                    modifier = Modifier.typeCentered(LocalTextStyle.current)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimens.space12))
                    Text(
                        stringResource(R.string.settings_label_language),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(Dimens.space8))
                    var languageExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it }
                    ) {
                        BedrudTextField(
                            value = language.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        // The chrome of the app's other menus (the chat's), so every menu that
                        // drops open has the same corners, fill and lift.
                        ExposedDropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false },
                            shape = BedrudShapeTokens.card,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = Elevation.level2,
                            shadowElevation = Elevation.level3,
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(lang.label, modifier = Modifier.typeCentered(LocalTextStyle.current))
                                    },
                                    onClick = {
                                        settingsStore.setLanguage(lang)
                                        languageExpanded = false
                                        (context as? Activity)?.recreate()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Notifications
            BedrudOutlinedCard {
                Column {
                    CardSectionHeader(
                        stringResource(R.string.settings_section_notifications),
                        modifier = Modifier.padding(start = Dimens.cardPadding, top = Dimens.cardPadding, end = Dimens.cardPadding, bottom = Dimens.space8)
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_label_enableNotifications),
                                modifier = Modifier.typeCentered(LocalTextStyle.current)
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { settingsStore.setNotificationsEnabled(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Account Info
            if (currentUser != null) {
                BedrudOutlinedCard {
                    Column {
                        // The admin mark sits centred on the header's letters, at the small icon
                        // size a mark beside a label takes elsewhere; it used to hang from the top
                        // of the row at full icon size.
                        Row(
                            modifier = Modifier.padding(start = Dimens.cardPadding, top = Dimens.cardPadding, end = Dimens.cardPadding, bottom = Dimens.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.space8)
                        ) {
                            CardSectionHeader(
                                stringResource(R.string.settings_section_account),
                                modifier = Modifier.typeCentered(MaterialTheme.typography.labelLarge)
                            )
                            if (currentUser?.isAdmin == true) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(Dimens.iconXs)
                                )
                            }
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_label_accountId),
                                    modifier = Modifier.typeCentered(LocalTextStyle.current)
                                )
                            },
                            // Every value in the card at one size, one colour; the ID alone keeps a
                            // monospace face, as the app's other machine-made names do.
                            trailingContent = {
                                val idStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                Text(currentUser?.id?.take(8) ?: "", style = idStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.typeCentered(idStyle))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.space16),
                            color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_label_signInMethod),
                                    modifier = Modifier.typeCentered(LocalTextStyle.current)
                                )
                            },
                            trailingContent = {
                                val valueStyle = MaterialTheme.typography.bodyMedium
                                Text(signInMethodLabel(signInMethod),
                                    style = valueStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.typeCentered(valueStyle))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.space16),
                            color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_label_role),
                                    modifier = Modifier.typeCentered(LocalTextStyle.current)
                                )
                            },
                            trailingContent = {
                                val valueStyle = MaterialTheme.typography.bodyMedium
                                Text(if (currentUser?.isAdmin == true) stringResource(R.string.settings_role_admin) else stringResource(R.string.settings_role_user),
                                    style = valueStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.typeCentered(valueStyle))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            // Change Password
            BedrudOutlinedCard {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                    CardSectionHeader(stringResource(R.string.settings_section_security))
                    Spacer(modifier = Modifier.height(Dimens.space12))

                    if (!signInMethod.hasPassword) {
                        Text(stringResource(R.string.settings_password_unavailable, signInMethodLabel(signInMethod)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BedrudPasswordField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = stringResource(R.string.settings_label_currentPassword)
                        )
                        Spacer(modifier = Modifier.height(Dimens.space8))
                        BedrudPasswordField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = stringResource(R.string.settings_label_newPassword)
                        )
                        Spacer(modifier = Modifier.height(Dimens.space8))
                        BedrudPasswordField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = stringResource(R.string.settings_label_confirmNewPassword)
                        )
                        Spacer(modifier = Modifier.height(Dimens.space12))
                        val passwordTooShortMessage =
                            stringResource(R.string.auth_hint_passwordMinLength, PasswordPolicy.MIN_LENGTH)
                        val passwordMismatchMessage = stringResource(R.string.auth_error_passwordMismatch)
                        val passwordChangedMessage = stringResource(R.string.settings_password_changeSuccess)
                        val passwordChangeFailedMessage = stringResource(R.string.settings_password_changeFailed)
                        BedrudButton(
                            text = stringResource(R.string.settings_button_changePassword),
                            onClick = {
                                when {
                                    !PasswordPolicy.meetsMinLength(newPassword) -> scope.launch {
                                        snackbarHostState.showSnackbar(passwordTooShortMessage)
                                    }
                                    newPassword != confirmPassword -> scope.launch {
                                        snackbarHostState.showSnackbar(passwordMismatchMessage)
                                    }
                                    else -> scope.launch {
                                        val api = authApi ?: run {
                                            snackbarHostState.showSnackbar(passwordChangeFailedMessage)
                                            return@launch
                                        }
                                        val changed = apiAction(
                                            passwordChangeFailedMessage,
                                            { snackbarHostState.showSnackbar(it) }
                                        ) {
                                            api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                                        }
                                        if (changed) {
                                            currentPassword = ""
                                            newPassword = ""
                                            confirmPassword = ""
                                            snackbarHostState.showSnackbar(passwordChangedMessage)
                                        }
                                    }
                                }
                            },
                            enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // About
            BedrudOutlinedCard {
                Column {
                    CardSectionHeader(
                        stringResource(R.string.settings_section_about),
                        modifier = Modifier.padding(start = Dimens.cardPadding, top = Dimens.cardPadding, end = Dimens.cardPadding, bottom = Dimens.space8)
                    )

                    val packageInfo = try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (_: Exception) {
                        null
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_label_version),
                                modifier = Modifier.typeCentered(LocalTextStyle.current)
                            )
                        },
                        trailingContent = {
                            val valueStyle = MaterialTheme.typography.bodyMedium
                            Text(
                                packageInfo?.versionName ?: "1.0.0",
                                style = valueStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.typeCentered(valueStyle)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimens.space16),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_label_build),
                                modifier = Modifier.typeCentered(LocalTextStyle.current)
                            )
                        },
                        trailingContent = {
                            val valueStyle = MaterialTheme.typography.bodyMedium
                            Text(
                                packageInfo?.longVersionCode?.toString() ?: "1",
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
}

