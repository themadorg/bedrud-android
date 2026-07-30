package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudShapeTokens

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
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
        textStyle = textStyle.copy(textDirection = textDirection),
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
