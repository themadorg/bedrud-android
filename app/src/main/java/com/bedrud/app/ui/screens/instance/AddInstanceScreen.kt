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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bedrud.app.BuildConfig
import com.bedrud.app.R
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.ServerUrlCanonicalizer
import com.bedrud.app.ui.components.BedrudBadge
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.components.BedrudSnackbarHost
import com.bedrud.app.ui.components.DevOnly
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.theme.bedrudColors
import com.bedrud.app.ui.theme.typeCentered
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
    // Whether the official server is already among the user's instances. It only labels the card
    // with the "Added" badge -- it must never disable it. This screen answers "which server?" for
    // two different questions: adding a new one from the instance list, and choosing the one to
    // sign in to after backing out of the sign-in hub. Refusing an added server served the first
    // and stranded the second, with no live control on screen and Continue permanently greyed
    // (#102). Continuing on a server that is already stored is not a no-op: submit() switches to
    // it and hands off to sign-in, which is exactly what the signed-out user came here for.
    val isDefaultAdded = defaultUrl != null && instances.any { it.serverURL.equals(defaultUrl, ignoreCase = true) }

    // Always starts on the official server. It used to start on CUSTOM whenever the default was
    // added -- which, with the field empty, opened the screen with Continue disabled and the
    // keyboard already up, presenting "type your own server" to a user who had chosen nothing.
    var choice by rememberSaveable { mutableStateOf(ServerChoice.DEFAULT) }
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
                    OfficialServerCard(
                        selected = choice == ServerChoice.DEFAULT,
                        onSelect = {
                            choice = ServerChoice.DEFAULT
                            errorMessage = null
                        },
                        badge = stringResource(
                            if (isDefaultAdded) R.string.instance_choice_default_addedTag
                            else R.string.instance_choice_default_tag
                        ),
                        address = displayUrl(defaultUrl ?: BuildConfig.DEFAULT_SERVER_HOST),
                    )

                    CustomServerCard(
                        selected = choice == ServerChoice.CUSTOM,
                        onSelect = {
                            choice = ServerChoice.CUSTOM
                            errorMessage = null
                        },
                        value = customInput,
                        onValueChange = {
                            customInput = it.filterNot(Char::isWhitespace)
                            errorMessage = null
                        },
                        focusRequester = customFocusRequester,
                        // Keyboard action key: dismiss keyboard + run the primary action.
                        onSubmit = {
                            keyboardController?.hide()
                            if (canContinue) submit()
                        },
                        onScanQrCode = ::scanQrCode,
                        isInsecure = isInsecure,
                    )
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
                        val errorStyle = MaterialTheme.typography.bodySmall
                        Text(
                            text = errorMessage ?: "",
                            style = errorStyle,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.typeCentered(errorStyle)
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
                        .heightIn(min = Dimens.buttonHeightLarge),
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

/** A size between the type scale's title steps, for the server cards' titles. */
private val CardTitleFontSize = 18.sp
private val CardTitleLineHeight = 24.sp

/** How far a server card's content sits under its title row, unless the content says otherwise. */
private val CardContentGap = Dimens.space8

@Composable
private fun ServerChoiceCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    badge: String?,
    modifier: Modifier = Modifier,
    contentGap: Dp = CardContentGap,
    content: @Composable (selected: Boolean) -> Unit
) {
    // Unselected, the card has the same outline as every other card in the app.
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(Motion.durationMedium, easing = Motion.standardEasing),
        label = "cardBorder"
    )
    val borderWidth = if (selected) Dimens.borderStrong else Dimens.borderThin

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Clipped before selectable, so the press ripple keeps to the card's corners.
            .clip(BedrudShapeTokens.card)
            .selectable(
                selected = selected,
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
            // The radio keeps to the card's top-end corner, in a square the size of the scan
            // button in the card below it: the two centre on one column at the card's end edge,
            // and the circle sits as far from the card's top as from its side.
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(Dimens.iconButtonExtraSmall)
            )
            // Every card keeps the same roomy minimum height, its content centred in it. Centring
            // only keeps the titles level because both cards' content is the same height: each
            // address sits in a row as tall as the scan button (see addressRowGap). When the
            // official address was one bare line, its title centred lower than the one over the
            // taller field.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
            ) {
                // The title row stops short of the radio's column, so a long title or its badge
                // never runs under the radio; the content under it may reach the card's edge.
                Row(
                    modifier = Modifier.padding(end = Dimens.iconButtonExtraSmall + Dimens.space8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Every line in the card is corrected, so the block keeps its spacing and moves
                    // as one; the title is also lined up against the badge beside it.
                    val titleStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = CardTitleFontSize,
                        lineHeight = CardTitleLineHeight
                    )
                    Text(
                        text = title,
                        style = titleStyle,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = Dimens.space8)
                            .typeCentered(titleStyle)
                    )
                    if (badge != null) {
                        BedrudBadge(badge)
                    }
                }
                Spacer(Modifier.height(contentGap))
                content(selected)
            }
        }
    }
}

/** A size between the type scale's body and title steps, for the two server addresses. */
private val ServerAddressFontSize = 17.sp
private val ServerAddressLineHeight = 24.sp

/** The type both server addresses are drawn in: the official one and the one typed in. */
@Composable
private fun serverAddressStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = ServerAddressFontSize,
    lineHeight = ServerAddressLineHeight,
    textDirection = TextDirection.Ltr
)

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
    // The address sits flush under the card's title, as the official address does, and the scan
    // button ends at the card's edge under the radio. The button is a trailing action, where
    // Material puts one; a filled tonal container keeps it findable next to a field that has no
    // outline of its own.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8)
    ) {
        // Unlike BedrudTextField, both the hint and the typed value are drawn here, so both can be
        // corrected, and by the same amount — the one replaces the other in place.
        val fieldStyle = serverAddressStyle()
        // An address reads left to right in every language, but it lines up under its title: in a
        // right-to-left layout that is the right edge, where the official address above it starts.
        val addressAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right
        else TextAlign.Left
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.instance_placeholder_serverAddress),
                    style = fieldStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.typeCentered(fieldStyle)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = fieldStyle.copy(color = textColor, textAlign = addressAlign),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .typeCentered(fieldStyle)
            )
        }
        FilledTonalIconButton(
            onClick = onScanQrCode,
            enabled = enabled,
            // Material's square shape for this size: the medium corner.
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(Dimens.iconButtonExtraSmall)
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = stringResource(R.string.instance_contentDescription_scanQr),
                modifier = Modifier.size(Dimens.iconButtonExtraSmallIcon)
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
        val noteStyle = MaterialTheme.typography.bodySmall
        Text(
            text = stringResource(R.string.instance_note_insecure),
            style = noteStyle,
            color = MaterialTheme.bedrudColors.warning,
            modifier = Modifier.typeCentered(noteStyle)
        )
    }
}

/**
 * The gap between a card's title row and its address row.
 *
 * Every address sits in a row as tall as the scan button, centred in it, and the scan button is
 * taller than one line of the address at most font sizes: the address lands (button - line) / 2
 * below the row's top. The gap gives that back, which leaves each address as far under its title
 * as a bare line would sit. The line is measured rather than read off the style: a single line is
 * laid out shorter than the style's line height, its top and bottom trimmed. From about twice the
 * default font size the line is the taller one and the gap is the card's usual one.
 */
@Composable
private fun addressRowGap(): Dp {
    val addressStyle = serverAddressStyle()
    val addressHint = stringResource(R.string.instance_placeholder_serverAddress)
    val textMeasurer = rememberTextMeasurer()
    val addressLinePx = remember(textMeasurer, addressStyle, addressHint) {
        textMeasurer.measure(addressHint, addressStyle).size.height
    }
    val addressLine = with(LocalDensity.current) { addressLinePx.toDp() }
    val addressDrop = ((Dimens.iconButtonExtraSmall - addressLine) / 2).coerceAtLeast(0.dp)
    return CardContentGap - addressDrop
}

/** The official server's card: its address, read-only, under the title and its badge. */
@Composable
internal fun OfficialServerCard(
    selected: Boolean,
    onSelect: () -> Unit,
    badge: String,
    address: String,
) {
    ServerChoiceCard(
        selected = selected,
        onSelect = onSelect,
        title = stringResource(R.string.instance_choice_default_title),
        badge = badge,
        contentGap = addressRowGap(),
    ) { cardSelected ->
        // A row as tall as the other card's scan row, though this one has no button: both cards'
        // content is then the same height, and centres to the same place.
        Box(
            modifier = Modifier.heightIn(min = Dimens.iconButtonExtraSmall),
            contentAlignment = Alignment.CenterStart
        ) {
            val urlStyle = serverAddressStyle()
            Text(
                text = address,
                style = urlStyle,
                color = if (cardSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.typeCentered(urlStyle)
            )
        }
    }
}

/** The card for the user's own server: the address field, the QR scan, and what is wrong with it. */
@Composable
internal fun CustomServerCard(
    selected: Boolean,
    onSelect: () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onScanQrCode: () -> Unit,
    isInsecure: Boolean,
) {
    ServerChoiceCard(
        selected = selected,
        onSelect = onSelect,
        title = stringResource(R.string.instance_choice_custom_title),
        badge = null,
        contentGap = addressRowGap(),
    ) { cardSelected ->
        CustomServerField(
            value = value,
            onValueChange = onValueChange,
            enabled = cardSelected,
            focusRequester = focusRequester,
            onSubmit = onSubmit,
            onScanQrCode = onScanQrCode
        )
        AnimatedVisibility(visible = cardSelected && isInsecure) {
            InsecureNote()
        }
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
