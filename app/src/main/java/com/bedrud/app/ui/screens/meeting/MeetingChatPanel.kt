package com.bedrud.app.ui.screens.meeting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.core.chat.ChatImageUploader
import com.bedrud.app.core.chat.ChatUploadFailure
import com.bedrud.app.core.chat.ChatUploadResult
import com.bedrud.app.core.livekit.ChatAttachment
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.core.meeting.chat.clustered
import com.bedrud.app.core.meeting.chat.rows
import com.bedrud.app.ui.components.ChatImageLightbox
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.bedrudColors
import kotlinx.coroutines.launch

/**
 * Everything the panel needs to put a picture in a message and to load the ones already sent.
 *
 * Null until the room is known. It used to be four separate optional parameters whose combinations
 * had no meaning — there is no such thing as knowing the server but not the room — so they are one
 * value that is either present or not.
 */
data class ChatImageContext(
    val roomId: String,
    val roomApi: RoomApi,
    val serverURL: String,
    val accessToken: String?,
)

/**
 * The in-call chat: the messages so far, and the dock to add to them.
 *
 * Attachments are a two-step send — the image goes to the server first, and only the URL it comes
 * back with travels to the other participants — so [onSendAttachment] is separate from [onSend].
 *
 * [sendDisabledReason] closes the dock and says why, while leaving the conversation readable:
 * turning chat off, or blocking one person, should not take away what has already been said.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingChatPanel(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendAttachment: (String, ChatAttachment) -> Unit,
    onClose: () -> Unit,
    imageContext: ChatImageContext?,
    @StringRes sendDisabledReason: Int?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Pinned rather than collapsing: the header stays put while the conversation moves under it,
    // and only its container colour reacts.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // One item per message, each carrying where it sits in its sender's run. Grouping decides how a
    // row is drawn; it must not decide what a list item is, or a long run would become one giant
    // item that the list can neither recycle nor scroll to the end of.
    //
    // Newest first, because the list is laid out in reverse: the conversation then hangs from the
    // bottom edge on its own, an arriving message never moves what is being read, and there is no
    // scroll to race against the message that triggered it.
    val rows = remember(messages) { messages.clustered().rows().asReversed() }

    // Whether the newest message is on screen right now — what the jump-to-latest button reacts to.
    // In a reversed list the newest is index 0, so this is simply "nothing left to scroll back to".
    val isAtLatest by remember { derivedStateOf { !listState.canScrollBackward } }

    // Whether the reader *wants* to be kept at the newest, which is a different question. It is
    // settled only when a scroll comes to rest, so a message arriving cannot quietly answer it:
    // keyed items hold their place when one is prepended, which would otherwise read as "the reader
    // has scrolled away" and strand the view one message further back with every arrival.
    var followLatest by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) followLatest = !listState.canScrollBackward }
    }

    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    val unreadableMessage = stringResource(R.string.meeting_chat_uploadUnreadable)
    val unreachableMessage = stringResource(R.string.meeting_chat_uploadUnreachable)
    val rejectedFormat = stringResource(R.string.meeting_chat_uploadRejected)

    val uploader = remember(imageContext) {
        imageContext?.let { ChatImageUploader(context.contentResolver, it.roomApi) }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || uploader == null || imageContext == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isUploading = true
            uploadError = null
            when (val result = uploader.upload(imageContext.roomId, uri)) {
                is ChatUploadResult.Success -> {
                    onSendAttachment(input.trim(), result.attachment)
                    onInputChange("")
                }
                is ChatUploadResult.Failure -> {
                    uploadError = when (val reason = result.reason) {
                        ChatUploadFailure.Unreadable -> unreadableMessage
                        ChatUploadFailure.Unreachable -> unreachableMessage
                        is ChatUploadFailure.Rejected -> rejectedFormat.format(reason.code)
                    }
                }
            }
            isUploading = false
        }
    }

    // Someone else's message follows the reader's wishes; your own overrides them, because pressing
    // send is a deliberate act and it would be strange to be left looking at something else.
    LaunchedEffect(messages.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        if (messages.last().isLocal || followLatest) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        ChatPanelHeader(onClose = onClose, scrollBehavior = scrollBehavior)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Padding on the content rather than the list, so a run can still scroll under the
                // header instead of stopping short of it, and neither the first nor the last one
                // sits flush against an edge.
                contentPadding = PaddingValues(
                    horizontal = Dimens.space12,
                    vertical = Dimens.chatListEdgeGap,
                ),
                reverseLayout = true,
            ) {
                items(rows, key = { it.message.id }) { row ->
                    MeetingChatRow(
                        row = row,
                        serverURL = imageContext?.serverURL.orEmpty(),
                        accessToken = imageContext?.accessToken,
                        onImageClick = { previewImageUrl = it },
                    )
                }
            }

            if (!isAtLatest) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.space8),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    elevation = FlatFabElevation,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.meeting_contentDescription_scrollToBottom),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (isUploading) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.space12, vertical = Dimens.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space6),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.chatUploadIndicator),
                    strokeWidth = Dimens.chatUploadIndicatorStroke,
                )
                Text(
                    text = stringResource(R.string.meeting_chat_uploading),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        uploadError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Dimens.space12, vertical = Dimens.space2),
            )
        }

        // Only the keyboard. The meeting Scaffold has already inset this panel past the navigation
        // bar, so padding for that again just lifts the dock off the bottom of the screen — which
        // is what the removed 72dp controls-bar reservation was doing too.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .padding(horizontal = Dimens.space8, vertical = Dimens.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (sendDisabledReason == null) {
                Arrangement.Start
            } else {
                Arrangement.Center
            },
        ) {
            if (sendDisabledReason != null) {
                ChatSendDisabledNotice(reason = sendDisabledReason)
            } else {
                if (imageContext != null) {
                    IconButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isUploading,
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = stringResource(R.string.meeting_contentDescription_attachImage),
                            tint = if (isUploading) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text(stringResource(R.string.meeting_chat_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
                    shape = BedrudShapeTokens.pill,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = BidiUtils.textDirection(input),
                    ),
                )
                Spacer(modifier = Modifier.width(Dimens.space4))
                val canSend = input.isNotBlank() && !isUploading
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.meeting_contentDescription_send),
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    ChatImageLightbox(
        url = previewImageUrl,
        serverURL = imageContext?.serverURL.orEmpty(),
        accessToken = imageContext?.accessToken,
        onClose = { previewImageUrl = null },
    )
}

/**
 * The panel's header, as a real Material 3 top app bar.
 *
 * Which buys the standard height and title treatment, and the scrolled state: instead of a rule
 * drawn under the title at all times, the bar takes on a raised container colour exactly while
 * messages are passing beneath it, and sits flush with the panel when they are not.
 *
 * Its own window insets are switched off — the meeting Scaffold has already inset this panel, and
 * applying the status bar a second time would push the title below where the call's own top bar
 * puts the room name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPanelHeader(
    onClose: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.meeting_panel_chat)) },
        actions = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.meeting_contentDescription_closeChat),
                )
            }
        },
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Stands in for the input dock when this person may not send.
 *
 * Warning colours, not error ones: being blocked, or joining a room with chat turned off, is a
 * restriction to be told about — nothing has gone wrong and nothing is irreversible.
 */
@Composable
private fun ChatSendDisabledNotice(@StringRes reason: Int) {
    Row(
        modifier = Modifier
            .clip(BedrudShapeTokens.pill)
            .background(MaterialTheme.bedrudColors.warningContainer)
            .padding(horizontal = Dimens.space12, vertical = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space6),
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.bedrudColors.onWarningContainer,
            modifier = Modifier.size(Dimens.iconXs),
        )
        Text(
            text = stringResource(reason),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.bedrudColors.onWarningContainer,
            textAlign = TextAlign.Center,
        )
    }
}

/** The scroll-to-latest button sits on the message list, not above it — so it casts no shadow. */
private val FlatFabElevation
    @Composable get() = FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
    )
