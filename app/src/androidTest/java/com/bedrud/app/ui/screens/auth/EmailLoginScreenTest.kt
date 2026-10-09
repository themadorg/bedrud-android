package com.bedrud.app.ui.screens.auth

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.Rule
import org.junit.Test

/** How the password-reset sheet reports a request the server refused. */
class EmailLoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showFailingResetSheet() {
        compose.setContent {
            BedrudTheme {
                ForgotPasswordSheet(
                    initialEmail = Email,
                    onDismiss = {},
                    onSubmit = { Result.failure(Exception(FailureMessage)) },
                    onSent = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.auth_forgot_submit)).performClick()
    }

    private fun emailField() = compose.onNode(hasSetTextAction(), useUnmergedTree = true)

    /** The field's own supporting text, as every other form in the app reports an error. */
    @Test
    fun shouldReportFailureAsTheFieldsSupportingText() {
        showFailingResetSheet()

        emailField().assert(hasAnyDescendant(hasText(FailureMessage)))
    }

    @Test
    fun shouldMarkTheFieldInError() {
        showFailingResetSheet()

        emailField().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    private companion object {
        const val Email = "reset@example.test"
        const val FailureMessage = "Server unreachable"
    }
}
