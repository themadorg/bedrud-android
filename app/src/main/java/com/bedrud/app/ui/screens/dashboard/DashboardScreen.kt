package com.bedrud.app.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.bedrud.app.ui.components.BedrudTabScaffoldContentInsets
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.theme.parseInstanceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val AUTO_REFRESH_INTERVAL_MS = 60_000L

// A failed fetch retries on this short delay instead of waiting out the full refresh interval,
// so a flaky request doesn't leave the list stale for a minute.
private const val FAILED_FETCH_RETRY_MS = 5_000L

// How long a deleted room's id keeps being filtered out of fetch results. The server's list
// endpoint can still return a just-deleted room for a while (eventual consistency), which would
// otherwise resurrect it on the next background refresh.
private const val DELETED_ROOM_TOMBSTONE_MS = 10 * 60_000L

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
    // Resolves a stored recent's color; falls back to the live instance, then a neutral color.
    fun colorForRecent(recent: RecentRoom): Color =
        parseInstanceColor(
            recent.instanceColorHex ?: instances.firstOrNull { it.id == recent.instanceId }?.iconColorHex,
        )
    val activeServerColor = parseInstanceColor(activeInstance?.iconColorHex)
    val activeServerName = activeInstance?.displayName

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var rooms by remember { mutableStateOf<List<UserRoomResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastFetchAtMs by remember { mutableLongStateOf(0L) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var roomToEdit by remember { mutableStateOf<UserRoomResponse?>(null) }
    var roomToDelete by remember { mutableStateOf<UserRoomResponse?>(null) }
    // A cross-server recent the user tapped: hold it here to confirm the server switch before joining.
    var pendingSwitchJoin by remember { mutableStateOf<RecentRoom?>(null) }
    var activeFilter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }
    var quickJoinText by remember { mutableStateOf("") }
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

    // Recently deleted room ids -> deletion time. Not compose state: it never drives UI directly,
    // it only filters what fetchRooms() accepts from the server.
    val deletedRoomTombstones = remember { mutableMapOf<String, Long>() }

    // Steers the auto-refresh loop onto the short retry delay after a failure.
    var lastFetchFailed by remember { mutableStateOf(false) }

    // Returns an error message on failure, or null on success.
    suspend fun fetchRooms(): String? {
        return try {
            val response = roomApi.listRooms()
            if (response.isSuccessful) {
                val now = System.currentTimeMillis()
                deletedRoomTombstones.entries.removeAll { now - it.value > DELETED_ROOM_TOMBSTONE_MS }
                rooms = (response.body() ?: emptyList())
                    .filterNot { it.id in deletedRoomTombstones }
                lastFetchAtMs = now
                lastFetchFailed = false
                null
            } else {
                lastFetchFailed = true
                "Failed to load rooms"
            }
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

    // Keyed on the API client: switching the active server rebuilds it, and the list must drop
    // the old server's rooms and refetch immediately instead of showing them until the next tick.
    LaunchedEffect(roomApi) {
        rooms = emptyList()
        loadRooms()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { silentlyRefreshRooms() }

    // Keep the list self-healing against server-side eventual consistency (e.g. a
    // just-created room not yet reflected in listRooms()) without requiring the user
    // to background/foreground the app or pull to refresh. A failed fetch retries on
    // the short delay rather than waiting out the full interval.
    LaunchedEffect(roomApi) {
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
                TextButton(
                    onClick = {
                        val deleting = room
                        roomToDelete = null
                        scope.launch {
                            try {
                                val response = roomApi.deleteRoom(deleting.id)
                                if (response.isSuccessful) {
                                    // Keep refreshes from resurrecting it while the server's list
                                    // endpoint catches up...
                                    deletedRoomTombstones[deleting.id] = System.currentTimeMillis()
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
                ) {
                    Text(
                        stringResource(R.string.common_button_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToDelete = null }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
        )
    }

    pendingSwitchJoin?.let { recent ->
        AlertDialog(
            onDismissRequest = { pendingSwitchJoin = null },
            title = { Text(stringResource(R.string.dashboard_dialog_switchServerTitle)) },
            text = {
                Text(stringResource(R.string.dashboard_dialog_switchServerMessage, recent.instanceName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = recent
                        pendingSwitchJoin = null
                        onJoinRecent(target)
                    },
                ) { Text(stringResource(R.string.dashboard_button_switchAndJoin)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchJoin = null }) {
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

    val filteredRooms = remember(rooms, activeFilter, currentUser) {
        when (activeFilter) {
            RoomFilter.MY_ROOMS -> rooms.filter { it.createdBy == currentUser?.id }
            RoomFilter.ALL -> rooms
        }
    }

    val allTabEntries = remember(rooms, recentRooms, activeInstanceId) {
        val recentOnly = recentRoomsNotInApiList(
            recentRooms,
            rooms.map { it.name }.toSet(),
            activeInstanceId,
        )
        // recentOnly first: those entries exist precisely because they're newer than the
        // last successful server sync (e.g. a just-created room), so they belong ahead of
        // the confirmed list, not appended after it -- keeps "newest first" true here the
        // same way RecentRoomsStore.add() already keeps it true.
        recentOnly.map { RoomListEntry.FromRecent(it) } + rooms.map { RoomListEntry.FromApi(it) }
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

    // Tapping a room joins it. A recent on another server needs a confirmed instance switch first.
    fun joinRecent(recent: RecentRoom) {
        if (recent.instanceId != activeInstanceId) {
            pendingSwitchJoin = recent
        } else {
            onJoinRecent(recent)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = BedrudTabScaffoldContentInsets,
        topBar = {
            BedrudCompactTopBar(
                actions = { ProfileAvatarButton(user = currentUser, onClick = onOpenProfile) },
                title = { RoomsHeaderTitle(serverName = activeServerName, serverColor = activeServerColor) },
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
                            val roomName = BedrudURLParser.parseJoinInput(quickJoinText)
                            if (!roomName.isNullOrBlank()) {
                                quickJoinText = ""
                                onJoinRoom(roomName)
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
                                            serverColor = activeServerColor,
                                            isOwner = entry.room.createdBy == currentUser?.id,
                                            onJoin = { onJoinRoom(entry.room.name) },
                                            onDelete = { roomToDelete = entry.room },
                                            onSettings = if (entry.room.createdBy == currentUser?.id) {
                                                { roomToEdit = entry.room }
                                            } else null,
                                            modifier = Modifier.padding(horizontal = Dimens.space16, vertical = Dimens.space4),
                                        )
                                        is RoomListEntry.FromRecent -> RecentRoomCard(
                                            recent = entry.recent,
                                            serverColor = colorForRecent(entry.recent),
                                            isCurrentServer = entry.recent.instanceId == activeInstanceId,
                                            now = nowTickMs,
                                            onJoin = { joinRecent(entry.recent) },
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
                                        serverColor = activeServerColor,
                                        isOwner = room.createdBy == currentUser?.id,
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

/** Two-tone rooms header: the active server's name (in its own accent color) + "rooms". */
@Composable
private fun RoomsHeaderTitle(serverName: String?, serverColor: Color) {
    val name = serverName ?: stringResource(R.string.instance_default_displayName)
    val suffix = stringResource(R.string.dashboard_header_roomsSuffix)
    val suffixColor = MaterialTheme.colorScheme.onSurface
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = serverColor, fontWeight = FontWeight.SemiBold)) { append(name) }
            append(" ")
            withStyle(SpanStyle(color = suffixColor)) { append(suffix) }
        },
        style = MaterialTheme.typography.headlineSmall,
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
                    style = MaterialTheme.typography.labelLarge,
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.dashboard_placeholder_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(Dimens.iconSm)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = BedrudShapeTokens.field,
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
    serverColor: Color,
    isOwner: Boolean,
    onJoin: () -> Unit,
    onDelete: () -> Unit,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val title = room.name.ifEmpty {
        val parts = room.id.split("-")
        if (parts.size >= 2) "${parts[0]}-${parts[1]}" else room.id
    }

    val activeTint by animateColorAsState(
        targetValue = if (room.isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.durationLong),
        label = "activeTint"
    )

    val statusText = if (room.isActive) {
        stringResource(R.string.dashboard_status_live)
    } else {
        stringResource(R.string.dashboard_status_idle)
    }
    val metaText = if (room.isPublic == false) {
        "$statusText · ${stringResource(R.string.dashboard_feature_private)}"
    } else {
        statusText
    }

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
        RoomCardScaffold(serverColor = serverColor, onClick = onJoin) {
            Column(modifier = Modifier.weight(1f)) {
                // API rooms always belong to the active server, which the header already names.
                RoomTitleLine(title = title, serverName = null, serverColor = serverColor)
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = activeTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

// ── Recent room card (cross-server, from local history) ────────────────────────

@Composable
private fun RecentRoomCard(
    recent: RecentRoom,
    serverColor: Color,
    isCurrentServer: Boolean,
    now: Long,
    onJoin: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOngoing = CallService.isRunning &&
        CallService.activeRoomName == recent.roomName &&
        CallService.activeInstanceId == recent.instanceId
    val recentTime = if (isOngoing) now else recent.leftAt ?: recent.joinedAt
    val metaText = formatRecentRoomTimeAgo(recentTime, now)

    val swipeAction = SwipeAction(
        label = stringResource(R.string.dashboard_action_remove),
        icon = Icons.Default.Close,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        // Non-destructive (only drops it from local history): remove immediately and dismiss.
        onTriggered = { onRemove(); true },
    )

    SwipeableRoomRow(action = swipeAction, modifier = modifier.fillMaxWidth()) {
        RoomCardScaffold(serverColor = serverColor, onClick = onJoin) {
            Column(modifier = Modifier.weight(1f)) {
                RoomTitleLine(
                    title = recent.roomName,
                    // On the active server the "on {server}" label is redundant with the header.
                    serverName = if (isCurrentServer) null else recent.instanceName,
                    serverColor = serverColor,
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TrailingChevron()
        }
    }
}

// ── Shared card pieces ─────────────────────────────────────────────────────────

/** Outlined card with the per-server accent stripe on its leading edge. */
@Composable
private fun RoomCardScaffold(
    serverColor: Color,
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
                // Min height first so it wins: every card renders the same height whether or not
                // it carries a trailing 48dp control (the settings button inflated owned cards).
                .heightIn(min = Dimens.roomCardMinHeight)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fused to the card's leading edge, full height — the card's own shape clips the
            // band's corners, so it reads as part of the card rather than a floating pill.
            Box(
                modifier = Modifier
                    .width(Dimens.roomCardStripe)
                    .fillMaxHeight()
                    .background(serverColor),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.space16, end = Dimens.space4, top = Dimens.space12, bottom = Dimens.space12),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Line 1 of a room card: the monospace room name + the colored "on {server}" tag. */
@Composable
private fun RoomTitleLine(title: String, serverName: String?, serverColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                textDirection = TextDirection.Ltr,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (serverName != null) {
            // Split the localized "on %1$s" template around its placeholder so the connective
            // word stays muted while only the server name carries the server's accent color.
            val template = stringResource(R.string.dashboard_recent_onServer)
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val placeholderIndex = template.indexOf(SERVER_NAME_PLACEHOLDER)
            Spacer(modifier = Modifier.width(Dimens.space8))
            Text(
                text = buildAnnotatedString {
                    if (placeholderIndex >= 0) {
                        withStyle(SpanStyle(color = mutedColor)) {
                            append(template.substring(0, placeholderIndex))
                        }
                        withStyle(SpanStyle(color = serverColor)) { append(serverName) }
                        withStyle(SpanStyle(color = mutedColor)) {
                            append(template.substring(placeholderIndex + SERVER_NAME_PLACEHOLDER.length))
                        }
                    } else {
                        withStyle(SpanStyle(color = serverColor)) {
                            append(template.format(serverName))
                        }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val SERVER_NAME_PLACEHOLDER = "%1\$s"

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
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text(stringResource(R.string.dashboard_label_roomName)) },
                    singleLine = true,
                    shape = BedrudShapeTokens.field,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(roomName) }) { Text(stringResource(
            R.string.common_button_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_button_cancel)) } }
    )
}
