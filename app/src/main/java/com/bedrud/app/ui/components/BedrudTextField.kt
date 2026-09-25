package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.typeCentered

/**
 * The app's standard single-line form field: outlined, [BedrudShapeTokens.field] corners, and
 * scroll-into-view on focus. Screens declare only what varies — label or placeholder, keyboard,
 * autofill, validation state — so every form field looks and behaves the same.
 *
 * [textDirection] pins the field's base direction regardless of locale: use [TextDirection.Ltr]
 * for machine-shaped values (emails, URLs, room slugs) and leave [TextDirection.Content] for
 * human names and free text.
 */
@Composable
fun BedrudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    autofill: ContentType? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textDirection: TextDirection = TextDirection.Content,
) {
    val fieldModifier = modifier
        .fillMaxWidth()
        .bringIntoViewOnFocus()
        .let { if (autofill != null) it.autofillType(autofill) else it }
    // Placeholder must match the input's own text style: M3 otherwise renders the placeholder at
    // its default bodyLarge regardless of [textStyle], so a compact field (e.g. bodyMedium) shows a
    // hint larger than the text the user types. It takes the same size, but not [textDirection]:
    // that pins the value typed in, while the hint is written in the app's language and reads in
    // its direction, as the label does. Laid out left-to-right, a Persian hint's closing "…" went
    // in front of its first word.
    //
    // The label is centred on its letters like every other label in the app (`typeCentered`, see
    // TextInk.kt), in its own current style. It never shares its place with typed text: it rests
    // in the field only while the field is empty and unfocused, and floats to the outline first.
    //
    // The placeholder deliberately takes no correction. It is replaced in place by the value the
    // user types, which is drawn inside OutlinedTextField where no modifier reaches it; correcting
    // only the placeholder would make the text jump on the first keystroke. Both stay uncorrected so
    // they agree with each other.
    val mergedTextStyle = textStyle.copy(textDirection = textDirection)
    // A single-line field keeps its hint to one line as well. A hint that wrapped at a large font
    // size made the empty field taller than the one line it takes once typed in, so the field
    // shrank on the first keystroke and everything under it jumped.
    val placeholderMaxLines = if (singleLine) 1 else Int.MAX_VALUE
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it, modifier = Modifier.typeCentered(LocalTextStyle.current)) } },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = textStyle,
                    maxLines = placeholderMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = BedrudShapeTokens.field,
        textStyle = mergedTextStyle,
        modifier = fieldModifier,
    )
}

/**
 * A password variant of [BedrudTextField]: masked input, LTR, password keyboard, and a show/hide
 * eye toggle.
 *
 * Visibility is self-managed by default. To share one visibility state across sibling fields
 * (password + confirm), hoist it: pass [visible] plus [onToggleVisibility] on the field that
 * shows the toggle, and [visible] alone on the follower (it renders no toggle of its own).
 */
@Composable
fun BedrudPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    autofill: ContentType? = null,
    visible: Boolean? = null,
    onToggleVisibility: (() -> Unit)? = null,
) {
    var selfVisible by rememberSaveable { mutableStateOf(false) }
    val isVisible = visible ?: selfVisible
    val toggle: (() -> Unit)? = onToggleVisibility
        ?: if (visible == null) ({ selfVisible = !selfVisible }) else null
    BedrudTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        trailingIcon = toggle?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (isVisible) {
                            stringResource(R.string.auth_password_toggle_hide)
                        } else {
                            stringResource(R.string.auth_password_toggle_show)
                        }
                    )
                }
            }
        },
        visualTransformation = if (isVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        autofill = autofill,
        textDirection = TextDirection.Ltr,
    )
}
