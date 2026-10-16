package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import org.junit.Rule
import org.junit.Test

/** How the app's snackbar says what kind of message it is carrying. */
class BedrudSnackbarHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The words a screen reader says for each kind, before the message itself. */
    private val kindNames = mapOf(
        BedrudSnackbarKind.INFO to R.string.common_contentDescription_info,
        BedrudSnackbarKind.SUCCESS to R.string.common_contentDescription_success,
        BedrudSnackbarKind.WARNING to R.string.common_contentDescription_warning,
        BedrudSnackbarKind.ERROR to R.string.common_contentDescription_error,
    )

    /** Shows one message of each of [kinds], each in a host of its own, and waits for them all. */
    private fun showMessages(vararg kinds: BedrudSnackbarKind) {
        val hostStates = kinds.map { kind -> kind to SnackbarHostState() }
        compose.setThemedContentAt(FontScales.Default) {
            Column {
                hostStates.forEach { (kind, hostState) ->
                    BedrudSnackbarHost(hostState)
                    LaunchedEffect(hostState) { hostState.showMessage(Message, kind) }
                }
            }
        }
        compose.waitUntil(ShowTimeoutMillis) {
            compose.onAllNodes(hasText(Message)).fetchSemanticsNodes().size == kinds.size
        }
    }

    /**
     * The icon is the only thing that says whether a message is an error or a confirmation, so a
     * screen reader is told in words what a sighted reader is told by the icon.
     */
    @Test
    fun shouldNameEveryKindForScreenReaders() {
        showMessages(*kindNames.keys.toTypedArray())

        kindNames.values.forEach { nameResource ->
            compose.onAllNodesWithContentDescription(context.getString(nameResource)).assertCountEquals(1)
        }
    }

    /** An error carries a button to put it away once it has been read. */
    @Test
    fun shouldOfferAnErrorACloseButton() {
        showMessages(BedrudSnackbarKind.ERROR)

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun shouldOfferAConfirmationNoCloseButton() {
        showMessages(BedrudSnackbarKind.SUCCESS)

        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private companion object {
        const val Message = "Settings saved"

        /** A snackbar animates in, so it is waited for rather than expected on the first frame. */
        const val ShowTimeoutMillis = 5_000L
    }
}
