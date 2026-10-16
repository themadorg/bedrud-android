package com.bedrud.app.ui.screens.dashboard

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.core.recent.RecentRoomsStore
import com.bedrud.app.models.Instance
import com.bedrud.app.models.User
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.clipsLines
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.testutil.textLayout
import com.bedrud.app.ui.screens.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The server the test's account is signed in to; `.invalid` never resolves, so nothing is fetched. */
private const val TEST_INSTANCE_ID = "dashboard-screen-test-account"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** Files of their own, so the test never touches the servers or rooms the app has saved. */
private const val TEST_INSTANCES_PREFS = "dashboard_screen_test_instances"
private const val TEST_RECENTS_PREFS = "dashboard_screen_test_recents"

/** The server's name for an account made by continuing as a guest. */
private const val GUEST_PROVIDER = "guest"

/**
 * The quick-join bar at the font scales a reader can choose in system settings, and what the
 * dashboard offers a guest compared with an account.
 */
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun recentRooms() = RecentRoomsStore(context.getSharedPreferences(TEST_RECENTS_PREFS, Context.MODE_PRIVATE))

    /** An [InstanceManager] whose only server has an account from [provider] signed in. */
    private fun signedInThrough(provider: String?): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        AuthManager(context, TEST_INSTANCE_ID).saveUser(
            User(id = TEST_INSTANCE_ID, email = "account@example.invalid", name = TEST_SERVER_NAME, provider = provider)
        )
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context), recentRooms())
    }

    /** Shows the rooms dashboard to whoever [instanceManager] has signed in. */
    private fun showDashboard(instanceManager: InstanceManager) {
        compose.setThemedContentAt(FontScales.Default) {
            DashboardContent(
                onJoinRoom = {},
                onOpenProfile = {},
                onNavigateToAddInstance = {},
                instanceManager = instanceManager,
                recentRoomsStore = recentRooms(),
            )
        }
    }

    /**
     * Waits for [text] to appear. The dashboard shows a spinner in place of its filters and list
     * until its first load is over, which for an account means a request to a server that never
     * answers.
     */
    private fun waitForText(text: String) {
        compose.waitUntil(LoadTimeoutMillis) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun forgetAccount() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
        context.deleteSharedPreferences(TEST_RECENTS_PREFS)
    }

    private fun showQuickJoinBar(fontScale: Float, value: String = "") {
        compose.setThemedContentAt(fontScale) {
            QuickJoinBar(value = value, onValueChange = {}, onJoin = {})
        }
    }

    private fun fieldHeight(): Dp = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().height

    private fun assertPlaceholderFits(fontScale: Float) {
        showQuickJoinBar(fontScale)
        val placeholder = context.getString(R.string.dashboard_placeholder_joinRoom)

        val layout = compose.onNodeWithText(placeholder, useUnmergedTree = true).textLayout()

        assertFalse("placeholder clipped at font scale $fontScale", layout.clipsLines)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtDefaultFontScale() {
        assertPlaceholderFits(FontScales.Default)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtRaisedFontScale() {
        assertPlaceholderFits(FontScales.Raised)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtLargeFontScale() {
        assertPlaceholderFits(FontScales.Large)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtLargestFontScale() {
        assertPlaceholderFits(FontScales.Largest)
    }

    @Test
    fun shouldLayOutTypedRoomNameInFullAtLargestFontScale() {
        showQuickJoinBar(FontScales.Largest, value = TypedRoomName)

        val layout = compose.onNode(hasSetTextAction()).textLayout()

        assertFalse("typed room name clipped", layout.clipsLines)
    }

    @Test
    fun shouldKeepJoinButtonAsTallAsFieldAtLargestFontScale() {
        showQuickJoinBar(FontScales.Largest, value = TypedRoomName)
        val joinLabel = context.getString(R.string.common_button_join)

        val buttonHeight = compose.onNode(hasText(joinLabel) and hasClickAction())
            .getUnclippedBoundsInRoot().height

        assertEquals(fieldHeight().value, buttonHeight.value, HeightToleranceDp)
    }

    /** Whatever sits under the bar would jump if the field changed height on the first keystroke. */
    @Test
    fun shouldKeepFieldHeightWhenTypingStartsAtLargestFontScale() {
        var value by mutableStateOf("")
        compose.setThemedContentAt(FontScales.Largest) {
            QuickJoinBar(value = value, onValueChange = {}, onJoin = {})
        }
        val emptyHeight = fieldHeight()

        compose.runOnIdle { value = TypedRoomName }
        val typedHeight = fieldHeight()

        assertEquals(emptyHeight.value, typedHeight.value, HeightToleranceDp)
    }

    /**
     * The server refuses a guest's request for the room list, and that refusal used to be shown as
     * an error on every visit. The dashboard now says what a guest can do instead.
     */
    @Test
    fun shouldTellGuestWhatTheyCanDo() {
        showDashboard(signedInThrough(GUEST_PROVIDER))

        compose.onNodeWithText(context.getString(R.string.dashboard_guest_notice)).assertExists()
    }

    /** A guest has no rooms of their own and cannot create one: both controls led only to a refusal. */
    @Test
    fun shouldOfferNeitherCreateRoomNorMyRoomsToGuest() {
        showDashboard(signedInThrough(GUEST_PROVIDER))
        waitForText(context.getString(R.string.dashboard_guest_notice))

        compose.onNodeWithContentDescription(context.getString(R.string.dashboard_contentDescription_createRoom))
            .assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.dashboard_filter_myRooms)).assertDoesNotExist()
    }

    @Test
    fun shouldOfferCreateRoomAndMyRoomsToSignedInAccount() {
        showDashboard(signedInThrough(provider = null))
        waitForText(context.getString(R.string.dashboard_filter_myRooms))

        compose.onNodeWithContentDescription(context.getString(R.string.dashboard_contentDescription_createRoom))
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.dashboard_guest_notice)).assertDoesNotExist()
    }

    private companion object {
        const val TypedRoomName = "weekly-sync"

        /** Bounds come back in fractional dp, so two equal heights can differ by rounding alone. */
        const val HeightToleranceDp = 0.5f

        /** Long enough for a lookup of an `.invalid` host to fail, which it does at once. */
        const val LoadTimeoutMillis = 10_000L
    }
}
