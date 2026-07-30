package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.bedrud.app.R
import com.bedrud.app.models.Instance
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.components.BedrudSnackbarHost
import com.bedrud.app.ui.theme.Dimens

/**
 * The shared frame for every auth step — the sign-in hub, the email form, and register: the brand
 * header at a fixed offset (so the mark never jumps between steps), a subtitle, a width-capped
 * centered content column inside a scrollable IME-aware body, and a floating back affordance that
 * overlays the header without reserving vertical space.
 */
@Composable
internal fun AuthScreenScaffold(
    snackbarHostState: SnackbarHostState,
    activeInstance: Instance?,
    subtitle: String,
    onBack: (() -> Unit)?,
    backEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                    // Match the server chooser's brand-mark position so it doesn't jump between steps.
                    Spacer(Modifier.height(Dimens.space56))

                    ServerHeader(
                        displayName = activeInstance?.displayName,
                        serverUrl = activeInstance?.serverURL,
                        iconColorHex = activeInstance?.iconColorHex
                    )

                    Spacer(Modifier.height(Dimens.space8))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(Dimens.space32))

                    content()

                    Spacer(Modifier.height(Dimens.space32))
                }
            }

            // Lightweight back affordance — floats over the header without reserving vertical
            // space, so the brand mark keeps the same position across auth steps.
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    enabled = backEnabled,
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
}

/** Shows [errorMessage] as a snackbar, then calls [onShown] so the caller clears the state. */
@Composable
internal fun AuthErrorSnackbar(
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit,
) {
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onShown()
    }
}
