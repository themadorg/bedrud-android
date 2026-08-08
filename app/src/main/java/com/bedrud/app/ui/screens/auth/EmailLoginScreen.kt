package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.R
import com.bedrud.app.core.auth.PasswordPolicy
import com.bedrud.app.core.auth.isValidEmail
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.models.ForgotPasswordRequest
import com.bedrud.app.models.LoginRequest
import com.bedrud.app.core.api.apiBody
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudPasswordField
import com.bedrud.app.ui.components.BedrudTextField
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Dedicated email + password sign-in form, reached from the sign-in hub. The hub owns passkey,
 * OAuth, and guest sign-in; this screen owns the classic credentials path plus password recovery.
 *
 * Password recovery ("Forgot password?") requests a reset email via the server's
 * `auth/forgot-password` endpoint. The reset link itself is completed on the server's web page —
 * the app only kicks off the email — and the server always answers uniformly whether or not the
 * account exists (no enumeration), so the confirmation is deliberately non-committal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    instanceManager: InstanceManager = koinInject()
) {
    val authApi = instanceManager.authApi.collectAsState().value ?: return
    val authManager = instanceManager.authManager.collectAsState().value ?: return
    val activeInstance = instanceManager.store.activeInstance

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetSheet by remember { mutableStateOf(false) }
    var resetSentEmail by remember { mutableStateOf<String?>(null) }

    val loginFailedMessage = stringResource(R.string.auth_error_loginFailed)
    val genericMessage = stringResource(R.string.auth_error_generic)
    val resetActionLabel = stringResource(R.string.auth_forgot_action)

    val canSubmit = isValidEmail(email) && PasswordPolicy.meetsMinLength(password) && !isLoading

    // Login errors surface as a snackbar. A wrong-password failure additionally offers a "Reset"
    // action, so recovery is right where the user hits the wall — without hiding it from the people
    // who already know they forgot (it also lives as an always-visible link below).
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        val offerReset = message == loginFailedMessage
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (offerReset) resetActionLabel else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            showResetSheet = true
        }
        errorMessage = null
    }

    // Resolve the confirmation via stringResource (not context.getString) so it stays locale-aware.
    val resetSentMessage = resetSentEmail?.let { stringResource(R.string.auth_forgot_sent, it) }
    LaunchedEffect(resetSentMessage) {
        val message = resetSentMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        resetSentEmail = null
    }

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            try {
                val body = apiBody(loginFailedMessage, { errorMessage = it }) {
                    authApi.login(LoginRequest(email = email.trim(), password = password))
                }
                if (body != null) {
                    authManager.saveTokens(body.tokens)
                    authManager.saveUser(body.user)
                    onLoginSuccess()
                }
            } finally {
                isLoading = false
            }
        }
    }

    AuthScreenScaffold(
        snackbarHostState = snackbarHostState,
        activeInstance = activeInstance,
        subtitle = stringResource(R.string.auth_email_subtitle),
        onBack = onBack,
        backEnabled = !isLoading,
    ) {
        BedrudTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = stringResource(R.string.auth_label_email),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            autofill = ContentType.EmailAddress,
            textDirection = TextDirection.Ltr
        )

        Spacer(Modifier.height(Dimens.space12))

        BedrudPasswordField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = stringResource(R.string.auth_label_password),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            autofill = ContentType.Password
        )

        Spacer(Modifier.height(Dimens.space4))

        // Slim text link aligned flush with the field's trailing edge — BedrudButton's
        // ghost padding would inset the label too far from the edge.
        TextButton(
            onClick = { showResetSheet = true },
            enabled = !isLoading,
            contentPadding = PaddingValues(
                horizontal = Dimens.space8,
                vertical = Dimens.space8
            ),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = stringResource(R.string.auth_forgot_link),
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(Dimens.space8))

        BedrudButton(
            text = stringResource(R.string.auth_button_signIn),
            onClick = { submit() },
            variant = BedrudButtonVariant.PRIMARY,
            enabled = canSubmit,
            loading = isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeightLarge)
        )
    }

    if (showResetSheet) {
        ForgotPasswordSheet(
            initialEmail = email,
            onDismiss = { showResetSheet = false },
            onSubmit = { target ->
                try {
                    val response = authApi.forgotPassword(ForgotPasswordRequest(email = target))
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception(genericMessage))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onSent = { target ->
                showResetSheet = false
                resetSentEmail = target
            }
        )
    }
}

/**
 * Bottom sheet that requests a password-reset email. Prefilled with whatever the user already typed
 * on the login form. Validates the email shape locally; on success the sheet closes and the caller
 * shows the uniform confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordSheet(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> Result<Unit>,
    onSent: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericMessage = stringResource(R.string.auth_error_generic)
    val emailValid = isValidEmail(email)
    val canSend = emailValid && !isSending

    fun send() {
        if (!canSend) return
        focusManager.clearFocus()
        val target = email.trim()
        scope.launch {
            isSending = true
            error = null
            onSubmit(target).fold(
                onSuccess = {
                    sheetState.hide()
                    onSent(target)
                },
                onFailure = {
                    error = it.message ?: genericMessage
                    isSending = false
                }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BedrudShapeTokens.sheetTop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = Dimens.space32)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.auth_forgot_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(Dimens.space8))

            Text(
                text = stringResource(R.string.auth_forgot_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Dimens.space24))

            val currentError = error
            BedrudTextField(
                value = email,
                onValueChange = {
                    email = it
                    error = null
                },
                label = stringResource(R.string.auth_label_email),
                isError = currentError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { send() }),
                autofill = ContentType.EmailAddress,
                textDirection = TextDirection.Ltr
            )

            if (currentError != null) {
                Spacer(Modifier.height(Dimens.space8))
                Text(
                    text = currentError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(Dimens.space24))

            BedrudButton(
                text = stringResource(R.string.auth_forgot_submit),
                onClick = { send() },
                variant = BedrudButtonVariant.PRIMARY,
                enabled = canSend,
                loading = isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.buttonHeightLarge)
            )
        }
    }
}
