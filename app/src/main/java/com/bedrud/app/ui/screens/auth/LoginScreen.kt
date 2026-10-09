package com.bedrud.app.ui.screens.auth

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.bedrud.app.R
import com.bedrud.app.core.DevFlags
import com.bedrud.app.core.api.apiBody
import com.bedrud.app.core.auth.OAuthLoginHandler
import com.bedrud.app.core.auth.SignInNoticeRelay
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.PublicSettingsState
import com.bedrud.app.models.GuestLoginRequest
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudTextField
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Alpha
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.inkCentered
import com.bedrud.app.ui.theme.typeCentered
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Which sign-in action is currently in flight, so only its button shows a spinner. */
private enum class HubAction { PASSKEY, GUEST }

/** The shortest guest name accepted, once surrounding spaces are trimmed. */
private const val MinGuestNameLength = 2

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
 * The hub's subtitle. It offers continuing as a guest only on a server that allows it: on one that
 * does not, the words above the buttons would promise what the greyed guest button below refuses.
 */
@StringRes
internal fun hubSubtitle(guestAllowed: Boolean): Int =
    if (guestAllowed) R.string.auth_subtitle_hubChoose else R.string.auth_subtitle_hubSignIn

/**
 * One sign-in method's full-width button. When the server has turned the method off
 * ([serverAllows] false) it is greyed and its own label says so, [offLabel] in place of [label],
 * rather than keeping the action's name and explaining it in a caption underneath. [enabled] is
 * everything else that gates it: another sign-in in flight, a guest name still too short.
 */
@Composable
internal fun SignInMethodButton(
    label: String,
    offLabel: String,
    serverAllows: Boolean,
    enabled: Boolean,
    loading: Boolean,
    variant: BedrudButtonVariant,
    onClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    BedrudButton(
        text = if (serverAllows) label else offLabel,
        onClick = onClick,
        variant = variant,
        enabled = enabled && serverAllows,
        loading = loading,
        leadingIcon = leadingIcon,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.buttonHeightLarge)
    )
}

/**
 * The guest way in: a name field and the button that joins under that name. [enabled] is false
 * while another sign-in is in flight; the button also waits for a name of [MinGuestNameLength].
 * On a server that turns guest sign-in off only the button stays, saying so: a greyed name field
 * would still look like somewhere to type a name nothing can use.
 */
@Composable
internal fun GuestSignIn(
    name: String,
    onNameChange: (String) -> Unit,
    serverAllows: Boolean,
    enabled: Boolean,
    loading: Boolean,
    onContinue: () -> Unit,
) {
    if (serverAllows) {
        BedrudTextField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.auth_label_displayName),
            placeholder = stringResource(R.string.auth_placeholder_displayName),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onContinue() }),
            enabled = enabled
        )
        Spacer(Modifier.height(Dimens.space12))
    }
    SignInMethodButton(
        label = stringResource(R.string.auth_button_continueAsGuest),
        offLabel = stringResource(R.string.auth_button_guestOff),
        serverAllows = serverAllows,
        enabled = enabled && name.trim().length >= MinGuestNameLength,
        loading = loading,
        variant = BedrudButtonVariant.TONAL,
        onClick = onContinue,
    )
}

/**
 * Sign-in landing / hub for the active server. Presents the ways in as peer choices — email &
 * password (opens a dedicated form), passkey (one tap), OAuth providers, or continue as a guest
 * (name inline) — plus a sign-up link. Which methods appear and are enabled is driven by the
 * server's public settings ([com.bedrud.app.models.PublicSettings]); a method the server has
 * disabled is shown greyed, its button saying it is off (guest sign-in also loses its name field),
 * and OAuth shows only the providers the server configured. On a failed settings fetch everything
 * falls back to enabled so a blip never blocks sign-in. The email/password form lives on its own
 * screen; passkey and guest sign-in happen here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToEmailLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onBack: (() -> Unit)? = null,
    instanceManager: InstanceManager = koinInject(),
    signInNoticeRelay: SignInNoticeRelay = koinInject(),
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

    // The length is passed as a number, so it is written in the app language's own digits.
    val nameTooShortMessage = pluralStringResource(
        R.plurals.auth_error_nameTooShort,
        MinGuestNameLength,
        MinGuestNameLength,
    )
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

    // Why the app signed the user out, said here because the screen that did it is gone — see
    // SignInNoticeRelay.
    val signInNotice by signInNoticeRelay.message.collectAsState()
    LaunchedEffect(signInNotice) {
        val notice = signInNotice ?: return@LaunchedEffect
        // Consumed only once it has been read out: clearing it first would change this effect's key
        // mid-message, cancelling the very snackbar it was showing.
        snackbarHostState.showSnackbar(notice)
        signInNoticeRelay.consume()
    }

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
        if (trimmed.length < MinGuestNameLength) {
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
        subtitle = stringResource(hubSubtitle(guestAllowed = guestEnabled)),
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
                .heightIn(min = Dimens.buttonHeightLarge)
        )
        Spacer(Modifier.height(Dimens.space12))
        SignInMethodButton(
            label = stringResource(R.string.auth_button_signInWithPasskey),
            offLabel = stringResource(R.string.auth_button_passkeyOff),
            serverAllows = passkeyEnabled,
            enabled = !isBusy,
            loading = loadingAction == HubAction.PASSKEY,
            variant = BedrudButtonVariant.OUTLINE,
            onClick = { signInWithPasskey() },
            leadingIcon = {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            },
        )

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
                    // serverUrl is non-null in here: showOAuthRow already requires it.
                    OAuthProviderButton(
                        option = option,
                        enabled = providerEnabled && !isBusy,
                        onClick = {
                            OAuthLoginHandler.launch(context, serverUrl, option.provider)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.space24))
        OrDivider()
        Spacer(Modifier.height(Dimens.space24))

        // ── Guest sign-in ──
        GuestSignIn(
            name = guestName,
            onNameChange = {
                guestName = it
                errorMessage = null
            },
            serverAllows = guestEnabled,
            enabled = !isBusy,
            loading = loadingAction == HubAction.GUEST,
            onContinue = { continueAsGuest() },
        )

        Spacer(Modifier.height(Dimens.space24))

        // ── Sign-up ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // The prompt and the button's label are corrected together: correcting only the label
            // moved it below the prompt it completes.
            val promptStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = stringResource(R.string.auth_prompt_noAccount),
                style = promptStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.typeCentered(promptStyle)
            )
            // A plain text button, not BedrudButton's ghost: it finishes the prompt beside it, and
            // the ghost's wider padding would push it away from the words it completes.
            TextButton(
                onClick = onNavigateToRegister,
                enabled = !isBusy && registrationEnabled,
                shape = BedrudShapeTokens.button,
            ) {
                val signUpStyle = MaterialTheme.typography.labelLarge
                Text(
                    text = stringResource(R.string.auth_button_signUp),
                    style = signUpStyle,
                    modifier = Modifier.typeCentered(signUpStyle),
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
        .alpha(if (enabled) 1f else Alpha.disabled)
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

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        // One short word alone between two rules, so its own ink is measured: a capital's centre
        // would put a lowercase "or" visibly below the line.
        val dividerWord = stringResource(R.string.auth_divider_or)
        val dividerStyle = MaterialTheme.typography.bodySmall
        Text(
            text = dividerWord,
            style = dividerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Dimens.space16)
                .inkCentered(dividerWord, dividerStyle)
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
