package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.bedrud.app.R
import com.bedrud.app.core.DevFlags
import com.bedrud.app.core.api.apiBody
import com.bedrud.app.core.auth.OAuthLoginHandler
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.PublicSettingsState
import com.bedrud.app.models.GuestLoginRequest
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudTextField
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Which sign-in action is currently in flight, so only its button shows a spinner. */
private enum class HubAction { PASSKEY, GUEST }

/** M3 disabled-content alpha, used to grey out unavailable OAuth logos. */
private const val DisabledLogoAlpha = 0.38f

/** The OAuth providers the app knows about, in display order (backend ids: google/github/twitter). */
private data class OAuthOption(
    val provider: OAuthLoginHandler.Provider,
    val iconRes: Int,
    val label: String,
    /** true = monochrome mark, tinted to onSurface; false = keep the brand's own colors (Google). */
    val tinted: Boolean
)

private val OAuthOptions = listOf(
    OAuthOption(OAuthLoginHandler.Provider.GOOGLE, R.drawable.ic_oauth_google, "Google", tinted = false),
    OAuthOption(OAuthLoginHandler.Provider.GITHUB, R.drawable.ic_oauth_github, "GitHub", tinted = true),
    OAuthOption(OAuthLoginHandler.Provider.TWITTER, R.drawable.ic_oauth_x, "X", tinted = true)
)

/**
 * Sign-in landing / hub for the active server. Presents the ways in as peer choices — email &
 * password (opens a dedicated form), passkey (one tap), OAuth providers, or continue as a guest
 * (name inline) — plus a sign-up link. Which methods appear and are enabled is driven by the
 * server's public settings ([com.bedrud.app.models.PublicSettings]); a method the server has
 * disabled is shown greyed with a short reason, and OAuth shows only the providers the server
 * configured. On a failed settings fetch everything falls back to enabled so a blip never blocks
 * sign-in. The email/password form lives on its own screen; passkey and guest sign-in happen here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToEmailLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onBack: (() -> Unit)? = null,
    instanceManager: InstanceManager = koinInject()
) {
    val authApi = instanceManager.authApi.collectAsState().value ?: return
    val authManager = instanceManager.authManager.collectAsState().value ?: return
    val passkeyManager = instanceManager.passkeyManager.collectAsState().value ?: return
    val activeInstance = instanceManager.store.activeInstance

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var guestName by rememberSaveable { mutableStateOf("") }
    var loadingAction by remember { mutableStateOf<HubAction?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Public settings come from InstanceManager (fetched on server activation), so the hub renders
    // ready regardless of which route reached it. settings == null means still loading.
    val settingsState by instanceManager.publicSettings.collectAsState()
    val settings = (settingsState as? PublicSettingsState.Loaded)?.settings
    val settingsFailed = settingsState is PublicSettingsState.Failed
    val isBusy = loadingAction != null

    val nameTooShortMessage = stringResource(R.string.auth_error_nameTooShort)
    val passkeyFailedMessage = stringResource(R.string.auth_error_generic)
    val guestFailedMessage = stringResource(R.string.auth_error_guestFailed)

    // Optimistic while loading, permissive on failure: a method is enabled unless the server said off.
    val passkeyEnabled = settings?.passkeysEnabled ?: true
    val guestEnabled = settings?.guestLoginEnabled ?: true
    val registrationEnabled = settings?.registrationEnabled ?: true
    // OAuth: known configured set once loaded; null while unknown (loading/failed).
    val configuredProviders = settings?.oauthProviders?.map { it.lowercase() }?.toSet()
    val serverUrl = activeInstance?.serverURL
    // Providers the server actually advertises (non-empty). Null while unknown or none configured.
    val realProviders = configuredProviders?.takeIf { it.isNotEmpty() }
    // The settings fetch has finished — resolved to a value or given up (bounded by its timeout).
    val settingsResolved = settings != null || settingsFailed
    // Dev/debug builds preview the row (providers shown disabled/greyed) ONLY once we know the server
    // configures none, so a server that DOES support OAuth still shows its real, tappable providers
    // rather than a misleading greyed state while loading. Release stays server-driven (hidden here).
    val oauthDevPreview = DevFlags.hintsEnabled && settingsResolved && realProviders == null
    // Show the row once the server's providers are known, in dev preview, or as a failure fallback.
    // Hidden while the settings call is still in flight, and when there's no server URL to hit.
    val showOAuthRow = serverUrl != null && (realProviders != null || oauthDevPreview || settingsFailed)

    AuthErrorSnackbar(errorMessage, snackbarHostState) { errorMessage = null }

    fun signInWithPasskey() {
        if (isBusy) return
        scope.launch {
            loadingAction = HubAction.PASSKEY
            val result = passkeyManager.loginWithPasskey(context)
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = { errorMessage = it.message ?: passkeyFailedMessage }
            )
            loadingAction = null
        }
    }

    fun continueAsGuest() {
        if (isBusy) return
        focusManager.clearFocus()
        val trimmed = guestName.trim()
        if (trimmed.length < 2) {
            errorMessage = nameTooShortMessage
            return
        }
        scope.launch {
            loadingAction = HubAction.GUEST
            try {
                val body = apiBody(guestFailedMessage, { errorMessage = it }) {
                    authApi.guestLogin(GuestLoginRequest(trimmed))
                }
                if (body != null) {
                    authManager.saveTokens(body.tokens)
                    authManager.saveUser(body.user)
                    onLoginSuccess()
                }
            } finally {
                loadingAction = null
            }
        }
    }

    AuthScreenScaffold(
        snackbarHostState = snackbarHostState,
        activeInstance = activeInstance,
        subtitle = stringResource(R.string.auth_subtitle_hubChoose),
        onBack = onBack,
        backEnabled = !isBusy,
    ) {
        // ── Account sign-in ──
        BedrudButton(
            text = stringResource(R.string.auth_button_signInWithEmail),
            onClick = onNavigateToEmailLogin,
            variant = BedrudButtonVariant.PRIMARY,
            enabled = !isBusy,
            leadingIcon = {
                Icon(
                    Icons.Filled.Mail,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeightLarge)
        )
        Spacer(Modifier.height(Dimens.space12))
        BedrudButton(
            text = stringResource(R.string.auth_button_signInWithPasskey),
            onClick = { signInWithPasskey() },
            variant = BedrudButtonVariant.OUTLINE,
            enabled = !isBusy && passkeyEnabled,
            loading = loadingAction == HubAction.PASSKEY,
            leadingIcon = {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeightLarge)
        )
        if (!passkeyEnabled) MethodDisabledHint()

        // ── OAuth providers (compact logo row) ──
        if (showOAuthRow) {
            Spacer(Modifier.height(Dimens.space24))
            Text(
                text = stringResource(R.string.auth_divider_orContinueWith),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.space12))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space16)) {
                OAuthOptions.forEach { option ->
                    // Real providers → only those enabled. Dev preview → shown disabled
                    // (layout review only). Failure fallback → enabled so users can try.
                    val providerEnabled = when {
                        realProviders != null -> option.provider.id in realProviders
                        oauthDevPreview -> false
                        else -> true
                    }
                    OAuthProviderButton(
                        option = option,
                        enabled = providerEnabled && !isBusy && serverUrl != null,
                        onClick = {
                            serverUrl?.let {
                                OAuthLoginHandler.launch(context, it, option.provider)
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.space24))
        OrDivider()
        Spacer(Modifier.height(Dimens.space24))

        // ── Guest sign-in ──
        BedrudTextField(
            value = guestName,
            onValueChange = {
                guestName = it
                errorMessage = null
            },
            label = stringResource(R.string.auth_label_displayName),
            placeholder = stringResource(R.string.auth_placeholder_displayName),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { continueAsGuest() }),
            enabled = !isBusy && guestEnabled
        )
        Spacer(Modifier.height(Dimens.space12))
        BedrudButton(
            text = stringResource(R.string.auth_button_continueAsGuest),
            onClick = { continueAsGuest() },
            variant = BedrudButtonVariant.TONAL,
            enabled = !isBusy && guestEnabled && guestName.trim().length >= 2,
            loading = loadingAction == HubAction.GUEST,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeightLarge)
        )
        if (!guestEnabled) MethodDisabledHint()

        Spacer(Modifier.height(Dimens.space24))

        // ── Sign-up ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.auth_prompt_noAccount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onNavigateToRegister,
                enabled = !isBusy && registrationEnabled
            ) {
                Text(
                    text = stringResource(R.string.auth_button_signUp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** A compact, outlined circular button carrying an OAuth provider's brand logo. */
@Composable
private fun OAuthProviderButton(
    option: OAuthOption,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentDescription = stringResource(R.string.auth_oauth_continueWithFormat, option.label)
    val logoModifier = Modifier
        .size(Dimens.iconMd)
        .alpha(if (enabled) 1f else DisabledLogoAlpha)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = BedrudShapeTokens.pill,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            Dimens.borderThin,
            if (enabled) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.size(Dimens.buttonHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (option.tinted) {
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = logoModifier
                )
            } else {
                Image(
                    painter = painterResource(option.iconRes),
                    contentDescription = contentDescription,
                    modifier = logoModifier
                )
            }
        }
    }
}

/** Short caption shown under a sign-in method the server has turned off. */
@Composable
private fun MethodDisabledHint() {
    Spacer(Modifier.height(Dimens.space4))
    Text(
        text = stringResource(R.string.auth_hint_methodDisabled),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.auth_divider_or),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.space16)
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
