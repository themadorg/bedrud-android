package com.bedrud.app.ui.screens.main

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.core.rooms.JoinFailureRelay
import com.bedrud.app.models.Instance
import com.bedrud.app.models.User
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.screens.settings.SettingsStore
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The server the test's account is signed in to; `.invalid` never resolves, so nothing is fetched. */
private const val TEST_INSTANCE_ID = "main-screen-test-account"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** A file of its own, so the test never touches the servers the app has saved. */
private const val TEST_INSTANCES_PREFS = "main_screen_test_instances"

/** Accesses as the server sends them for each level an account can hold. */
private val SUPERADMIN_ACCESSES = listOf("user", "superadmin")
private val ADMIN_ACCESSES = listOf("user", "admin")

class MainScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** An [InstanceManager] whose only server has an account with [accesses] signed in. */
    private fun signedInWith(accesses: List<String>): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        AuthManager(context, TEST_INSTANCE_ID).saveUser(
            User(id = TEST_INSTANCE_ID, email = "account@example.invalid", name = TEST_SERVER_NAME, accesses = accesses)
        )
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context))
    }

    /** Shows the signed-in screens for whoever [instanceManager] has signed in. */
    private fun showMainScreen(instanceManager: InstanceManager) {
        compose.setThemedContentAt(FontScales.Default) {
            MainScreen(
                onJoinRoom = {},
                onLogout = {},
                onNavigateToAddInstance = {},
                instanceManager = instanceManager,
                settingsStore = SettingsStore(context),
                joinFailureRelay = JoinFailureRelay(),
            )
        }
    }

    @After
    fun forgetAccount() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
    }

    @Test
    fun shouldOfferAdminTabToSuperadmin() {
        // The server sends a superadmin's rights as an access level, never as an isAdmin flag;
        // read from that flag, no account ever got the tab.
        showMainScreen(signedInWith(SUPERADMIN_ACCESSES))

        compose.onNodeWithText(context.getString(R.string.main_tab_admin)).assertExists()
    }

    @Test
    fun shouldNotOfferAdminTabToAdminBelowSuperadmin() {
        // Every admin endpoint turns a plain admin away, so the tab would hold only refusals.
        showMainScreen(signedInWith(ADMIN_ACCESSES))

        compose.onNodeWithText(context.getString(R.string.main_tab_admin)).assertDoesNotExist()
    }
}
