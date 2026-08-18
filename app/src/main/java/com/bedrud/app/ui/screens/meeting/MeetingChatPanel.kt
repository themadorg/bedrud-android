package com.bedrud.app.ui.screens.meeting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusEvent
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
import com.bedrud.app.core.meeting.chat.ChatPoll
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
 * Reactions and votes are separate again — [onToggleReaction] and [onVote] name a message that may
 * be anybody's, where [onSendPoll] adds one of this reader's own.
 *
 * [sendDisabledReason] closes the dock and says why, while leaving the conversation readable:
 * turning chat off, or blocking one person, should not take away what has already been said.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingChatPanel(
    messages: List<ChatMessage>,
    input: String,
    currentIdentity: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendAttachment: (String, ChatAttachment) -> Unit,
    onSendPoll: (ChatPoll) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onVote: (String, String) -> Unit,
    resolveName: (String) -> String,
    imageContext: ChatImageContext?,
    @StringRes sendDisabledReason: Int?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
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
    var isComposingPoll by remember { mutableStateOf(false) }
    // The message whose results are open, rather than the poll itself: votes keep arriving while
    // the sheet is up, and holding the poll would freeze the numbers at the moment it opened.
    var resultsMessageId by remember { mutableStateOf<String?>(null) }

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

    Column(modifier = modifier) {

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
                        currentIdentity = currentIdentity,
                        canParticipate = sendDisabledReason == null,
                        serverURL = imageContext?.serverURL.orEmpty(),
                        accessToken = imageContext?.accessToken,
                        onImageClick = { previewImageUrl = it },
                        onToggleReaction = onToggleReaction,
                        onVote = onVote,
                        onShowPollResults = { resultsMessageId = it },
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
                        Icons.Rounded.KeyboardArrowDown,
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

        // Whichever is taller, the keyboard or the gesture bar — never both stacked. The panel used
        // to sit inside the meeting Scaffold, which had already inset it past the navigation bar; in
        // a sheet window it has to do that itself, and without this the gesture bar drew across the
        // composer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = Dimens.meetingScreenMargin, vertical = Dimens.space8),
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
                // Built as the call's controls bar, not as a chat widget: same corner, same
                // container, same hairline, same screen margin. Chat is part of the call, and the
                // two bars sit in the same place on screen — they should be the same object.
                val chrome = meetingChromeColors()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.chatDockBar)
                        .clip(BedrudShapeTokens.controlsBar)
                        .background(chrome.bar)
                        .border(Dimens.borderThin, chrome.divider, BedrudShapeTokens.controlsBar)
                        .padding(horizontal = Dimens.meetingBarPaddingH),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (imageContext != null) {
                        DockIcon(
                            icon = Icons.Outlined.AttachFile,
                            contentDescription = stringResource(R.string.meeting_contentDescription_attachImage),
                            enabled = !isUploading,
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                    }
                    DockIcon(
                        icon = Icons.Outlined.Poll,
                        contentDescription = stringResource(R.string.meeting_chat_poll_new),
                        enabled = !isUploading,
                        onClick = { isComposingPoll = true },
                    )

                    // A bare text field rather than an `OutlinedTextField`: that one carries a focus
                    // outline drawn *inside* the bar it already sits in, and a 56dp minimum height
                    // that made the dock taller than the messages it belongs under.
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textDirection = BidiUtils.textDirection(input),
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Dimens.space4)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch { bringIntoViewRequester.bringIntoView() }
                                }
                            },
                        decorationBox = { field ->
                            if (input.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.meeting_chat_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            field()
                        },
                    )

                    val canSend = input.isNotBlank() && !isUploading
                    DockIcon(
                        icon = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.meeting_contentDescription_send),
                        enabled = canSend,
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = onSend,
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

    if (isComposingPoll) {
        MeetingChatPollSheet(
            onDismiss = { isComposingPoll = false },
            onCreate = { poll ->
                isComposingPoll = false
                onSendPoll(poll)
            },
        )
    }

    // Read back out of the list every time, so a vote arriving while the sheet is open moves the
    // numbers under it. The sheet closes on its own if the message it was showing goes away.
    val results = resultsMessageId?.let { id -> messages.firstOrNull { it.id == id }?.poll }
    if (results != null) {
        ChatPollResultsSheet(
            poll = results,
            currentIdentity = currentIdentity,
            resolveName = resolveName,
            onDismiss = { resultsMessageId = null },
        )
    }
}

/**
 * One control in the composer bar.
 *
 * Sized to the bar instead of to `IconButton`'s 48dp default — three of those in a 44dp bar left the
 * icons crowding each other while the row had to grow to fit them.
 */
@Composable
private fun DockIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(Dimens.chatDockIcon),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Dimens.iconMd),
        )
    }
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
            Icons.Rounded.Lock,
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
