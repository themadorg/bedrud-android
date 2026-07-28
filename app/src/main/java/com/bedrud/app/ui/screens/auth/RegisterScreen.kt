package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.R
import com.bedrud.app.core.api.LoginOutcome
import com.bedrud.app.core.api.RegisterOutcome
import com.bedrud.app.core.api.parseRegisterResponse
import com.bedrud.app.core.api.performLogin
import com.bedrud.app.core.auth.PasswordPolicy
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.models.RegisterRequest
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.components.BedrudSnackbarHost
import com.bedrud.app.ui.components.autofillType
import com.bedrud.app.ui.components.bringIntoViewOnFocus
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Account creation form, reached from the sign-in hub's "No account yet? Sign up" prompt.
 *
 * Mirrors [EmailLoginScreen]'s layout — the shared [ServerHeader] at the same vertical position, a
 * floating back affordance, token-based fields — so the brand mark never jumps between auth steps.
 * On success it registers and immediately signs the new account in (via [performLogin]); if the
 * server requires email verification, it surfaces the notice and hands back to the sign-in hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    instanceManager: InstanceManager = koinInject()
) {
    val authApi = instanceManager.authApi.collectAsState().value ?: return
    val authManager = instanceManager.authManager.collectAsState().value ?: return
    val activeInstance = instanceManager.store.activeInstance

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val genericMessage = stringResource(R.string.auth_error_generic)

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordLongEnough = password.length >= PasswordPolicy.MIN_LENGTH
    val passwordsMatch = password == confirmPassword
    val nameValid = displayName.trim().isNotEmpty()

    // Fields only flag an error once the user has typed into them, so a pristine form looks calm.
    val emailInErrorState = email.isNotBlank() && !emailValid
    val passwordTooShort = password.isNotEmpty() && !passwordLongEnough
    val confirmMismatch = confirmPassword.isNotEmpty() && !passwordsMatch

    val canSubmit = nameValid &&
        emailValid &&
        passwordLongEnough &&
        confirmPassword.isNotEmpty() &&
        passwordsMatch &&
        !isLoading

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        errorMessage = null
    }

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            try {
                val trimmedEmail = email.trim()
                val trimmedName = displayName.trim()
                val registerResponse = authApi.register(
                    RegisterRequest(email = trimmedEmail, password = password, name = trimmedName)
                )
                when (val registerOutcome = parseRegisterResponse(registerResponse)) {
                    is RegisterOutcome.Failed -> errorMessage = registerOutcome.message
                    is RegisterOutcome.VerificationRequired -> {
                        errorMessage = registerOutcome.message
                        onNavigateToLogin()
                    }
                    is RegisterOutcome.AccountCreated -> {
                        when (
                            val loginOutcome = performLogin(
                                authApi = authApi,
                                authManager = authManager,
                                email = trimmedEmail,
                                password = password
                            )
                        ) {
                            is LoginOutcome.Success -> onRegisterSuccess()
                            is LoginOutcome.VerificationRequired -> {
                                errorMessage = loginOutcome.message
                                onNavigateToLogin()
                            }
                            is LoginOutcome.Failed -> errorMessage = loginOutcome.message
                        }
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: genericMessage
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = BedrudScaffoldContentInsets,
        snackbarHost = { BedrudSnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = Dimens.maxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Match the hub/server-chooser brand-mark position so it doesn't jump between steps.
                    Spacer(Modifier.height(Dimens.space56))

                    ServerHeader(
                        displayName = activeInstance?.displayName,
                        serverUrl = activeInstance?.serverURL,
                        iconColorHex = activeInstance?.iconColorHex
                    )

                    Spacer(Modifier.height(Dimens.space8))
                    Text(
                        text = stringResource(R.string.auth_register_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(Dimens.space32))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.auth_label_displayName)) },
                        singleLine = true,
                        shape = BedrudShapeTokens.field,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .autofillType(ContentType.PersonFullName),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
                    )

                    Spacer(Modifier.height(Dimens.space12))

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.auth_label_email)) },
                        singleLine = true,
                        isError = emailInErrorState,
                        supportingText = if (emailInErrorState) {
                            { Text(stringResource(R.string.auth_error_emailInvalid)) }
                        } else {
                            null
                        },
                        shape = BedrudShapeTokens.field,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .autofillType(ContentType.EmailAddress),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                    )

                    Spacer(Modifier.height(Dimens.space12))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.auth_label_password)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) {
                                        stringResource(R.string.auth_password_toggle_hide)
                                    } else {
                                        stringResource(R.string.auth_password_toggle_show)
                                    }
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        singleLine = true,
                        isError = passwordTooShort,
                        // Always-on helper doubles as the too-short error (it turns red via isError).
                        supportingText = { Text(stringResource(R.string.auth_hint_passwordMinLength, PasswordPolicy.MIN_LENGTH)) },
                        shape = BedrudShapeTokens.field,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .autofillType(ContentType.NewPassword),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                    )

                    Spacer(Modifier.height(Dimens.space12))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.auth_label_confirmPassword)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        singleLine = true,
                        isError = confirmMismatch,
                        supportingText = if (confirmMismatch) {
                            { Text(stringResource(R.string.auth_error_passwordMismatch)) }
                        } else {
                            null
                        },
                        shape = BedrudShapeTokens.field,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .autofillType(ContentType.NewPassword),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                    )

                    Spacer(Modifier.height(Dimens.space24))

                    BedrudButton(
                        text = stringResource(R.string.auth_title_createAccount),
                        onClick = { submit() },
                        variant = BedrudButtonVariant.PRIMARY,
                        enabled = canSubmit,
                        loading = isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.buttonHeightLarge)
                    )

                    Spacer(Modifier.height(Dimens.space16))

                    TextButton(
                        onClick = onNavigateToLogin,
                        enabled = !isLoading
                    ) {
                        Text(
                            text = stringResource(R.string.auth_link_alreadyHaveAccount),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.height(Dimens.space32))
                }
            }

            // Lightweight back affordance — floats over the header without reserving vertical space,
            // so the brand mark keeps the same position as the hub and server chooser.
            IconButton(
                onClick = onNavigateToLogin,
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_action_back)
                )
            }
        }
    }
}
