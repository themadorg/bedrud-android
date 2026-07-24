package com.bedrud.app.ui.screens.instance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.BuildConfig
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.DevOnly
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class ServerChoice { DEFAULT, CUSTOM }

/**
 * First-run / add-server screen. The user either takes the recommended default server or points
 * the app at their own, then continues. `Continue` health-checks the chosen server, stores +
 * activates it, and hands off to sign-in. Server management (switch/remove) lives in the
 * instance list/switcher, not here.
 */
@Composable
fun AddInstanceScreen(
    onInstanceAdded: () -> Unit,
    instanceManager: InstanceManager = koinInject()
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val instances by instanceManager.store.instances.collectAsState()

    var choice by rememberSaveable { mutableStateOf(ServerChoice.DEFAULT) }
    var customInput by rememberSaveable { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val customFocusRequester = remember { FocusRequester() }

    val defaultUrl = remember { canonicalizeServerUrl(BuildConfig.DEFAULT_SERVER_URL) }
    val resolvedCustom = canonicalizeServerUrl(customInput)
    val resolvedUrl = if (choice == ServerChoice.DEFAULT) defaultUrl else resolvedCustom
    val isInsecure = choice == ServerChoice.CUSTOM && resolvedCustom?.startsWith("http://") == true
    val canContinue = !isChecking && resolvedUrl != null

    val defaultName = stringResource(R.string.instance_default_displayName)
    val unreachableMessage = stringResource(R.string.instance_error_unreachable)

    // Selecting "your own server" focuses the field and raises the keyboard immediately.
    LaunchedEffect(choice) {
        if (choice == ServerChoice.CUSTOM) customFocusRequester.requestFocus()
    }

    fun submit() {
        val url = resolvedUrl ?: return
        scope.launch {
            isChecking = true
            errorMessage = null
            try {
                val existing = instances.firstOrNull { it.serverURL.equals(url, ignoreCase = true) }
                if (existing != null) {
                    instanceManager.switchTo(existing.id)
                } else {
                    val name = if (choice == ServerChoice.DEFAULT) defaultName else deriveDisplayName(url)
                    instanceManager.addInstance(url, name)
                }
                onInstanceAdded()
            } catch (e: Exception) {
                errorMessage = unreachableMessage
            } finally {
                isChecking = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = BedrudScaffoldContentInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Dimens.space56))
                BrandHeader(monogram = defaultName.take(1).uppercase(), wordmark = defaultName)
                Spacer(Modifier.height(Dimens.space40))

                Column(
                    modifier = Modifier
                        .widthIn(max = Dimens.maxContentWidth)
                        .fillMaxWidth()
                        .selectableGroup()
                ) {
                    ServerChoiceCard(
                        selected = choice == ServerChoice.DEFAULT,
                        onSelect = {
                            choice = ServerChoice.DEFAULT
                            errorMessage = null
                        },
                        title = stringResource(R.string.instance_choice_default_title),
                        tag = stringResource(R.string.instance_choice_default_tag),
                    ) { selected ->
                        Text(
                            text = displayUrl(defaultUrl ?: BuildConfig.DEFAULT_SERVER_URL),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(Dimens.space16))

                    ServerChoiceCard(
                        selected = choice == ServerChoice.CUSTOM,
                        onSelect = {
                            choice = ServerChoice.CUSTOM
                            errorMessage = null
                        },
                        title = stringResource(R.string.instance_choice_custom_title),
                        tag = null,
                    ) { selected ->
                        CustomServerField(
                            value = customInput,
                            onValueChange = {
                                customInput = it
                                errorMessage = null
                            },
                            enabled = selected,
                            focusRequester = customFocusRequester,
                            onSubmit = { if (canContinue) submit() }
                        )
                        AnimatedVisibility(visible = selected && isInsecure) {
                            InsecureNote()
                        }
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = Dimens.maxContentWidth)
                            .fillMaxWidth()
                            .padding(top = Dimens.space12, start = Dimens.space4, end = Dimens.space4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space8)
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimens.iconSm)
                        )
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.space24))
            }

            // Pinned action
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(top = Dimens.space8, bottom = Dimens.space16),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BedrudButton(
                    text = stringResource(R.string.instance_button_continue),
                    onClick = { submit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.buttonHeightLarge),
                    enabled = canContinue,
                    loading = isChecking
                )
                DevOnly {
                    Spacer(Modifier.height(Dimens.space8))
                    Text(
                        text = "dev • ${BuildConfig.VERSION_NAME} • ${resolvedUrl ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(monogram: String, wordmark: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(Dimens.brandMark)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(Dimens.space16))
        Text(
            text = wordmark,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Dimens.space4))
        Text(
            text = stringResource(R.string.instance_subtitle_connectToServer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServerChoiceCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    tag: String?,
    content: @Composable (selected: Boolean) -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(Motion.durationMedium, easing = Motion.standardEasing),
        label = "cardBorder"
    )
    val borderWidth = if (selected) Dimens.borderStrong else Dimens.borderThin

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        shape = BedrudShapeTokens.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Dimens.space8)
                )
                if (tag != null) {
                    RecommendedTag(tag)
                }
                Spacer(Modifier.weight(1f))
                RadioButton(selected = selected, onClick = null)
            }
            Spacer(Modifier.height(Dimens.space8))
            content(selected)
        }
    }
}

@Composable
private fun RecommendedTag(text: String) {
    Surface(
        shape = BedrudShapeTokens.pill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Dimens.space8, vertical = Dimens.space2)
        )
    }
}

@Composable
private fun CustomServerField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit
) {
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.instance_placeholder_customServer),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                textDirection = TextDirection.Ltr,
                color = textColor
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun InsecureNote() {
    Row(
        modifier = Modifier.padding(top = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8)
    ) {
        Icon(
            Icons.Rounded.LockOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Text(
            text = stringResource(R.string.instance_note_insecure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Normalizes user/host input to a canonical `scheme://host/` URL. Defaults to https; honors an
 * explicit http:// (for local/dev servers). Returns null for blank input.
 */
private fun canonicalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val scheme = if (trimmed.startsWith("http://", ignoreCase = true)) "http" else "https"
    var rest = trimmed
    listOf("https://", "http://").forEach { prefix ->
        if (rest.startsWith(prefix, ignoreCase = true)) rest = rest.substring(prefix.length)
    }
    rest = rest.trim().trimEnd('/')
    if (rest.isEmpty()) return null
    return "$scheme://$rest/"
}

/** Human-friendly name derived from a canonical URL — the host (path/scheme stripped). */
private fun deriveDisplayName(canonicalUrl: String): String {
    val hostAndPath = canonicalUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    return hostAndPath.substringBefore('/').ifBlank { hostAndPath }
}

/** Strips the trailing slash for display so the URL reads cleanly in a card. */
private fun displayUrl(canonicalUrl: String): String = canonicalUrl.trimEnd('/')
