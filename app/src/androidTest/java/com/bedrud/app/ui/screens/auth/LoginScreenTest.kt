package com.bedrud.app.ui.screens.auth

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.SignInNoticeRelay
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.models.Instance
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.screens.settings.SettingsStore
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The server being signed in to; `.invalid` never resolves, so nothing is fetched. */
private const val TEST_INSTANCE_ID = "login-screen-test-server"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** A file of its own, so the test never touches the servers the app has saved. */
private const val TEST_INSTANCES_PREFS = "login_screen_test_instances"

/** Long enough for a snackbar to appear, and to leave again once it has been read out. */
private const val SNACKBAR_TIMEOUT_MILLIS = 10_000L

class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** An [InstanceManager] whose only server has nobody signed in. */
    private fun signedOut(): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context))
    }

    @After
    fun forgetServer() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
    }

    @Test
    fun shouldSayWhyTheUserWasSignedOut() {
        // Signing out tears down the screen that did it, so the reason is only seen if the sign-in
        // screen the user lands on shows it.
        val notice = context.getString(R.string.auth_notice_passwordChanged)
        val relay = SignInNoticeRelay().apply { report(notice) }
        val instanceManager = signedOut()

        compose.setThemedContentAt(FontScales.Default) {
            LoginScreen(
                onLoginSuccess = {},
                onNavigateToEmailLogin = {},
                onNavigateToRegister = {},
                instanceManager = instanceManager,
                signInNoticeRelay = relay,
            )
        }

        compose.waitUntil(SNACKBAR_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(notice)).fetchSemanticsNodes().isNotEmpty()
        }
        // Consumed once read out, so a later visit to the sign-in screen does not say it again.
        compose.waitUntil(SNACKBAR_TIMEOUT_MILLIS) { relay.message.value == null }
    }
}
