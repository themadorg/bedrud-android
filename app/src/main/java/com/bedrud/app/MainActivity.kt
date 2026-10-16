package com.bedrud.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.lifecycleScope
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
import com.bedrud.app.core.pip.PipAspect
import com.bedrud.app.core.pip.PipStateHolder
import com.bedrud.app.core.pip.PipWindow
import com.bedrud.app.core.rooms.PendingRoom
import com.bedrud.app.ui.screens.auth.EmailLoginScreen
import com.bedrud.app.ui.screens.auth.LoginScreen
import com.bedrud.app.ui.screens.auth.RegisterScreen
import com.bedrud.app.ui.screens.instance.AddInstanceScreen
import com.bedrud.app.ui.screens.main.MainScreen
import com.bedrud.app.ui.screens.meeting.MeetingScreen
import com.bedrud.app.ui.screens.settings.AppAppearance
import com.bedrud.app.ui.screens.settings.LicensesScreen
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.bedrud.app.ui.theme.BedrudTheme
import com.bedrud.app.ui.theme.SystemBarDarkScrim
import com.bedrud.app.ui.theme.SystemBarLightScrim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val instanceManager: InstanceManager by inject()
    private val settingsStore: SettingsStore by inject()
    private val pipStateHolder: PipStateHolder by inject()

    // The room the app was asked to open, by a join from the dashboard, a link, the call notification
    // or a resumed call, held until somebody is signed in on its server to open it.
    private val pendingRoom = PendingRoom()
    private val _oauthToken = MutableStateFlow<String?>(null)

    /** Holds [roomName] for the server that is active now, the one the request arrived on. */
    private fun holdOnActiveServer(roomName: String) {
        pendingRoom.hold(roomName, instanceManager.store.activeInstanceId.value)
    }

    /** One of the picture-in-picture window's controls, labelled by [titleResource]. */
    private fun pipAction(@DrawableRes iconResource: Int, @StringRes titleResource: Int, intent: PendingIntent): RemoteAction {
        val title = getString(titleResource)
        return RemoteAction(Icon.createWithResource(this, iconResource), title, title, intent)
    }

    /**
     * The picture-in-picture window, as [window] shapes it, with its two controls: the microphone,
     * drawn as it is now and labelled with what a tap does, and hanging up. Both go to
     * [CallService], as the call notification's controls do. The window grows from the tile it will
     * show; without that, Android draws the app's icon over it while it opens.
     *
     * From Android 12 the window opens by itself whenever the app is left during a call, and grows
     * out of the app with the system's own animation; [onUserLeaveHint] opens it on older versions.
     */
    private fun pictureInPictureParams(window: PipWindow): PictureInPictureParams {
        val muteAction = if (pipStateHolder.isMicEnabled.value) {
            pipAction(R.drawable.ic_pip_mic, R.string.call_action_mute, CallService.toggleMuteIntent(this))
        } else {
            pipAction(R.drawable.ic_pip_mic_off, R.string.call_action_unmute, CallService.toggleMuteIntent(this))
        }
        val hangUpAction =
            pipAction(R.drawable.ic_pip_call_end, R.string.call_action_hangUp, CallService.hangUpIntent(this))
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(window.aspect.width, window.aspect.height))
            .setActions(listOf(muteAction, hangUpAction))
            .apply {
                window.sourceRect?.let { setSourceRectHint(Rect(it.left, it.top, it.right, it.bottom)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(pipStateHolder.isInMeeting.value)
                }
            }
            .build()
    }

    /**
     * Hands the window to [apply]. A phone whose maker narrowed the shapes Android allows refuses
     * one inside Android's own limits, so the window falls back to a square, which every phone
     * takes, rather than the app crashing.
     */
    private fun withPictureInPictureParams(apply: (PictureInPictureParams) -> Unit) {
        try {
            apply(pictureInPictureParams(pipStateHolder.window.value))
        } catch (e: IllegalArgumentException) {
            apply(pictureInPictureParams(PipWindow(PipAspect.Square, sourceRect = null)))
        }
    }

    /**
     * Closes the picture-in-picture window once the call it shows has ended, from its own hang-up
     * control or a removal by a moderator: left open, it went on to show the rooms dashboard.
     */
    private fun closePictureInPictureAfterCall() {
        if (pipStateHolder.isInPipMode.value) moveTaskToBack(false)
    }

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
            holdOnActiveServer(room)
        }

        // Handle OAuth callback from initial intent (app not running)
        handleOAuthCallback(intent)

        // Kept current while the activity lives, so the window opens with its microphone control
        // as it is, in the shape of what it shows, from where that is on screen, and by itself only
        // during a call; a mute from inside the window, or from anywhere else, turns it over.
        lifecycleScope.launch {
            combine(pipStateHolder.isMicEnabled, pipStateHolder.isInMeeting, pipStateHolder.window) { _, _, _ -> }
                .collect { withPictureInPictureParams(::setPictureInPictureParams) }
        }

        setContent {
            val appearance by settingsStore.appearance.collectAsState()
            val darkTheme = when (appearance) {
                AppAppearance.LIGHT -> false
                AppAppearance.DARK -> true
                AppAppearance.SYSTEM -> isSystemInDarkTheme()
            }

            // enableEdgeToEdge's default picks light or dark bar icons from the system's night mode,
            // which the in-app appearance setting overrides. Left alone, a Light app on a phone in
            // Dark mode drew white status icons over a white screen, and the reverse drew dark on
            // dark. Re-applying it whenever the resolved theme changes keeps the bars in step.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        SystemBarLightScrim.toArgb(),
                        SystemBarDarkScrim.toArgb(),
                    ) { darkTheme },
                )
                onDispose {}
            }

            val language by settingsStore.language.collectAsState()

            BedrudTheme(darkTheme = darkTheme, language = language) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BedrudNavHost(
                        instanceManager = instanceManager,
                        pendingRoom = pendingRoom,
                        oauthToken = _oauthToken,
                        onMeetingLeft = ::closePictureInPictureAfterCall,
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
        holdOnActiveServer(parsed.roomName)
    }

    private fun handleReturnToMeeting(intent: Intent?) {
        if (intent?.action != CallService.ACTION_RETURN_TO_MEETING) return
        val roomName = intent.getStringExtra(CallService.EXTRA_ROOM_NAME) ?: return
        holdOnActiveServer(roomName)
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
        // From Android 12 the window opens by itself; see pictureInPictureParams.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipStateHolder.isInMeeting.value) {
            withPictureInPictureParams { enterPictureInPictureMode(it) }
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
    const val LICENSES = "licenses"
    const val MEETING = "meeting/{roomName}"

    fun meeting(roomName: String): String = "meeting/$roomName"
}

@Composable
fun BedrudNavHost(
    instanceManager: InstanceManager,
    pendingRoom: PendingRoom,
    oauthToken: MutableStateFlow<String?> = MutableStateFlow(null),
    // Called once a meeting screen has left, for the activity to close its picture-in-picture
    // window rather than go on showing whatever is under the meeting.
    onMeetingLeft: () -> Unit = {},
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
        val user = apiBody("", onError = {}) { api.getMe() }
        if (user != null) {
            manager.saveUser(user)
        }
        oauthToken.value = null
    }

    // Open the room the app was asked for once somebody is signed in on its server. Every way into a
    // meeting holds its room in pendingRoom rather than navigating itself, so a room asked for on a
    // server with nobody signed in waits there across the sign-in, which clears the back stack.
    // Keyed on the collected state so it re-runs when a sign-in or a switch lands, but decided on the
    // live state: for a frame after a server switch, the collected sign-in is still the old server's.
    val requestedRoom by pendingRoom.request.collectAsState()
    val activeServerId by instanceManager.store.activeInstanceId.collectAsState()
    LaunchedEffect(requestedRoom, isLoggedIn, activeServerId) {
        val roomName = pendingRoom.take(
            activeServerId = instanceManager.store.activeInstanceId.value,
            isSignedIn = instanceManager.authManager.value?.isLoggedIn?.value == true,
        ) ?: return@LaunchedEffect
        // The recent is recorded once the call actually starts (MeetingScreen), not on the way
        // in: a link to a room the server has deleted must not leave a card behind for it.
        // In place of any meeting already on the stack, so at most one is ever there: a room link
        // followed from a call's chat opens its room where the room just left used to be.
        navController.navigate(Routes.meeting(roomName)) {
            launchSingleTop = true
            popUpTo(Routes.MEETING) { inclusive = true }
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
                // The active server is read when the join is asked for, after any switch the
                // caller made just before it, so a join on another server is held for that server
                // and waits there if its sign-in is still to come.
                onJoinRoom = { roomName ->
                    pendingRoom.hold(roomName, instanceManager.store.activeInstanceId.value)
                },
                onLogout = {
                    instanceManager.authManager.value?.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAddInstance = {
                    navController.navigate(Routes.ADD_INSTANCE)
                },
                onNavigateToLicenses = {
                    navController.navigate(Routes.LICENSES)
                }
            )
        }

        composable(Routes.LICENSES) {
            // A second tap on the arrow while the screen animates out would pop Main as well and
            // leave the back stack empty; dropUnlessResumed ignores taps once the entry is leaving.
            LicensesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
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
                    // pop while this entry is still the current one, so the second
                    // call can't pop past it and empty the back stack. The entry,
                    // not the route: after following a room link the next room is
                    // a meeting too, and the room just left, still on screen while
                    // it animates out, must not close it.
                    if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                        navController.popBackStack()
                        onMeetingLeft()
                    }
                },
                // Held like every other way in. The meeting screen switches server before asking,
                // so the room is held for the server it lives on, and a room there that needs a
                // sign-in first opens after it rather than being lost with the back stack.
                onJoinRoom = { nextRoomName ->
                    pendingRoom.hold(nextRoomName, instanceManager.store.activeInstanceId.value)
                },
            )
        }
    }
}
