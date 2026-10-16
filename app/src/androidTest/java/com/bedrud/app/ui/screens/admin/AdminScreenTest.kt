package com.bedrud.app.ui.screens.admin

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.api.AdminApi
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.core.recent.RecentRoomsStore
import com.bedrud.app.models.AdminRoom
import com.bedrud.app.models.AdminSettings
import com.bedrud.app.models.AdminUser
import com.bedrud.app.models.CreateInviteTokenRequest
import com.bedrud.app.models.Instance
import com.bedrud.app.models.InviteToken
import com.bedrud.app.models.RoomListResponse
import com.bedrud.app.models.SetAccessesRequest
import com.bedrud.app.models.TokenListResponse
import com.bedrud.app.models.User
import com.bedrud.app.models.UserListResponse
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.screens.settings.SettingsStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection

/** The server the gate tests' account is signed in to; `.invalid` never resolves. */
private const val TEST_INSTANCE_ID = "admin-screen-test-account"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** Files of their own, so the test never touches the servers or rooms the app has saved. */
private const val TEST_INSTANCES_PREFS = "admin_screen_test_instances"
private const val TEST_RECENTS_PREFS = "admin_screen_test_recents"

/** The server's access levels: only the highest one is let into the admin API. */
private const val SUPERADMIN_ACCESS = "superadmin"
private const val ADMIN_ACCESS = "admin"

/** The field the server names the online count with. */
private const val ONLINE_COUNT_FIELD = "count"

/** How often the overview asks the server again for the online count. */
private const val ONLINE_COUNT_REFRESH_MILLIS = 30_000L

/** Enough time for a message to appear, or for a short one to be read and go. */
private const val MESSAGE_TIMEOUT_MILLIS = 10_000L

/** How long an invite token stays valid when the screen asks for one: a week, in hours. */
private const val INVITE_TOKEN_LIFETIME_HOURS = 168

/** Who an invite token is made out to. */
private const val INVITE_EMAIL = "invitee@example.invalid"

/** How a token's row shortens it: its first sixteen characters, then an ellipsis. */
private const val SHORTENED_TOKEN = "0123456789abcdef…"

private val JsonMediaType = "application/json".toMediaType()

/** A refusal the server explains in its own words, as it does with `{"error": "…"}`. */
private fun <T> refusal(explanation: String): Response<T> =
    Response.error(HttpURLConnection.HTTP_FORBIDDEN, """{"error":"$explanation"}""".toResponseBody(JsonMediaType))

/** A failure the server gives no reason for. */
private fun <T> unexplainedFailure(): Response<T> =
    Response.error(HttpURLConnection.HTTP_INTERNAL_ERROR, "".toResponseBody(JsonMediaType))

private fun onlineCount(count: Int): Response<Map<String, Int>> = Response.success(mapOf(ONLINE_COUNT_FIELD to count))

/**
 * An admin API that answers each call as the test set it up, and keeps every change the screen asked
 * the server for, in order.
 */
private class FakeAdminApi : AdminApi {
    var usersAnswer: () -> Response<UserListResponse> = { Response.success(UserListResponse(emptyList())) }
    var roomsAnswer: () -> Response<RoomListResponse> = { Response.success(RoomListResponse(emptyList())) }
    var onlineCountAnswer: () -> Response<Map<String, Int>> = { onlineCount(0) }
    var settingsAnswer: () -> Response<AdminSettings> = { Response.success(AdminSettings()) }
    var inviteTokensAnswer: () -> Response<TokenListResponse> = { Response.success(TokenListResponse(emptyList())) }

    var userStatusAnswer: () -> Response<Unit> = { Response.success(Unit) }
    var roomDeleteAnswer: () -> Response<Unit> = { Response.success(Unit) }
    var settingsSaveAnswer: () -> Response<Unit> = { Response.success(Unit) }
    var tokenCreateAnswer: () -> Response<InviteToken> = { unexplainedFailure() }
    var tokenDeleteAnswer: () -> Response<Unit> = { Response.success(Unit) }

    val userStatusRequests = mutableListOf<Pair<String, Map<String, Boolean>>>()
    val roomDeleteRequests = mutableListOf<String>()
    val settingsSaveRequests = mutableListOf<AdminSettings>()
    val tokenCreateRequests = mutableListOf<CreateInviteTokenRequest>()
    val tokenDeleteRequests = mutableListOf<String>()

    override suspend fun listUsers() = usersAnswer()

    override suspend fun setUserStatus(id: String, body: Map<String, Boolean>): Response<Unit> {
        userStatusRequests += id to body
        return userStatusAnswer()
    }

    override suspend fun setUserAccesses(id: String, body: SetAccessesRequest): Response<Unit> =
        error("The admin screen never changes a user's accesses")

    override suspend fun listRooms() = roomsAnswer()

    override suspend fun deleteRoom(id: String): Response<Unit> {
        roomDeleteRequests += id
        return roomDeleteAnswer()
    }

    override suspend fun updateRoom(id: String, body: Map<String, Int>): Response<Unit> =
        error("The admin screen never changes a room")

    override suspend fun getSettings() = settingsAnswer()

    override suspend fun updateSettings(settings: AdminSettings): Response<Unit> {
        settingsSaveRequests += settings
        return settingsSaveAnswer()
    }

    override suspend fun listInviteTokens() = inviteTokensAnswer()

    override suspend fun createInviteToken(body: CreateInviteTokenRequest): Response<InviteToken> {
        tokenCreateRequests += body
        return tokenCreateAnswer()
    }

    override suspend fun deleteInviteToken(id: String): Response<Unit> {
        tokenDeleteRequests += id
        return tokenDeleteAnswer()
    }

    override suspend fun getOnlineCount() = onlineCountAnswer()
}

/**
 * The admin panel: who gets in, and that every change it asks the server for shows on screen only
 * once the server has made it. A refused ban, delete or setting must leave the screen as it was.
 */
class AdminScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val activeUser = AdminUser(id = "user-ada", email = "ada@example.invalid", name = "Ada Lovelace")
    private val bannedUser = activeUser.copy(isActive = false)
    private val grace = AdminUser(id = "user-grace", email = "grace@navy.example.invalid", name = "Grace Hopper")
    private val room = AdminRoom(id = "room-standup", name = "standup")
    private val token = InviteToken(id = "token-1", token = "0123456789abcdefTAIL", email = INVITE_EMAIL)

    private val api = FakeAdminApi()

    private fun string(resource: Int, vararg arguments: Any): String = context.getString(resource, *arguments)

    /** An [InstanceManager] whose only server has an account with [accesses] signed in. */
    private fun signedInWith(accesses: List<String>): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        AuthManager(context, TEST_INSTANCE_ID).saveUser(
            User(id = TEST_INSTANCE_ID, email = "owner@example.invalid", name = "Owner", accesses = accesses)
        )
        val recentRooms = RecentRoomsStore(context.getSharedPreferences(TEST_RECENTS_PREFS, Context.MODE_PRIVATE))
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context), recentRooms)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(MESSAGE_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Waits, at the pace of real time, for [text] to go. Unlike [waitForMessageToRunItsCourse] it skips
     * no time, so a message queued behind it has no chance to come and go unseen.
     */
    private fun waitForTextToGo(text: String) {
        compose.waitUntil(MESSAGE_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Waits for [message], then lets its time on screen run out. A failed call reports itself before
     * it returns, so what a refused change leaves on screen is settled only once its message has gone.
     */
    private fun waitForMessageToRunItsCourse(message: String) {
        waitForText(message)
        compose.mainClock.advanceTimeBy(MESSAGE_TIMEOUT_MILLIS)
        waitForTextToGo(message)
    }

    /** Waits for [message], and checks the tab's spinner had already cleared by the time it showed. */
    private fun assertReportedAfterLoading(message: String) {
        waitForText(message)
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }

    private fun typedText(): String =
        compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun confirm(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    private fun showOverview() {
        compose.setThemedContentAt(FontScales.Default) { AdminOverviewContent(adminApi = api) }
        compose.waitForIdle()
    }

    private fun showUsers(vararg users: AdminUser) {
        api.usersAnswer = { Response.success(UserListResponse(users.toList())) }
        compose.setThemedContentAt(FontScales.Default) { AdminUsersContent(adminApi = api) }
        compose.waitForIdle()
    }

    private fun showRooms(vararg rooms: AdminRoom) {
        api.roomsAnswer = { Response.success(RoomListResponse(rooms.toList())) }
        compose.setThemedContentAt(FontScales.Default) { AdminRoomsContent(adminApi = api) }
        compose.waitForIdle()
    }

    private fun showSettings(settings: AdminSettings = AdminSettings(), tokens: List<InviteToken> = emptyList()) {
        api.settingsAnswer = { Response.success(settings) }
        api.inviteTokensAnswer = { Response.success(TokenListResponse(tokens)) }
        compose.setThemedContentAt(FontScales.Default) { AdminSettingsContent(adminApi = api) }
        compose.waitForIdle()
    }

    // The registration card's two switches, in the order the card lists them.
    private fun registrationSwitch() = compose.onAllNodes(isToggleable())[0]

    private fun inviteOnlySwitch() = compose.onAllNodes(isToggleable())[1]

    @After
    fun forgetAccount() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
        context.deleteSharedPreferences(TEST_RECENTS_PREFS)
    }

    // ── Who gets in ──────────────────────────────────────────────────────────

    /** The server lets only a superadmin into its admin API, so a plain admin would find refusals. */
    @Test
    fun shouldKeepAnAccountThatIsNotSuperadminOut() {
        compose.setThemedContentAt(FontScales.Default) { AdminScreen(instanceManager = signedInWith(listOf(ADMIN_ACCESS))) }

        compose.onNodeWithText(string(R.string.admin_access_denied)).assertExists()
        compose.onNodeWithText(string(R.string.admin_tab_overview)).assertDoesNotExist()
    }

    @Test
    fun shouldOpenThePanelForASuperadmin() {
        compose.setThemedContentAt(FontScales.Default) { AdminScreen(instanceManager = signedInWith(listOf(SUPERADMIN_ACCESS))) }

        compose.onNodeWithText(string(R.string.admin_tab_overview)).assertExists()
        compose.onNodeWithText(string(R.string.admin_access_denied)).assertDoesNotExist()
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    @Test
    fun shouldCountEveryUserButOnlyLiveRooms() {
        api.usersAnswer = { Response.success(UserListResponse((1..4).map { number -> activeUser.copy(id = "user-$number") })) }
        api.roomsAnswer = {
            Response.success(RoomListResponse(listOf(room.copy(isActive = true), room.copy(id = "room-2"), room.copy(id = "room-3"))))
        }
        api.onlineCountAnswer = { onlineCount(7) }
        showOverview()

        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithText("3").assertDoesNotExist()
    }

    /** The server lists users oldest first; the card shows the five who signed up last, newest on top. */
    @Test
    fun shouldListTheFiveNewestSignupsNewestFirst() {
        api.usersAnswer = {
            Response.success(UserListResponse((1..6).map { number -> activeUser.copy(id = "user-$number", name = "Member $number") }))
        }
        showOverview()

        compose.onNodeWithText("Member 1").assertDoesNotExist()
        val newestTop = compose.onNodeWithText("Member 6").fetchSemanticsNode().boundsInRoot.top
        val oldestShownTop = compose.onNodeWithText("Member 2").fetchSemanticsNode().boundsInRoot.top
        assertTrue("the newest signup should sit above the oldest one shown", newestTop < oldestShownTop)
    }

    /** A server that is down fails all three loads; three messages in a row would hold the screen. */
    @Test
    fun shouldReportOnlyTheFirstOfSeveralFailedLoads() {
        api.usersAnswer = { refusal("users are unavailable") }
        api.roomsAnswer = { refusal("rooms are unavailable") }
        api.onlineCountAnswer = { refusal("the count is unavailable") }
        showOverview()

        assertReportedAfterLoading("users are unavailable")
        waitForTextToGo("users are unavailable")
        compose.waitForIdle()
        compose.onNodeWithText("rooms are unavailable").assertDoesNotExist()
        compose.onNodeWithText("the count is unavailable").assertDoesNotExist()
    }

    @Test
    fun shouldAskForTheOnlineCountAgainEveryThirtySeconds() {
        api.onlineCountAnswer = { onlineCount(7) }
        showOverview()
        api.onlineCountAnswer = { onlineCount(9) }

        compose.mainClock.advanceTimeBy(ONLINE_COUNT_REFRESH_MILLIS - 1_000)
        compose.onNodeWithText("7").assertExists()

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("9").assertExists()
    }

    /** The one silent failure on the panel: a message every thirty seconds would never leave the screen. */
    @Test
    fun shouldKeepTheLastOnlineCountQuietlyWhenARefreshFails() {
        api.onlineCountAnswer = { onlineCount(7) }
        showOverview()
        api.onlineCountAnswer = { throw IOException("the network went away") }

        compose.mainClock.advanceTimeBy(ONLINE_COUNT_REFRESH_MILLIS + 1_000)

        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithText("the network went away", substring = true).assertDoesNotExist()
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @Test
    fun shouldAskBeforeBanningAUser() {
        showUsers(activeUser)

        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_ban)).performClick()
        compose.onNodeWithText(string(R.string.admin_dialog_banMessage, activeUser.name)).assertExists()
        confirm(string(R.string.common_button_cancel))

        assertEquals(emptyList<Pair<String, Map<String, Boolean>>>(), api.userStatusRequests)
        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_ban)).assertExists()
    }

    @Test
    fun shouldMarkAUserBannedOnceTheServerAccepts() {
        showUsers(activeUser)

        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_ban)).performClick()
        confirm(string(R.string.admin_contentDescription_ban))

        assertEquals(listOf(activeUser.id to mapOf("active" to false)), api.userStatusRequests)
        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_unban)).assertExists()
    }

    @Test
    fun shouldLeaveAUserActiveWhenTheServerRefusesTheBan() {
        api.userStatusAnswer = { refusal("an admin cannot be banned") }
        showUsers(activeUser)

        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_ban)).performClick()
        confirm(string(R.string.admin_contentDescription_ban))

        waitForMessageToRunItsCourse("an admin cannot be banned")
        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_ban)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_unban)).assertDoesNotExist()
    }

    /** Unbanning only gives access back, so it is one tap, with no question first. */
    @Test
    fun shouldUnbanAUserWithoutAsking() {
        showUsers(bannedUser)

        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_unban)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(bannedUser.id to mapOf("active" to true)), api.userStatusRequests)
        compose.onNodeWithContentDescription(string(R.string.admin_contentDescription_ban)).assertExists()
    }

    @Test
    fun shouldFindAUserByNameInAnyCase() {
        showUsers(activeUser, grace)

        compose.onNode(hasSetTextAction()).performTextInput("HOPPER")

        compose.onNodeWithText(grace.name).assertExists()
        compose.onNodeWithText(activeUser.name).assertDoesNotExist()
    }

    @Test
    fun shouldFindAUserByEmail() {
        showUsers(activeUser, grace)

        compose.onNode(hasSetTextAction()).performTextInput("navy")

        compose.onNodeWithText(grace.name).assertExists()
        compose.onNodeWithText(activeUser.name).assertDoesNotExist()
    }

    @Test
    fun shouldReportAFailedUserListOnceItsSpinnerClears() {
        api.usersAnswer = { unexplainedFailure() }
        compose.setThemedContentAt(FontScales.Default) { AdminUsersContent(adminApi = api) }

        assertReportedAfterLoading(string(R.string.admin_error_loadUsersFailed))
    }

    // ── Rooms ────────────────────────────────────────────────────────────────

    @Test
    fun shouldAskBeforeDeletingARoom() {
        showRooms(room)

        compose.onNodeWithContentDescription(string(R.string.common_button_delete)).performClick()
        confirm(string(R.string.common_button_cancel))

        assertEquals(emptyList<String>(), api.roomDeleteRequests)
        compose.onNodeWithText(room.name).assertExists()
    }

    @Test
    fun shouldRemoveARoomOnceTheServerDeletesIt() {
        showRooms(room)

        compose.onNodeWithContentDescription(string(R.string.common_button_delete)).performClick()
        confirm(string(R.string.common_button_delete))

        assertEquals(listOf(room.id), api.roomDeleteRequests)
        compose.onNodeWithText(room.name).assertDoesNotExist()
    }

    @Test
    fun shouldKeepARoomListedWhenTheServerRefusesTheDelete() {
        api.roomDeleteAnswer = { unexplainedFailure() }
        showRooms(room)

        compose.onNodeWithContentDescription(string(R.string.common_button_delete)).performClick()
        confirm(string(R.string.common_button_delete))

        waitForMessageToRunItsCourse(string(R.string.admin_error_deleteRoomFailed))
        compose.onNodeWithText(room.name).assertExists()
    }

    @Test
    fun shouldReportAFailedRoomListOnceItsSpinnerClears() {
        api.roomsAnswer = { unexplainedFailure() }
        compose.setThemedContentAt(FontScales.Default) { AdminRoomsContent(adminApi = api) }

        assertReportedAfterLoading(string(R.string.admin_error_loadRoomsFailed))
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    @Test
    fun shouldTurnRegistrationOffOnceTheServerSavesIt() {
        showSettings(AdminSettings(registrationEnabled = true, tokenRegistrationOnly = true))

        registrationSwitch().performClick()
        compose.waitForIdle()

        assertEquals(listOf(AdminSettings(registrationEnabled = false, tokenRegistrationOnly = true)), api.settingsSaveRequests)
        registrationSwitch().assertIsOff()
    }

    @Test
    fun shouldLeaveRegistrationOnWhenTheServerRefusesTheChange() {
        api.settingsSaveAnswer = { unexplainedFailure() }
        showSettings(AdminSettings(registrationEnabled = true))

        registrationSwitch().performClick()

        waitForMessageToRunItsCourse(string(R.string.admin_error_saveSettingsFailed))
        registrationSwitch().assertIsOn()
    }

    @Test
    fun shouldTurnInviteOnlyOnOnceTheServerSavesIt() {
        showSettings(AdminSettings(registrationEnabled = false, tokenRegistrationOnly = false))

        inviteOnlySwitch().performClick()
        compose.waitForIdle()

        assertEquals(listOf(AdminSettings(registrationEnabled = false, tokenRegistrationOnly = true)), api.settingsSaveRequests)
        inviteOnlySwitch().assertIsOn()
    }

    @Test
    fun shouldLeaveInviteOnlyOffWhenTheServerRefusesTheChange() {
        api.settingsSaveAnswer = { refusal("settings are read-only here") }
        showSettings(AdminSettings(tokenRegistrationOnly = false))

        inviteOnlySwitch().performClick()

        waitForMessageToRunItsCourse("settings are read-only here")
        inviteOnlySwitch().assertIsOff()
    }

    @Test
    fun shouldShowANewTokenOnceTheServerCreatesIt() {
        api.tokenCreateAnswer = { Response.success(token) }
        showSettings()

        compose.onNode(hasSetTextAction()).performTextInput(INVITE_EMAIL)
        confirm(string(R.string.common_button_generate))

        assertEquals(
            listOf(CreateInviteTokenRequest(email = INVITE_EMAIL, expiresInHours = INVITE_TOKEN_LIFETIME_HOURS)),
            api.tokenCreateRequests
        )
        compose.onNodeWithText(token.token).assertExists()
        compose.onNodeWithText(SHORTENED_TOKEN).assertExists()
        assertEquals("", typedText())
    }

    @Test
    fun shouldAskForATokenWithNoEmailWhenTheFieldIsBlank() {
        api.tokenCreateAnswer = { Response.success(token.copy(email = null)) }
        showSettings()

        compose.onNode(hasSetTextAction()).performTextInput("   ")
        confirm(string(R.string.common_button_generate))

        assertEquals(
            listOf(CreateInviteTokenRequest(email = null, expiresInHours = INVITE_TOKEN_LIFETIME_HOURS)),
            api.tokenCreateRequests
        )
    }

    @Test
    fun shouldKeepTheEmailWhenTheServerRefusesAToken() {
        api.tokenCreateAnswer = { refusal("invites are turned off") }
        showSettings()

        compose.onNode(hasSetTextAction()).performTextInput(INVITE_EMAIL)
        confirm(string(R.string.common_button_generate))

        waitForMessageToRunItsCourse("invites are turned off")
        assertEquals(INVITE_EMAIL, typedText())
        compose.onNodeWithContentDescription(string(R.string.common_action_copy)).assertDoesNotExist()
    }

    @Test
    fun shouldAskBeforeDeletingAToken() {
        showSettings(tokens = listOf(token))

        compose.onNodeWithContentDescription(string(R.string.common_button_delete)).performClick()
        confirm(string(R.string.common_button_cancel))

        assertEquals(emptyList<String>(), api.tokenDeleteRequests)
        compose.onNodeWithText(SHORTENED_TOKEN).assertExists()
    }

    @Test
    fun shouldRemoveATokenOnceTheServerDeletesIt() {
        showSettings(tokens = listOf(token))

        compose.onNodeWithContentDescription(string(R.string.common_button_delete)).performClick()
        confirm(string(R.string.common_button_delete))

        assertEquals(listOf(token.id), api.tokenDeleteRequests)
        compose.onNodeWithText(SHORTENED_TOKEN).assertDoesNotExist()
    }

    @Test
    fun shouldKeepATokenListedWhenTheServerRefusesTheDelete() {
        api.tokenDeleteAnswer = { unexplainedFailure() }
        showSettings(tokens = listOf(token))

        compose.onNodeWithContentDescription(string(R.string.common_button_delete)).performClick()
        confirm(string(R.string.common_button_delete))

        waitForMessageToRunItsCourse(string(R.string.admin_error_deleteTokenFailed))
        compose.onNodeWithText(SHORTENED_TOKEN).assertExists()
    }

    @Test
    fun shouldReportOnlyTheFirstOfTwoFailedSettingsLoads() {
        api.settingsAnswer = { refusal("settings are unavailable") }
        api.inviteTokensAnswer = { refusal("invites are unavailable") }
        compose.setThemedContentAt(FontScales.Default) { AdminSettingsContent(adminApi = api) }

        waitForText("settings are unavailable")
        waitForTextToGo("settings are unavailable")
        compose.waitForIdle()
        compose.onNodeWithText("invites are unavailable").assertDoesNotExist()
    }
}
