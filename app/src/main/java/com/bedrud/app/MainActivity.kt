package com.bedrud.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bedrud.app.core.api.apiBody
import com.bedrud.app.core.auth.OAuthLoginHandler
import com.bedrud.app.core.call.CallService
import com.bedrud.app.core.createLocaleContext
import com.bedrud.app.core.deeplink.BedrudURLParser
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.meeting.VideoAspect
import com.bedrud.app.core.pip.PipStateHolder
import com.bedrud.app.ui.screens.auth.EmailLoginScreen
import com.bedrud.app.ui.screens.auth.LoginScreen
import com.bedrud.app.ui.screens.auth.RegisterScreen
import com.bedrud.app.ui.screens.instance.AddInstanceScreen
import com.bedrud.app.ui.screens.main.MainScreen
import com.bedrud.app.ui.screens.meeting.MeetingScreen
import com.bedrud.app.ui.screens.settings.AppAppearance
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.bedrud.app.ui.theme.BedrudTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val instanceManager: InstanceManager by inject()
    private val settingsStore: SettingsStore by inject()
    private val pipStateHolder: PipStateHolder by inject()

    private val _deepLinkRoomName = MutableStateFlow<String?>(null)
    private val _oauthToken = MutableStateFlow<String?>(null)

    override fun attachBaseContext(base: Context) {
        val localeTag = SettingsStore(base).getLanguageTag()
        super.attachBaseContext(base.createLocaleContext(localeTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A null savedInstanceState means Android had no state to restore for this task —
        // which is exactly what happens when the task was swiped away / force-stopped (as
        // opposed to merely backgrounded, where the OS preserves and hands back the saved
        // Bundle even if it killed the process to reclaim memory in the meantime). Treat
        // that as "fully closed" and reset the remembered tab so the app lands on Rooms
        // instead of wherever the user was last, without affecting real background resumes.
        if (savedInstanceState == null) {
            settingsStore.setLastTab(0)
        }

        // Parse deep link from initial intent
        handleDeepLink(intent)

        // Return to an ongoing meeting from the call notification
        handleReturnToMeeting(intent)
        clearLockScreenFlags()

        // Resume meeting if the foreground call service is still running
        CallService.activeRoomName?.let { room ->
            _deepLinkRoomName.value = room
        }

        // Handle OAuth callback from initial intent (app not running)
        handleOAuthCallback(intent)

        setContent {
            val appearance by settingsStore.appearance.collectAsState()
            val darkTheme = when (appearance) {
                AppAppearance.LIGHT -> false
                AppAppearance.DARK -> true
                AppAppearance.SYSTEM -> isSystemInDarkTheme()
            }

            val language by settingsStore.language.collectAsState()

            BedrudTheme(darkTheme = darkTheme, language = language) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BedrudNavHost(
                        instanceManager = instanceManager,
                        deepLinkRoomName = _deepLinkRoomName,
                        oauthToken = _oauthToken
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleReturnToMeeting(intent)
        handleOAuthCallback(intent)
        clearLockScreenFlags()
    }

    override fun onPause() {
        clearLockScreenFlags()
        super.onPause()
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val parsed = BedrudURLParser.parse(uri.toString()) ?: return
        _deepLinkRoomName.value = parsed.roomName
    }

    private fun handleReturnToMeeting(intent: Intent?) {
        if (intent?.action != CallService.ACTION_RETURN_TO_MEETING) return
        val roomName = intent.getStringExtra(CallService.EXTRA_ROOM_NAME) ?: return
        _deepLinkRoomName.value = roomName
    }

    private fun clearLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!OAuthLoginHandler.isOAuthCallback(uri)) return
        val token = OAuthLoginHandler.extractToken(intent) ?: return
        _oauthToken.value = token
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipStateHolder.isInMeeting.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(VideoAspect.WIDTH, VideoAspect.HEIGHT))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipStateHolder.setInPipMode(isInPictureInPictureMode)
    }
}

object Routes {
    const val ADD_INSTANCE = "add_instance"
    const val LOGIN = "login"
    const val EMAIL_LOGIN = "email_login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val MEETING = "meeting/{roomName}"

    fun meeting(roomName: String): String = "meeting/$roomName"
}

@Composable
fun BedrudNavHost(
    instanceManager: InstanceManager,
    deepLinkRoomName: MutableStateFlow<String?> = MutableStateFlow(null),
    oauthToken: MutableStateFlow<String?> = MutableStateFlow(null)
) {
    val navController = rememberNavController()
    val instances by instanceManager.store.instances.collectAsState()
    val authManager by instanceManager.authManager.collectAsState()
    val authApi by instanceManager.authApi.collectAsState()
    val isLoggedIn = authManager?.isLoggedIn?.collectAsState()?.value ?: false

    // Route to the right top-level destination for the current auth state. This re-runs when the
    // active instance is switched (authManager swaps) — including a switch made as part of a
    // cross-server join. In that case we're navigating to MEETING, so MUST NOT force MAIN here:
    // once logged in, both MAIN and MEETING are valid, and force-navigating MAIN with popUpTo(0)
    // would pop the meeting we're opening and leave only the CallService notification running.
    LaunchedEffect(instances.isEmpty(), isLoggedIn, authManager) {
        val current = navController.currentDestination?.route
        when {
            instances.isEmpty() ->
                navController.navigate(Routes.ADD_INSTANCE) { popUpTo(0) { inclusive = true } }
            !isLoggedIn ->
                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            current != Routes.MAIN && current != Routes.MEETING ->
                navController.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
        }
    }

    // Handle OAuth callback — save token then fetch user profile
    val oauthTokenValue by oauthToken.collectAsState()
    LaunchedEffect(oauthTokenValue) {
        val token = oauthTokenValue ?: return@LaunchedEffect
        val manager = authManager ?: return@LaunchedEffect
        val api = authApi ?: return@LaunchedEffect
        // Save access token first so getMe() uses it in the Authorization header
        manager.saveTokens(token, "")
        // Best-effort: the token is already saved, so a failed fetch just means no cached profile
        // yet. Must not throw — this runs directly in a LaunchedEffect.
        val body = apiBody("", onError = {}) { api.getMe() }
        if (body != null) {
            manager.saveUser(
                com.bedrud.app.models.User(
                    id = body.id,
                    email = body.email,
                    name = body.name,
                    avatarUrl = body.avatarUrl,
                    isAdmin = body.isAdmin,
                    provider = body.provider
                )
            )
        }
        oauthToken.value = null
    }

    // Handle deep links
    val deepLink by deepLinkRoomName.collectAsState()
    LaunchedEffect(deepLink) {
        val roomName = deepLink ?: return@LaunchedEffect
        if (isLoggedIn) {
            // The recent is recorded once the call actually starts (MeetingScreen), not on the way
            // in: a link to a room the server has deleted must not leave a card behind for it.
            navController.navigate(Routes.meeting(roomName)) {
                launchSingleTop = true
                popUpTo(Routes.MEETING) { inclusive = true }
            }
            deepLinkRoomName.value = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.ADD_INSTANCE
    ) {
        composable(Routes.ADD_INSTANCE) {
            AddInstanceScreen(
                onInstanceAdded = {
                    // Where to land is the chosen server's own auth state, read after the switch
                    // rather than from this composable's collected copy, which is a frame behind.
                    // A brand-new server always needs sign-in, but the picker can now also be used
                    // to continue on a server already stored (#102) — and sending a user who is
                    // signed in there to the sign-in hub would strand them a second way: the auth
                    // router does not re-fire, because none of its keys changed.
                    val signedIn = instanceManager.authManager.value?.isLoggedIn?.value == true
                    // singleTop because the sign-in hub may already be underneath: reaching this
                    // screen from the hub's back button now leaves LOGIN on the stack (see its
                    // onBack), and pushing a second copy would put two hubs back to back.
                    navController.navigate(if (signedIn) Routes.MAIN else Routes.LOGIN) {
                        popUpTo(Routes.ADD_INSTANCE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEmailLogin = {
                    navController.navigate(Routes.EMAIL_LOGIN)
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onBack = {
                    // Pushed on top of the hub rather than replacing it. Clearing the stack here
                    // made the server picker the only entry, so system back had nowhere to go and
                    // the user was stuck on it (#102). Leaving LOGIN underneath means back from
                    // the picker returns to sign-in, which is where they came from.
                    navController.navigate(Routes.ADD_INSTANCE)
                }
            )
        }

        composable(Routes.EMAIL_LOGIN) {
            EmailLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onJoinRoom = { roomName ->
                    navController.navigate(Routes.meeting(roomName)) {
                        launchSingleTop = true
                        popUpTo(Routes.MEETING) { inclusive = true }
                    }
                },
                onLogout = {
                    instanceManager.authManager.value?.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAddInstance = {
                    navController.navigate(Routes.ADD_INSTANCE)
                }
            )
        }

        composable(
            route = Routes.MEETING,
            arguments = listOf(
                navArgument("roomName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomName = backStackEntry.arguments?.getString("roomName") ?: return@composable
            MeetingScreen(
                roomName = roomName,
                onLeave = {
                    // Leaving can be triggered twice for one action (e.g. the
                    // button handler pops immediately, then the connection-state
                    // watcher pops again once the async disconnect lands). Only
                    // pop while the meeting screen is still the current entry so
                    // the second call can't pop past it and empty the back stack.
                    if (navController.currentDestination?.route == Routes.MEETING) {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
