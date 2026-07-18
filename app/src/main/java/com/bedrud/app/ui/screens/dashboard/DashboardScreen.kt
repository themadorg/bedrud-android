package com.bedrud.app.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.bedrud.app.ui.components.BedrudOutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.bedrud.app.ui.components.BedrudCompactTopBar
import com.bedrud.app.ui.components.BedrudTabScaffoldContentInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.bedrud.app.models.UserRoomResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val AUTO_REFRESH_INTERVAL_MS = 60_000L

// ── Filter state ─────────────────────────────────────────────────────────────

private enum class RoomFilter { RECENT, MY_ROOMS, ALL }

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
    instanceManager: InstanceManager = koinInject(),
    recentRoomsStore: RecentRoomsStore = koinInject(),
) {
    val roomApi = instanceManager.roomApi.collectAsState().value ?: return
    val authManager = instanceManager.authManager.collectAsState().value
    val currentUser by (authManager?.currentUser ?: MutableStateFlow(null)).collectAsState()
    val recentRooms by recentRoomsStore.rooms.collectAsState()
    val activeInstanceId by instanceManager.store.activeInstanceId.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var rooms by remember { mutableStateOf<List<UserRoomResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastFetchAtMs by remember { mutableLongStateOf(0L) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var roomToEdit by remember { mutableStateOf<UserRoomResponse?>(null) }
    var roomToDelete by remember { mutableStateOf<UserRoomResponse?>(null) }
    var activeFilter by rememberSaveable { mutableStateOf(RoomFilter.RECENT) }
    var quickJoinText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Survives the dispose/recompose Navigation does when leaving for MeetingScreen and
    // coming back via Back -- listState's own scroll position is restored by that same
    // mechanism, so without this a newly created room can land above the restored scroll
    // offset in a long list, out of view until the user manually scrolls up.
    // Holds the just-created room's name (not just a boolean) so the wait-for-data check
    // below can confirm *that room* has actually arrived, rather than firing as soon as
    // the tab's list is merely non-empty -- which, for the server-backed My Rooms/All
    // tabs, is true immediately from pre-existing rooms, well before the async refetch
    // actually includes the new one.
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

    // Returns an error message on failure, or null on success.
    suspend fun fetchRooms(): String? {
        return try {
            val response = roomApi.listRooms()
            if (response.isSuccessful) {
                rooms = response.body() ?: emptyList()
                lastFetchAtMs = System.currentTimeMillis()
                null
            } else {
                "Failed to load rooms"
            }
        } catch (e: Exception) {
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

    LaunchedEffect(Unit) { loadRooms() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { silentlyRefreshRooms() }

    // Keep the list self-healing against server-side eventual consistency (e.g. a
    // just-created room not yet reflected in listRooms()) without requiring the user
    // to background/foreground the app or pull to refresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MS)
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
                                    rooms = rooms.filter { it.id != deleting.id }
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
            RoomFilter.RECENT -> emptyList()
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
        // same way RecentRoomsStore.add() already keeps it true for the Recent tab.
        recentOnly.map { RoomListEntry.FromRecent(it) } + rooms.map { RoomListEntry.FromApi(it) }
    }

    LaunchedEffect(pendingScrollToTopFor, activeFilter, recentRooms, filteredRooms, allTabEntries) {
        val targetRoomName = pendingScrollToTopFor ?: return@LaunchedEffect
        val targetRoomVisibleInCurrentTab = when (activeFilter) {
            RoomFilter.RECENT -> recentRooms.any { it.roomName == targetRoomName }
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
                title = stringResource(R.string.dashboard_title_rooms),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dashboard_contentDescription_createRoom)) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .onGloballyPositioned { quickJoinHeightPx = it.size.height }
                    )

                    // ── Filter tabs ───────────────────────────────────
                    FilterRow(
                        activeFilter = activeFilter,
                        onFilterChange = { activeFilter = it },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .onGloballyPositioned { filterRowHeightPx = it.size.height }
                    )

                    // ── Room list ─────────────────────────────────────
                    val isCurrentTabEmpty = when (activeFilter) {
                        RoomFilter.RECENT -> recentRooms.isEmpty()
                        RoomFilter.ALL -> allTabEntries.isEmpty() && !isLoading
                        else -> filteredRooms.isEmpty() && !isLoading
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
                                when (activeFilter) {
                                    RoomFilter.RECENT -> RecentEmptyState()
                                    RoomFilter.ALL -> EmptyState(
                                        hasFilter = false,
                                        onCreateRoom = { showCreateDialog = true },
                                    )
                                    else -> EmptyState(
                                        hasFilter = true,
                                        onCreateRoom = { showCreateDialog = true },
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = if (isKeyboardVisible) 16.dp else 88.dp,
                            )
                        ) {
                            if (activeFilter == RoomFilter.RECENT) {
                                items(
                                    recentRooms,
                                    key = { "${it.instanceId}:${it.roomName}" },
                                ) { recent ->
                                    RecentRoomCard(
                                        recent = recent,
                                        isCurrentServer = recent.instanceId == activeInstanceId,
                                        now = nowTickMs,
                                        onJoin = { onJoinRecent(recent) },
                                        onRemove = {
                                            recentRoomsStore.remove(recent.roomName, recent.instanceId)
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                            } else if (activeFilter == RoomFilter.ALL) {
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
                                            onJoin = { onJoinRoom(entry.room.name) },
                                            onDelete = { roomToDelete = entry.room },
                                            onSettings = if (entry.room.createdBy == currentUser?.id) {
                                                { roomToEdit = entry.room }
                                            } else null,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        )
                                        is RoomListEntry.FromRecent -> RecentRoomCard(
                                            recent = entry.recent,
                                            isCurrentServer = entry.recent.instanceId == activeInstanceId,
                                            now = nowTickMs,
                                            onJoin = { onJoinRecent(entry.recent) },
                                            onRemove = {
                                                recentRoomsStore.remove(
                                                    entry.recent.roomName,
                                                    entry.recent.instanceId,
                                                )
                                            },
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            } else {
                                items(filteredRooms, key = { it.id }) { room ->
                                    RoomCard(
                                        room = room,
                                        onJoin = { onJoinRoom(room.name) },
                                        onDelete = { roomToDelete = room },
                                        onSettings = if (room.createdBy == currentUser?.id) {
                                            { roomToEdit = room }
                                        } else null,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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

// ── Quick join bar ────────────────────────────────────────────────────────────

@Composable
private fun QuickJoinBar(
    value: String,
    onValueChange: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.dashboard_placeholder_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onJoin,
            enabled = value.isNotBlank()
        ) { Text(stringResource(R.string.common_button_join)) }
    }
}

// ── Filter tabs ───────────────────────────────────────────────────────────────

@Composable
private fun FilterRow(
    activeFilter: RoomFilter,
    onFilterChange: (RoomFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoomFilter.entries.forEach { filter ->
            FilterChip(
                selected = activeFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            RoomFilter.RECENT -> stringResource(R.string.dashboard_filter_recent)
                            RoomFilter.MY_ROOMS -> stringResource(R.string.dashboard_filter_myRooms)
                            RoomFilter.ALL -> stringResource(R.string.dashboard_filter_all)
                        }
                    )
                }
            )
        }
    }
}

// ── Room card ─────────────────────────────────────────────────────────────────

@Composable
private fun RoomCard(
    room: UserRoomResponse,
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
        animationSpec = tween(400),
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

    BedrudOutlinedCard(
        onClick = onJoin,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = activeTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onSettings != null) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.dashboard_contentDescription_settings),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_button_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Recent room card ──────────────────────────────────────────────────────────

@Composable
private fun RecentRoomCard(
    recent: RecentRoom,
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
    val metaText = if (isCurrentServer) {
        formatRecentRoomTimeAgo(recentTime, now)
    } else {
        "${formatRecentRoomTimeAgo(recentTime, now)} · ${
            stringResource(R.string.dashboard_recent_onServer, recent.instanceName)
        }"
    }

    BedrudOutlinedCard(
        onClick = onJoin,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recent.roomName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.dashboard_contentDescription_removeRecent),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentEmptyState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dashboard_empty_noRecent),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dashboard_empty_noRecentHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasFilter: Boolean, onCreateRoom: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasFilter) stringResource(R.string.dashboard_empty_noMatch) else stringResource(R.string.dashboard_empty_noRooms),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasFilter) {
            Spacer(modifier = Modifier.height(4.dp))
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_dialog_createTitle)) },
        text = {
            Column {
                Text(stringResource(R.string.dashboard_dialog_createDescription),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text(stringResource(R.string.dashboard_label_roomName)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
