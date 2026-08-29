package com.bedrud.app.ui.screens.instance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.BuildConfig
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.ServerUrlCanonicalizer
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.components.BedrudSnackbarHost
import com.bedrud.app.ui.components.DevOnly
import com.bedrud.app.ui.theme.Alpha
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.theme.bedrudColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    val keyboardController = LocalSoftwareKeyboardController.current

    val instances by instanceManager.store.instances.collectAsState()

    val defaultUrl = remember { ServerUrlCanonicalizer.canonicalize(BuildConfig.DEFAULT_SERVER_HOST) }
    // The official server can only be added once -- if it's already among the user's instances,
    // selecting it again here would just re-trigger submit()'s switchTo(existing.id) path. Disable
    // it instead so "Add Server" reliably means "add a *new* one" rather than sometimes silently
    // switching back to a server the user already has.
    val isDefaultAdded = defaultUrl != null && instances.any { it.serverURL.equals(defaultUrl, ignoreCase = true) }

    var choice by rememberSaveable {
        mutableStateOf(if (isDefaultAdded) ServerChoice.CUSTOM else ServerChoice.DEFAULT)
    }
    var customInput by rememberSaveable { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val customFocusRequester = remember { FocusRequester() }

    val resolvedCustom = ServerUrlCanonicalizer.canonicalize(customInput)
    val resolvedUrl = if (choice == ServerChoice.DEFAULT) defaultUrl else resolvedCustom
    val isInsecure = choice == ServerChoice.CUSTOM && resolvedCustom?.startsWith("http://") == true
    val canContinue = !isChecking && resolvedUrl != null

    val defaultName = stringResource(R.string.instance_default_displayName)
    val unreachableMessage = stringResource(R.string.instance_error_unreachable)

    // Selecting "your own server" focuses the field and raises the keyboard immediately.
    LaunchedEffect(choice) {
        if (choice == ServerChoice.CUSTOM) customFocusRequester.requestFocus()
    }

    // Pure on-device decode (ZXing) -- no Play Services dependency, so it can't fail the way
    // Play Services' own code scanner did (its module needs to be fetched over network on first
    // use, which proved unreliable on restricted networks). Requires CAMERA permission, which
    // this app already holds for calls; ZXing's own capture activity requests it if missing.
    //
    // STANDARD/REQUIRED FOLLOW-UP WORK (not in this Android-only repo): the QR code itself has to
    // come from somewhere. This decodes whatever a QR code contains (expected to be the server's
    // bare address or a full URL, either of which ServerUrlCanonicalizer already resolves) -- but
    // no self-hosted Bedrud server currently generates or displays such a code anywhere. For this
    // to be a true "point your camera at the admin's screen" flow, the server's admin panel needs
    // a page that renders a QR code encoding its own address. That's backend/admin-UI work,
    // tracked outside this repo -- see AGENTS.md.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            customInput = scanned.filterNot(Char::isWhitespace)
            errorMessage = null
        }
    }

    fun scanQrCode() {
        keyboardController?.hide()
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }

    fun submit() {
        if (isChecking) return
        keyboardController?.hide()
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
                // Wait for the server's public settings so the sign-in hub renders ready (bounded).
                instanceManager.awaitPublicSettings()
                onInstanceAdded()
            } catch (e: Exception) {
                errorMessage = unreachableMessage
            } finally {
                isChecking = false
            }
        }
    }

    Scaffold(
        // Standard insets (IME excluded) — the keyboard is allowed to cover the Continue button.
        // The scrollable content below adds imePadding so the focused input still scrolls into view.
        contentWindowInsets = BedrudScaffoldContentInsets,
        snackbarHost = { BedrudSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable content: header + the two compact choice cards. The keyboard is allowed to
            // cover the Continue button below; the input card sits high enough to stay visible above
            // the keyboard, and verticalScroll covers the rare short-screen case.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Dimens.space56))
                BrandHeader(wordmark = defaultName)
                Spacer(Modifier.height(Dimens.space40))

                Column(
                    modifier = Modifier
                        .widthIn(max = Dimens.maxContentWidth)
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space16)
                ) {
                    ServerChoiceCard(
                        selected = choice == ServerChoice.DEFAULT,
                        onSelect = {
                            choice = ServerChoice.DEFAULT
                            errorMessage = null
                        },
                        title = stringResource(R.string.instance_choice_default_title),
                        badge = stringResource(
                            if (isDefaultAdded) R.string.instance_choice_default_addedTag
                            else R.string.instance_choice_default_tag
                        ),
                        enabled = !isDefaultAdded,
                    ) { selected ->
                        Text(
                            text = displayUrl(defaultUrl ?: BuildConfig.DEFAULT_SERVER_HOST),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ServerChoiceCard(
                        selected = choice == ServerChoice.CUSTOM,
                        onSelect = {
                            choice = ServerChoice.CUSTOM
                            errorMessage = null
                        },
                        title = stringResource(R.string.instance_choice_custom_title),
                        badge = null,
                    ) { selected ->
                        CustomServerField(
                            value = customInput,
                            onValueChange = {
                                customInput = it.filterNot(Char::isWhitespace)
                                errorMessage = null
                            },
                            enabled = selected,
                            focusRequester = customFocusRequester,
                            // Keyboard action key: dismiss keyboard + run the primary action.
                            onSubmit = {
                                keyboardController?.hide()
                                if (canContinue) submit()
                            },
                            onScanQrCode = ::scanQrCode
                        )
                        AnimatedVisibility(visible = selected && isInsecure) {
                            InsecureNote()
                        }
                    }
                }

                // Inline error sits directly under the cards.
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
            }

            // Pinned action — stays above the keyboard thanks to the IME inset above.
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.maxContentWidth)
                    .fillMaxWidth()
                    .padding(top = Dimens.space16, bottom = Dimens.space16),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BedrudButton(
                    text = stringResource(R.string.instance_button_continue),
                    onClick = { submit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.buttonHeightLarge),
                    enabled = resolvedUrl != null,
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
private fun BrandHeader(wordmark: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(Dimens.brandMark)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            // The app's logo mark (launcher glyph), tinted into the rose brand avatar.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                modifier = Modifier.size(Dimens.brandMark * 1.9f)
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
    badge: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        // A disabled card's own colors are indistinguishable from an unselected-but-selectable
        // one, so without this the "already added" state reads as merely deselected. Dimming the
        // whole Surface fades border, title, address and badge together in one pass.
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else Alpha.disabled)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        shape = BedrudShapeTokens.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.serverCardMinHeight)
                .padding(Dimens.cardPadding)
        ) {
            // Radio pinned to the top-end corner; the label + value block is centered vertically.
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = Dimens.space32)
                    .align(Alignment.CenterStart)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Dimens.space8)
                    )
                    if (badge != null) {
                        CardBadge(badge)
                    }
                }
                Spacer(Modifier.height(Dimens.space8))
                content(selected)
            }
        }
    }
}

@Composable
private fun CardBadge(text: String) {
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
    onSubmit: () -> Unit,
    onScanQrCode: () -> Unit
) {
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    // Leading icon (matching the dashboard quick-join field's leading search icon) rather than
    // trailing: a trailing icon ends up floating far from short input text since the field itself
    // needs weight(1f) to stay fully tappable, which reads as misplaced/disconnected.
    Row(verticalAlignment = Alignment.CenterVertically) {
        // IconButton's 48dp touch target centers the 24dp glyph, insetting it (48-24)/2 = 12dp on
        // each side. Left-shifting the whole thing by that inset lines the glyph up flush with
        // "Your own server" above it, but leaves the *reported* width at a full 48dp -- so the text
        // field after it would still start 24dp from the glyph, reading as an oversized gap. This
        // custom layout keeps the full 48dp touch target (for accessibility) but reports only
        // iconMd + space8 of width upstream, so the field starts a normal icon-to-text gap away
        // from the glyph instead of from the touch target's far edge.
        IconButton(
            onClick = onScanQrCode,
            enabled = enabled,
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val inset = ((Dimens.minTouchTarget - Dimens.iconMd) / 2).roundToPx()
                val reportedWidth = (Dimens.iconMd + Dimens.space8).roundToPx()
                layout(reportedWidth, placeable.height) {
                    placeable.placeRelative(-inset, 0)
                }
            },
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = stringResource(R.string.instance_contentDescription_scanQr),
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outlineVariant
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.instance_placeholder_serverAddress),
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
            tint = MaterialTheme.bedrudColors.warning,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Text(
            text = stringResource(R.string.instance_note_insecure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.bedrudColors.warning
        )
    }
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
