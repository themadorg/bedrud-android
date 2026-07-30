package com.bedrud.app.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.bedrud.app.R
import com.bedrud.app.core.call.CallService
import com.bedrud.app.core.deeplink.BedrudURLParser
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.recent.RecentRoom
import com.bedrud.app.core.recent.RecentRoomsStore
import com.bedrud.app.core.recent.formatRecentRoomTimeAgo
import com.bedrud.app.core.recent.recentRoomsNotInApiList
import com.bedrud.app.core.rooms.DeletedRoomTombstones
import com.bedrud.app.models.CreateRoomRequest
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.models.UpdateRoomSettingsRequest
import com.bedrud.app.models.User
import com.bedrud.app.models.UserRoomResponse
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudCompactTopBar
import com.bedrud.app.ui.components.BedrudOutlinedCard
import com.bedrud.app.ui.components.BedrudSnackbarHost
import com.bedrud.app.ui.components.BedrudTextField
import com.bedrud.app.ui.components.BedrudTabScaffoldContentInsets
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val AUTO_REFRESH_INTERVAL_MS = 60_000L

// A failed fetch retries on this short delay instead of waiting out the full refresh interval,
// so a flaky request doesn't leave the list stale for a minute.
private const val FAILED_FETCH_RETRY_MS = 5_000L

// ── Filter state ─────────────────────────────────────────────────────────────

// ALL merges the active server's rooms with recent rooms from every server (recency/live first);
// MY_ROOMS is the subset the signed-in user created on the active server.
private enum class RoomFilter { ALL, MY_ROOMS }

private sealed interface RoomListEntry {
    data class FromApi(val room: UserRoomResponse) : RoomListEntry
    data class FromRecent(val recent: RecentRoom) : RoomListEntry
}

// ── Screen entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    modifier: Modifier = Modifier,
    onJoinRoom: (String) -> Unit,
    onJoinRecent: (RecentRoom) -> Unit,
    onOpenProfile: () -> Unit,
    instanceManager: InstanceManager = koinInject(),
    recentRoomsStore: RecentRoomsStore = koinInject(),
) {
    val roomApi = instanceManager.roomApi.collectAsState().value ?: return
    val authManager = instanceManager.authManager.collectAsState().value
    val currentUser by (authManager?.currentUser ?: MutableStateFlow(null)).collectAsState()
    val recentRooms by recentRoomsStore.rooms.collectAsState()
    val instances by instanceManager.store.instances.collectAsState()
    val activeInstanceId by instanceManager.store.activeInstanceId.collectAsState()
    val activeInstance = remember(instances, activeInstanceId) {
        instances.firstOrNull { it.id == activeInstanceId }
    }
    val activeServerName = activeInstance?.displayName
    // Active-server recents keyed by room name, so a server-backed card can tell when the user was
    // last in that room -- driving the Live / "Xm ago" presence label without a scan per card.
    val activeRecentByName = remember(recentRooms, activeInstanceId) {
        recentRooms.filter { it.instanceId == activeInstanceId }.associateBy { it.roomName }
    }
    fun lastVisitFor(roomName: String): Long? =
        activeRecentByName[roomName]?.let { it.leftAt ?: it.joinedAt }
    fun isOngoingFor(roomName: String): Boolean =
        CallService.isRunning &&
            CallService.activeRoomName == roomName &&
            CallService.activeInstanceId == activeInstanceId

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var rooms by remember { mutableStateOf<List<UserRoomResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastFetchAtMs by remember { mutableLongStateOf(0L) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var roomToEdit by remember { mutableStateOf<UserRoomResponse?>(null) }
    var roomToDelete by remember { mutableStateOf<UserRoomResponse?>(null) }
    var activeFilter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }
    var quickJoinText by remember { mutableStateOf("") }
    // Captured here (not in the join callback) because stringResource is composition-only.
    val invalidJoinInputMessage = stringResource(R.string.dashboard_join_invalidInput)
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    // Survives the dispose/recompose Navigation does when leaving for MeetingScreen and
    // coming back via Back -- listState's own scroll position is restored by that same
    // mechanism, so without this a newly created room can land above the restored scroll
    // offset in a long list, out of view until the user manually scrolls up.
    // Holds the just-created room's name (not just a boolean) so the wait-for-data check
    // below can confirm *that room* has actually arrived, rather than firing as soon as
    // the tab's list is merely non-empty -- which, for the server-backed My Rooms tab,
    // is true immediately from pre-existing rooms, well before the async refetch includes it.
    var pendingScrollToTopFor by rememberSaveable { mutableStateOf<String?>(null) }

    // Drives the "Xm ago" labels on recent-room cards. Compose only recomposes on state
    // change, so without an explicit ticking clock those labels freeze at whatever they
    // read on the last recomposition instead of advancing with real time.
    var nowTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowTickMs = System.currentTimeMillis()
        }
    }

    // Steers the auto-refresh loop onto the short retry delay after a failure.
    var lastFetchFailed by remember { mutableStateOf(false) }

    // Returns an error message on failure, or null on success. Resolves the client from the flow
    // at call time so a long-lived caller (the auto-refresh loop) can never fetch through a stale
    // client after the active instance's clients are rebuilt.
    suspend fun fetchRooms(): String? {
        val api = instanceManager.roomApi.value ?: return null
        return try {
            val response = api.listRooms()
            if (response.isSuccessful) {
                val body = response.body() ?: emptyList()
                // Drop dead rooms the server still returns: ones already stamped deletedAt
                // (room/list never filters them) and ones deleted from this device moments ago
                // that the async delete hasn't stamped yet.
                rooms = body.filterNot { room ->
                    room.deletedAt != null || DeletedRoomTombstones.isTombstoned(room.id)
                }
                // Self-heal recents: a deleted room also lingers as a recent card (local history
                // the deletedAt filter above can't reach). Whenever the active server reports one
                // of its rooms deleted, drop the matching recent — clearing it on this server now,
                // and clearing a cross-server one the next time that server is the active one.
                val activeId = activeInstanceId
                if (activeId != null) {
                    val deletedNames = body.filter { it.deletedAt != null }.map { it.name }.toSet()
                    if (deletedNames.isNotEmpty()) {
                        recentRoomsStore.rooms.value
                            .filter { it.instanceId == activeId && it.roomName in deletedNames }
                            .forEach { recentRoomsStore.remove(it.roomName, it.instanceId) }
                    }
                }
                lastFetchAtMs = System.currentTimeMillis()
                lastFetchFailed = false
                null
            } else {
                lastFetchFailed = true
                "Failed to load rooms"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Never swallow cancellation: a cancelled fetch must die quietly, not be recorded as
            // a network failure (which would put the refresh loop on the fast retry cadence).
            throw e
        } catch (e: Exception) {
            lastFetchFailed = true
            e.message ?: "Failed to load rooms"
        }
    }

    fun loadRooms() {
        scope.launch {
            isLoading = true
            fetchRooms()?.let { snackbarHostState.showSnackbar(it) }
            isLoading = false
        }
    }

    fun refreshRooms() {
        nowTickMs = System.currentTimeMillis()
        scope.launch {
            isRefreshing = true
            fetchRooms()?.let { snackbarHostState.showSnackbar(it) }
            isRefreshing = false
        }
    }

    // Background refresh triggered by natural events (screen/app resumed). Skipped
    // while a fetch is already in flight, a dialog is open (so we don't yank the
    // room list out from under an in-progress edit/delete), or the last successful
    // fetch was too recent — and fails without surfacing a snackbar, so flaky
    // connectivity or rapid tab switching doesn't spam the user or hammer the server.
    fun silentlyRefreshRooms() {
        if (isLoading || isRefreshing) return
        if (showCreateDialog || roomToEdit != null || roomToDelete != null) return
        if (System.currentTimeMillis() - lastFetchAtMs < 3_000L) return
        scope.launch { fetchRooms() }
    }

    // Keyed on the active instance ID — a stable String — NEVER on the Retrofit client object:
    // Retrofit's dynamic proxies route Object.equals through their invocation handler and are
    // not even equal to themselves, so a proxy key restarts the effect on every recomposition.
    // With the empty+reload body below, that fed itself (each restart invalidates, scheduling
    // the next recomposition) into an endless visible reload storm. The String key restarts the
    // load only when the active server actually changes, dropping the old server's rooms and
    // refetching immediately instead of showing them until the next tick.
    LaunchedEffect(activeInstanceId) {
        rooms = emptyList()
        loadRooms()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { silentlyRefreshRooms() }

    // Keep the list self-healing against server-side eventual consistency (e.g. a
    // just-created room not yet reflected in listRooms()) without requiring the user
    // to background/foreground the app or pull to refresh. A failed fetch retries on
    // the short delay rather than waiting out the full interval.
    LaunchedEffect(activeInstanceId) {
        while (true) {
            delay(if (lastFetchFailed) FAILED_FETCH_RETRY_MS else AUTO_REFRESH_INTERVAL_MS)
            silentlyRefreshRooms()
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                scope.launch {
                    try {
                        val response = roomApi.createRoom(
                            CreateRoomRequest(
                                name = name.ifBlank { null },
                                // Sent explicitly rather than left to server defaults: 0 is
                                // the server's own convention for "unlimited" (see
                                // AddParticipantWithCapacityCheck), there's no UI for this
                                // yet so we're not narrowing it by accident.
                                maxParticipants = 0,
                                isPublic = true,
                                // Only "standard" exists in the app today; sent explicitly
                                // so adding the other two modes later is a one-line change
                                // here instead of relying on the server's own default.
                                mode = "standard",
                                settings = RoomSettings(
                                    allowChat = true,
                                    allowVideo = true,
                                    allowAudio = true,
                                    requireApproval = false,
                                    e2ee = false,
                                    // Server force-overrides this to false for non-superadmins
                                    // anyway; sent explicitly so the request isn't relying on
                                    // that server-side behavior to stay correct.
                                    isPersistent = false,
                                    // Locked off in the UI for now -- see RoomSettingsDialog.
                                    recordingsAllowed = false,
                                )
                            )
                        )
                        if (response.isSuccessful) {
                            val room = response.body()!!
                            showCreateDialog = false
                            pendingScrollToTopFor = room.name
                            loadRooms()
                            onJoinRoom(room.name)
                        } else {
                            snackbarHostState.showSnackbar("Failed to create room")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "Failed to create room")
                    }
                }
            }
        )
    }

    roomToDelete?.let { room ->
        val title = room.name.ifEmpty { room.id }
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = { Text(stringResource(R.string.dashboard_dialog_deleteTitle)) },
            text = {
                Text(stringResource(R.string.dashboard_dialog_deleteMessage, title))
            },
            confirmButton = {
                BedrudButton(
                    text = stringResource(R.string.common_button_delete),
                    variant = BedrudButtonVariant.DESTRUCTIVE,
                    onClick = {
                        val deleting = room
                        roomToDelete = null
                        scope.launch {
                            try {
                                val response = roomApi.deleteRoom(deleting.id)
                                if (response.isSuccessful) {
                                    // Keep refreshes from resurrecting it while the server's
                                    // async delete catches up...
                                    DeletedRoomTombstones.add(deleting.id)
                                    rooms = rooms.filter { it.id != deleting.id }
                                    // ...and drop its local recent entry, or the All tab would
                                    // immediately weave the deleted room back in as a recent card.
                                    if (deleting.name.isNotEmpty()) {
                                        activeInstanceId?.let {
                                            recentRoomsStore.remove(deleting.name, it)
                                        }
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Failed to delete room")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Failed to delete room")
                            }
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { roomToDelete = null }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
        )
    }

    roomToEdit?.let { room ->
        RoomSettingsDialog(
            room = room,
            onDismiss = { roomToEdit = null },
            onSave = { isPublic, settings ->
                scope.launch {
                    try {
                        val response = roomApi.updateRoomSettings(
                            room.id,
                            UpdateRoomSettingsRequest(isPublic = isPublic, settings = settings)
                        )
                        if (response.isSuccessful) {
                            // Apply locally before the async loadRooms() refetch lands, so
                            // reopening this room's settings (or reading its card) right away
                            // reflects what was just saved instead of the pre-save snapshot.
                            rooms = rooms.map {
                                if (it.id == room.id) it.copy(isPublic = isPublic, settings = settings) else it
                            }
                            roomToEdit = null
                            loadRooms()
                            snackbarHostState.showSnackbar("Settings saved")
                        } else {
                            snackbarHostState.showSnackbar("Failed to save settings")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "Failed to save settings")
                    }
                }
            }
        )
    }

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val filteredRooms = remember(rooms, activeFilter, currentUser, activeRecentByName) {
        when (activeFilter) {
            // Same recency order as the All tab: most-recently-used first, rooms never joined from
            // this device last (stable sort keeps those in their existing server order).
            RoomFilter.MY_ROOMS ->
                rooms.filter { it.createdBy == currentUser?.id }
                    .sortedByDescending {
                        activeRecentByName[it.name]?.let { r -> r.leftAt ?: r.joinedAt } ?: Long.MIN_VALUE
                    }
            RoomFilter.ALL -> rooms
        }
    }

    // One recency-ordered list: every room with local history — whether it renders as an API card
    // (active server) or a recent card (other servers / not yet in the API list) — is positioned
    // by when it was last used. This keeps a room's rank stable across server switches; the old
    // recents-first-then-server-order split made the same room jump sections (and the list
    // visibly reshuffle) every time the active server changed. Server rooms never joined from
    // this device have no recency, so they follow at the end in server order.
    val allTabEntries = remember(rooms, recentRooms, activeInstanceId) {
        val recencyByName = recentRooms
            .filter { it.instanceId == activeInstanceId }
            .associate { it.roomName to (it.leftAt ?: it.joinedAt) }
        val recentOnly = recentRoomsNotInApiList(
            recentRooms,
            rooms.map { it.name }.toSet(),
            activeInstanceId,
        )
        val dated = recentOnly.map { RoomListEntry.FromRecent(it) to (it.leftAt ?: it.joinedAt) } +
            rooms.mapNotNull { room ->
                recencyByName[room.name]?.let { RoomListEntry.FromApi(room) to it }
            }
        val neverJoined = rooms.filter { it.name !in recencyByName }.map { RoomListEntry.FromApi(it) }
        dated.sortedByDescending { (_, lastUsedAt) -> lastUsedAt }.map { (entry, _) -> entry } +
            neverJoined
    }

    LaunchedEffect(pendingScrollToTopFor, activeFilter, recentRooms, filteredRooms, allTabEntries) {
        val targetRoomName = pendingScrollToTopFor ?: return@LaunchedEffect
        val targetRoomVisibleInCurrentTab = when (activeFilter) {
            RoomFilter.MY_ROOMS -> filteredRooms.any { it.name == targetRoomName }
            RoomFilter.ALL -> allTabEntries.any { entry ->
                when (entry) {
                    is RoomListEntry.FromApi -> entry.room.name == targetRoomName
                    is RoomListEntry.FromRecent -> entry.recent.roomName == targetRoomName
                }
            }
        }
        if (targetRoomVisibleInCurrentTab) {
            listState.scrollToItem(0)
            pendingScrollToTopFor = null
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = BedrudTabScaffoldContentInsets,
        topBar = {
            BedrudCompactTopBar(
                actions = { ProfileAvatarButton(user = currentUser, onClick = onOpenProfile) },
                title = { RoomsHeaderTitle(serverName = activeServerName) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dashboard_contentDescription_createRoom)) }
        },
        snackbarHost = { BedrudSnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshRooms() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isLoading && rooms.isEmpty() && recentRooms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                // Header height, captured so the empty state below can be centered against the
                // full screen rather than just the space left over beneath it.
                var quickJoinHeightPx by remember { mutableIntStateOf(0) }
                var filterRowHeightPx by remember { mutableIntStateOf(0) }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ── Quick join bar ────────────────────────────────
                    QuickJoinBar(
                        value = quickJoinText,
                        onValueChange = { quickJoinText = it },
                        onJoin = {
                            // Dismiss the keyboard first: it satisfies the "action key hides the
                            // keyboard" rule and, on failure, keeps the snackbar from rendering
                            // hidden behind the IME.
                            focusManager.clearFocus()
                            val roomName = BedrudURLParser.parseJoinInput(quickJoinText)
                            if (!roomName.isNullOrBlank()) {
                                quickJoinText = ""
                                onJoinRoom(roomName)
                            } else {
                                // Input didn't resolve to a room (e.g. a URL with no /m/ or /c/):
                                // tell the user instead of the button appearing to do nothing.
                                scope.launch { snackbarHostState.showSnackbar(invalidJoinInputMessage) }
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = Dimens.space16, vertical = Dimens.space4)
                            .onGloballyPositioned { quickJoinHeightPx = it.size.height }
                    )

                    // ── Filter chips ──────────────────────────────────
                    FilterRow(
                        activeFilter = activeFilter,
                        onFilterChange = { activeFilter = it },
                        modifier = Modifier
                            .padding(horizontal = Dimens.space16, vertical = Dimens.space4)
                            .onGloballyPositioned { filterRowHeightPx = it.size.height }
                    )

                    // ── Room list ─────────────────────────────────────
                    val isCurrentTabEmpty = when (activeFilter) {
                        RoomFilter.ALL -> allTabEntries.isEmpty() && !isLoading
                        RoomFilter.MY_ROOMS -> filteredRooms.isEmpty() && !isLoading
                    }

                    if (isCurrentTabEmpty) {
                        // Centering only within this leftover space (below the header) would pull
                        // the icon+text+button group noticeably above the true center of the
                        // screen, since nothing balances the header's height at the bottom. Nudge
                        // the group up by half the header height so its own midpoint lands on the
                        // screen's midpoint instead.
                        val pullUp = with(LocalDensity.current) {
                            ((quickJoinHeightPx + filterRowHeightPx) / 2).toDp()
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(modifier = Modifier.offset(y = -pullUp)) {
                                EmptyState(
                                    hasFilter = activeFilter == RoomFilter.MY_ROOMS,
                                    onCreateRoom = { showCreateDialog = true },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = if (isKeyboardVisible) Dimens.space16 else Dimens.roomListBottomSpace,
                            )
                        ) {
                            if (activeFilter == RoomFilter.ALL) {
                                items(
                                    allTabEntries,
                                    key = { entry ->
                                        when (entry) {
                                            is RoomListEntry.FromApi -> "api:${entry.room.id}"
                                            is RoomListEntry.FromRecent ->
                                                "recent:${entry.recent.instanceId}:${entry.recent.roomName}"
                                        }
                                    },
                                ) { entry ->
                                    when (entry) {
                                        is RoomListEntry.FromApi -> RoomCard(
                                            room = entry.room,
                                            isOwner = entry.room.createdBy == currentUser?.id,
                                            isOngoing = isOngoingFor(entry.room.name),
                                            lastVisitAtMs = lastVisitFor(entry.room.name),
                                            now = nowTickMs,
                                            onJoin = { onJoinRoom(entry.room.name) },
                                            onDelete = { roomToDelete = entry.room },
                                            onSettings = if (entry.room.createdBy == currentUser?.id) {
                                                { roomToEdit = entry.room }
                                            } else null,
                                            modifier = Modifier.padding(horizontal = Dimens.space16, vertical = Dimens.space4),
                                        )
                                        is RoomListEntry.FromRecent -> RecentRoomCard(
                                            recent = entry.recent,
                                            now = nowTickMs,
                                            onJoin = { onJoinRecent(entry.recent) },
                                            onRemove = {
                                                recentRoomsStore.remove(
                                                    entry.recent.roomName,
                                                    entry.recent.instanceId,
                                                )
                                            },
                                            modifier = Modifier.padding(horizontal = Dimens.space16, vertical = Dimens.space4),
                                        )
                                    }
                                }
                            } else {
                                items(filteredRooms, key = { it.id }) { room ->
                                    RoomCard(
                                        room = room,
                                        isOwner = room.createdBy == currentUser?.id,
                                        isOngoing = isOngoingFor(room.name),
                                        lastVisitAtMs = lastVisitFor(room.name),
                                        now = nowTickMs,
                                        onJoin = { onJoinRoom(room.name) },
                                        onDelete = { roomToDelete = room },
                                        onSettings = if (room.createdBy == currentUser?.id) {
                                            { roomToEdit = room }
                                        } else null,
                                        modifier = Modifier.padding(horizontal = Dimens.space16, vertical = Dimens.space4)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

/** Rooms header: the active server's name + "rooms", in a single neutral tone. */
@Composable
private fun RoomsHeaderTitle(serverName: String?) {
    val name = serverName ?: stringResource(R.string.instance_default_displayName)
    val suffix = stringResource(R.string.dashboard_header_roomsSuffix)
    Text(
        text = "$name $suffix",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Circular profile shortcut in the top bar — the user's photo, or their colored initial. */
@Composable
private fun ProfileAvatarButton(user: User?, onClick: () -> Unit) {
    val desc = stringResource(R.string.dashboard_contentDescription_profile)
    val avatarUrl = user?.avatarUrl
    IconButton(onClick = onClick, modifier = Modifier.size(Dimens.minTouchTarget)) {
        Box(
            modifier = Modifier
                .size(Dimens.avatarLg)
                .clip(BedrudShapeTokens.pill)
                .background(MaterialTheme.colorScheme.primary)
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(BedrudShapeTokens.pill),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = (user?.name?.take(1) ?: "?").uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ── Quick join bar ────────────────────────────────────────────────────────────

@Composable
private fun QuickJoinBar(
    value: String,
    onValueChange: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Both the field and the button sit at the compact 48dp control height with the shared
        // corner token, so the row reads as one control without dominating the header area.
        BedrudTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.dashboard_placeholder_search),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(Dimens.iconSm)) },
            textStyle = MaterialTheme.typography.bodyMedium,
            // Room slugs and links are lowercase, no spaces — suppress auto-capitalize/correct and
            // use the URL keyboard. The "Go" key joins (and, on success, the navigation dismisses it).
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onJoin() }),
            textDirection = TextDirection.Ltr,
            modifier = Modifier.weight(1f).height(Dimens.buttonHeight)
        )
        Spacer(modifier = Modifier.width(Dimens.space8))
        BedrudButton(
            text = stringResource(R.string.common_button_join),
            onClick = onJoin,
            variant = BedrudButtonVariant.TONAL,
            enabled = value.isNotBlank(),
        )
    }
}

// ── Filter chips ────────────────────────────────────────────────────────────

@Composable
private fun FilterRow(
    activeFilter: RoomFilter,
    onFilterChange: (RoomFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.space8)) {
        RoomFilter.entries.forEach { filter ->
            FilterChip(
                selected = activeFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            RoomFilter.ALL -> stringResource(R.string.dashboard_filter_all)
                            RoomFilter.MY_ROOMS -> stringResource(R.string.dashboard_filter_myRooms)
                        }
                    )
                }
            )
        }
    }
}

// ── Room card (server-backed) ─────────────────────────────────────────────────

@Composable
private fun RoomCard(
    room: UserRoomResponse,
    isOwner: Boolean,
    isOngoing: Boolean,
    lastVisitAtMs: Long?,
    now: Long,
    onJoin: () -> Unit,
    onDelete: () -> Unit,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val title = room.name.ifEmpty {
        val parts = room.id.split("-")
        if (parts.size >= 2) "${parts[0]}-${parts[1]}" else room.id
    }

    val presence = presenceFor(isOngoing = isOngoing, lastVisitAtMs = lastVisitAtMs, now = now)
    val statusTint by animateColorAsState(
        targetValue = if (presence?.isLive == true) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.durationLong),
        label = "statusTint"
    )
    // Presence and the private tag are each optional; join whichever are present, or drop the line.
    val privateLabel =
        if (room.isPublic == false) stringResource(R.string.dashboard_feature_private) else null
    val metaText = listOfNotNull(presence?.text, privateLabel).joinToString(" · ").ifEmpty { null }

    // Only rooms the user owns get the swipe-to-delete affordance (the server rejects other deletes).
    val swipeAction = if (isOwner) {
        SwipeAction(
            label = stringResource(R.string.common_button_delete),
            icon = Icons.Default.Delete,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            // Route through the confirm dialog (destructive), so snap back rather than dismiss.
            onTriggered = { onDelete(); false },
        )
    } else null

    SwipeableRoomRow(action = swipeAction, modifier = modifier.fillMaxWidth()) {
        RoomCardScaffold(onClick = onJoin) {
            Column(modifier = Modifier.weight(1f)) {
                RoomTitleLine(title = title)
                if (metaText != null) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (onSettings != null) {
                IconButton(onClick = onSettings, modifier = Modifier.size(Dimens.minTouchTarget)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.dashboard_contentDescription_settings),
                        modifier = Modifier.size(Dimens.iconSm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TrailingChevron()
        }
    }
}

// ── Recent room card (link-joined on this server, from local history) ──────────

@Composable
private fun RecentRoomCard(
    recent: RecentRoom,
    now: Long,
    onJoin: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOngoing = CallService.isRunning &&
        CallService.activeRoomName == recent.roomName &&
        CallService.activeInstanceId == recent.instanceId
    val presence = presenceFor(
        isOngoing = isOngoing,
        lastVisitAtMs = recent.leftAt ?: recent.joinedAt,
        now = now,
    )
    val statusTint by animateColorAsState(
        targetValue = if (presence?.isLive == true) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.durationLong),
        label = "recentStatusTint"
    )

    val swipeAction = SwipeAction(
        label = stringResource(R.string.dashboard_action_remove),
        icon = Icons.Default.Close,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        // Non-destructive (only drops it from local history): remove immediately and dismiss.
        onTriggered = { onRemove(); true },
    )

    SwipeableRoomRow(action = swipeAction, modifier = modifier.fillMaxWidth()) {
        RoomCardScaffold(onClick = onJoin) {
            Column(modifier = Modifier.weight(1f)) {
                RoomTitleLine(title = recent.roomName)
                if (presence != null) {
                    Text(
                        text = presence.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            TrailingChevron()
        }
    }
}

// ── Presence ───────────────────────────────────────────────────────────────────

/** A card's presence label plus whether it's the "Live" state (so the caller can tint it). */
private data class Presence(val text: String, val isLive: Boolean)

// The user counts as "Live" while in the room or within a minute of leaving; after that we show
// how long ago they were last in.
private const val LIVE_WINDOW_MS = 60_000L

// Two presence states only. Null means the user has never joined this room — a server-backed card
// with no local history shows no presence line at all.
@Composable
private fun presenceFor(isOngoing: Boolean, lastVisitAtMs: Long?, now: Long): Presence? {
    val isLive = isOngoing || (lastVisitAtMs != null && now - lastVisitAtMs < LIVE_WINDOW_MS)
    return when {
        isLive -> Presence(stringResource(R.string.dashboard_status_live), isLive = true)
        lastVisitAtMs != null -> Presence(
            // "%1$s ago" — the connective is localized; the compact duration ("12m", "3h") is not.
            stringResource(R.string.dashboard_status_timeAgo, formatRecentRoomTimeAgo(lastVisitAtMs, now)),
            isLive = false,
        )
        else -> null
    }
}

// ── Shared card pieces ─────────────────────────────────────────────────────────

/** Plain outlined card holding one room row. */
@Composable
private fun RoomCardScaffold(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    BedrudOutlinedCard(
        onClick = onClick,
        shape = BedrudShapeTokens.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Uniform card height whether or not the card carries a trailing 48dp control
                // (the settings button would otherwise inflate owned cards).
                .heightIn(min = Dimens.roomCardMinHeight)
                .padding(start = Dimens.space16, end = Dimens.space4, top = Dimens.space12, bottom = Dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Line 1 of a room card: the room name in monospace. */
@Composable
private fun RoomTitleLine(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            textDirection = TextDirection.Ltr,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrailingChevron() {
    Icon(
        Icons.Default.ChevronRight,
        contentDescription = null,
        modifier = Modifier
            .padding(end = Dimens.space6)
            .size(Dimens.iconMd),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Swipe-to-action ────────────────────────────────────────────────────────────

private data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    // Returns true to let the row dismiss (instant action), false to snap back (deferred/confirmed).
    val onTriggered: () -> Boolean,
)

/** Wraps a card in a leading-edge swipe gesture that reveals [action]; no swipe when it's null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRoomRow(
    action: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (action == null) {
        Box(modifier = modifier) { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) action.onTriggered() else false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { SwipeActionBackground(action, state) },
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionBackground(action: SwipeAction, state: SwipeToDismissBoxState) {
    // Only paint the panel while it's the gesture's target — a settled row shows nothing behind it.
    val revealed = state.targetValue == SwipeToDismissBoxValue.EndToStart
    val container = if (revealed) action.containerColor else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(BedrudShapeTokens.card)
            .background(container)
            .padding(horizontal = Dimens.space20),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            Text(action.label, style = MaterialTheme.typography.labelLarge, color = action.contentColor)
            Icon(action.icon, contentDescription = null, tint = action.contentColor, modifier = Modifier.size(Dimens.iconSm))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasFilter: Boolean, onCreateRoom: () -> Unit) {
    // One consistent gap between icon, phrase, and call to action.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.space16),
    ) {
        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Text(
            text = if (hasFilter) stringResource(R.string.dashboard_empty_noMatch) else stringResource(R.string.dashboard_empty_noRooms),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasFilter) {
            BedrudButton(
                text = stringResource(R.string.dashboard_button_createFirstRoom),
                onClick = onCreateRoom,
                variant = BedrudButtonVariant.OUTLINE
            )
        }
    }
}

// ── Create room dialog ────────────────────────────────────────────────────────

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var roomName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_dialog_createTitle)) },
        text = {
            Column {
                Text(stringResource(R.string.dashboard_dialog_createDescription),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(Dimens.space16))
                BedrudTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = stringResource(R.string.dashboard_label_roomName),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    textDirection = TextDirection.Ltr
                )
            }
        },
        confirmButton = {
            BedrudButton(
                text = stringResource(R.string.common_button_create),
                variant = BedrudButtonVariant.TONAL,
                onClick = { onCreate(roomName) },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_button_cancel)) } }
    )
}
