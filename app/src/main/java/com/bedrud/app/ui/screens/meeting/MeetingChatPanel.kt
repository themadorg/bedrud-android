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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.bedrud.app.R
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.core.call.CallService
import com.bedrud.app.core.chat.ChatImageUploader
import com.bedrud.app.core.chat.ChatUploadFailure
import com.bedrud.app.core.chat.ChatUploadResult
import com.bedrud.app.core.livekit.ChatAttachment
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.core.meeting.chat.ChatPoll
import com.bedrud.app.core.meeting.chat.clustered
import com.bedrud.app.core.meeting.chat.rows
import com.bedrud.app.ui.components.ChatImageLightbox
import com.bedrud.app.ui.theme.Alpha
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation
import com.bedrud.app.ui.theme.bedrudColors
import com.bedrud.app.ui.util.openChatLink
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
    /**
     * The servers this person has added, by host. A room link on one of them opens the app; anything
     * else opens a browser. Passed in rather than looked up here because a link to somebody else's
     * site that happens to carry an `/m/` path is not this app's to swallow, and only the caller
     * knows which servers are actually the reader's.
     */
    knownHosts: Set<String>,
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
    // A device with no browser at all cannot open a link, and a tap that silently does nothing
    // reads as a broken link rather than as a missing app. Shares the notice line below.
    var linkError by remember { mutableStateOf<String?>(null) }
    val linkUnopenable = stringResource(R.string.meeting_chat_linkUnopenable)
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var isComposingPoll by remember { mutableStateOf(false) }
    // Attach and poll show only on an empty field. They hold a fixed column on the left, and once
    // The message whose results are open, rather than the poll itself: votes keep arriving while
    // the sheet is up, and holding the poll would freeze the numbers at the moment it opened.
    var resultsMessageId by remember { mutableStateOf<String?>(null) }
    var reactionsMessageId by remember { mutableStateOf<String?>(null) }

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
                        onShowReactions = { reactionsMessageId = it },
                        onLinkClick = { url ->
                            linkError = if (
                                context.openChatLink(
                                    url = url,
                                    knownHosts = knownHosts,
                                    // Chat only exists inside a call, so this is false today. It is
                                    // asked rather than assumed so the room hop turns itself on the
                                    // moment leaving a call to follow a link becomes a thing.
                                    canJoinAnotherRoom = !CallService.isRunning,
                                )
                            ) {
                                null
                            } else {
                                linkUnopenable
                            }
                        },
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
        (uploadError ?: linkError)?.let { error ->
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
                // Bottom gap = the control bar's own `space12`, because this bar replaces that one
                // on screen and the swap must not move it. The horizontal margin belongs to
                // MeetingBarSurface now; only the gap separating the dock from the conversation
                // above it is this row's to keep.
                .padding(top = Dimens.space8, bottom = Dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (sendDisabledReason == null) {
                Arrangement.Start
            } else {
                Arrangement.Center
            },
        ) {
            if (sendDisabledReason != null) {
                Box(modifier = Modifier.padding(horizontal = Dimens.meetingScreenMargin)) {
                    ChatSendDisabledNotice(reason = sendDisabledReason)
                }
            } else {
                // Built as the call's controls bar, not as a chat widget: the same shell via
                // MeetingBarSurface, the same 48dp control band on the same 12dp paddings. Chat is
                // part of the call, and the two bars sit in the same place on screen and swap with
                // each other — they are the same object with different contents.
                val chrome = meetingChromeColors()
                MeetingBarSurface {
                    // Horizontal padding only: the bar's two vertical 12s are not the row's to
                    // keep — they are slack the field spends. Its slot IS the bar's full resting
                    // height, so a second line eats the padding's place and the bar itself grows
                    // only once the text genuinely runs out of it. The controls own their insets
                    // instead: send carries the 12dp bottom the row no longer provides, and the
                    // "+" centres, so at one line nothing sits differently than it did.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.meetingBarPaddingH),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.meetingBarItemGap),
                        // Bottom, so a grown field keeps the controls beside the line being written
                        // rather than floating them against the middle of a block of text.
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // A fixture of the bar, not a visitor: it used to stand down while there
                        // was text, which put a control appearing and disappearing beside every
                        // first and last keystroke. It centres in a grown bar — attach-and-poll
                        // belongs to the whole message, where send belongs to the line being
                        // written and stays at the bottom beside it.
                        ComposerAddButton(
                            colors = chrome,
                            enabled = !isUploading,
                            showAttach = imageContext != null,
                            onAttach = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPoll = { isComposingPoll = true },
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )

                        // A bare field on the bar's own surface, not a boxed one: the call bar
                        // holds its controls directly and the composer holds its text the same
                        // way. (It wore the mic pill's chrome for one build; one pill beside two
                        // more read as chrome arguing with itself, and the inner edge bought
                        // nothing the bar's own edge was not already providing.)
                        //
                        // Its insets live INSIDE the decoration, not around the field: a text
                        // field's tap-to-focus region is its own measured bounds, so padding
                        // outside it would leave strips of the slot drawn as input but dead to
                        // the finger. The min height makes the field span the whole 48dp band —
                        // anywhere in the middle of the bar is the field.
                        //
                        // A bare text field rather than an `OutlinedTextField`: that one carries
                        // a focus outline drawn inside the bar it already sits in, and a 56dp
                        // minimum that would outgrow the band.
                        // propagateMinConstraints hands the slot's full size INTO the field, which
                        // is what makes its tap-to-focus region span the whole band — `weight` plus
                        // a min height on the field itself sized its layout but left its pointer
                        // region hugging the text, and the slot's edges went dead to the finger.
                        // (The pill Surface used to do exactly this propagation; this keeps the
                        // mechanics without the chrome.)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(
                                    min = Dimens.meetingMediaButtonHeight +
                                        Dimens.meetingBarPaddingV * 2,
                                ),
                            propagateMinConstraints = true,
                        ) {
                                BasicTextField(
                                    value = input,
                                    onValueChange = onInputChange,
                                    // Wraps and grows the bar with it. Single-line scrolled the
                                    // text sideways instead, which hides the start of the sentence
                                    // being written — the one part a writer needs to see to finish
                                    // it. Capped so a long message cannot push the conversation it
                                    // is replying to off the top of the panel; past the cap the
                                    // field scrolls within its own height.
                                    maxLines = ChatDockMaxLines,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textDirection = BidiUtils.textDirection(input),
                                        // Alignment as well as direction: the field spans the
                                        // whole band, so an unaligned RTL paragraph writes its
                                        // words in the right order and still starts against the
                                        // left edge, leaving the gap on the side the writer's eye
                                        // begins at.
                                        textAlign = BidiUtils.textAlign(input),
                                        lineHeightStyle = CenteredLineHeight,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .bringIntoViewRequester(bringIntoViewRequester)
                                        .onFocusEvent { focusState ->
                                            if (focusState.isFocused) {
                                                scope.launch { bringIntoViewRequester.bringIntoView() }
                                            }
                                        },
                                    // The placeholder and the field are stacked in a box that
                                    // aligns them, rather than emitted as bare siblings: left to
                                    // the slot's own placement the two sat on slightly different
                                    // lines, and the hint read about a pixel lower than the text
                                    // that replaces it.
                                    decorationBox = { field ->
                                        // This box IS the 48dp band: CenterStart centres a single
                                        // line in it, and a grown field takes it past the minimum,
                                        // where the row's bottom alignment takes over.
                                        // space6, because the arithmetic is exact: three 20dp
                                        // lines plus 6dp above and below is precisely the bar's
                                        // 72dp resting height, so even the full three lines never
                                        // grow the bar. A single line still centres in the band.
                                        Box(
                                            contentAlignment = Alignment.CenterStart,
                                            modifier = Modifier.padding(
                                                horizontal = Dimens.space4,
                                                vertical = Dimens.space6,
                                            ),
                                        ) {
                                            if (input.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.meeting_chat_placeholder),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        lineHeightStyle = CenteredLineHeight,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            // The band's full width has to reach the text itself,
                                            // not stop at the box around it. A plain Box hands its
                                            // children a minimum width of zero, so the paragraph
                                            // laid out at exactly its own width — and a paragraph
                                            // no wider than its own letters has nothing to align
                                            // within, which is why setting `textAlign` alone moved
                                            // a Persian message not at all.
                                            //
                                            // Width only. Propagating the band's minimum height as
                                            // well would make the field as tall as the band and
                                            // render its single line against the top of it, losing
                                            // the vertical centring the enclosing box provides.
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                propagateMinConstraints = true,
                                            ) {
                                                field()
                                            }
                                        }
                                    },
                                )
                        }

                        val canSend = input.isNotBlank() && !isUploading
                        DockSendButton(
                            colors = chrome,
                            enabled = canSend,
                            onClick = onSend,
                        )
                    }
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

    // Read back the same way, so a reaction arriving while the sheet is open lands in it, and the
    // sheet closes on its own once the last reaction is taken back.
    val openReactions = reactionsMessageId
        ?.let { id -> messages.firstOrNull { it.id == id }?.reactions }
        ?.takeIf { it.isNotEmpty() }
    if (openReactions != null) {
        ChatReactionsSheet(
            reactions = openReactions,
            currentIdentity = currentIdentity,
            resolveName = resolveName,
            onDismiss = { reactionsMessageId = null },
        )
    }
}

/**
 * The composer's one secondary control: a "+" that opens everything a message can carry.
 *
 * One button rather than a row of them, for the reason every messenger converged on the same
 * shape: attachments multiply — image today, polls today, whatever comes next — and a bar that
 * grows a glyph per kind runs out of room at exactly the moment the field needs it most. The menu
 * scales; the bar does not. It stays put while the field grows or fills — a control that appears
 * and disappears beside every first and last keystroke is motion the bar does not need.
 *
 * It wears the camera toggle's background — the same field-shaped surface, at the same 56×48 —
 * so the composer's one secondary control is visibly a control, the way the call bar's leading
 * button is. The fill is the media-off tone because that is the only fill that survives the dark
 * palette (`colors.button` sits two values out of 255 from the bar); the state-language cost of
 * borrowing the "off" tone for an always-live control was weighed against an invisible button,
 * and visibility won.
 */
@Composable
private fun ComposerAddButton(
    colors: MeetingChromeColors,
    enabled: Boolean,
    showAttach: Boolean,
    onAttach: () -> Unit,
    onPoll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { menuOpen = true },
            enabled = enabled,
            shape = BedrudShapeTokens.field,
            color = colors.buttonMediaOff,
            modifier = Modifier
                .size(
                    width = Dimens.meetingMediaButtonWidth,
                    height = Dimens.meetingMediaButtonHeight,
                )
                .alpha(if (enabled) 1f else Alpha.disabled)
                .semantics { role = Role.Button },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.meeting_chat_composerMenu),
                    tint = colors.onButtonMediaOff,
                    modifier = Modifier.size(Dimens.meetingBarIconMedia),
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // The message long-press card's chrome and rows, not the stock menu's: both menus grow
            // out of the same conversation and offer the same kind of short action list, so they
            // speak one language — card corners, the same lift, labels leading and symbols
            // trailing, a hairline between rows.
            shape = BedrudShapeTokens.card,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevation.level2,
            shadowElevation = Elevation.level3,
            modifier = Modifier.widthIn(
                min = Dimens.chatMenuMinWidth,
                max = Dimens.chatMenuMaxWidth,
            ),
            // Not focusable, so opening it over a raised keyboard does not steal the window focus
            // and dismiss the IME — the same call material3's own editable-anchor menus make. An
            // outside tap still dismisses it; only back-key dismissal is given up.
            properties = PopupProperties(focusable = false),
        ) {
            if (showAttach) {
                ChatMessageAction(
                    label = stringResource(R.string.meeting_chat_attachImage),
                    icon = Icons.Outlined.AttachFile,
                    onClick = {
                        menuOpen = false
                        onAttach()
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ChatMessageAction(
                label = stringResource(R.string.meeting_chat_poll_new),
                icon = Icons.Outlined.Poll,
                onClick = {
                    menuOpen = false
                    onPoll()
                },
            )
        }
    }
}

/**
 * Send: the hang-up button's twin.
 *
 * Both are the one primary action of a bar that is otherwise secondary controls and an expandable
 * middle, both sit at the same end of it, and both are a filled pill of the same size — only the
 * role colour differs, because one ends the call and the other does not.
 *
 * With nothing to send it keeps the pill — an empty composer still shows where send is — as a
 * `button` fill lifted by the mic pill's resting shadow, with the icon dimmed. The shadow is what
 * makes it visible at all: that fill sits two values out of 255 from the bar in the dark palette.
 * Deliberately NOT the media-off fill: on the call bar that fill marks a control that is off *and
 * tappable* (the muted mic, the stopped camera — tapping turns them back on), and an inert pill
 * wearing it would invite exactly the tap it ignores.
 */
@Composable
private fun DockSendButton(
    colors: MeetingChromeColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = BedrudShapeTokens.pill,
        color = if (enabled) colors.accent else colors.button,
        shadowElevation = if (enabled) Elevation.level0 else Elevation.micPillResting,
        modifier = Modifier
            // The bottom inset the row used to provide: the row gave its vertical padding to the
            // field as spendable slack, so the pill keeps its own distance from the bar's edge.
            .padding(bottom = Dimens.meetingBarPaddingV)
            .size(
                width = Dimens.meetingEndCallWidth,
                height = Dimens.meetingMediaButtonHeight,
            )
            // The role IconButton used to provide for free: without it TalkBack reads the label
            // but stops announcing the composer's primary action as a button.
            .semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = stringResource(R.string.meeting_contentDescription_send),
                tint = if (enabled) colors.onAccent else colors.onButtonVariant.copy(alpha = Alpha.disabled),
                modifier = Modifier
                    // The plane is left-heavy — wide tail, sharp tip — and its ink centroid sits
                    // about 3dp behind its box's centre (measured on a device capture), so
                    // geometrically centred it reads as sitting off towards the tail. Nudged half
                    // the imbalance towards the tip; `offset` follows the layout direction, so the
                    // correction mirrors exactly as the icon does in RTL.
                    .offset(x = Dimens.meetingSendIconNudge)
                    .size(Dimens.meetingBarIconLg),
            )
        }
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

/**
 * Splits a line's spare leading evenly above and below its ink.
 *
 * A font's line box is taller than the letters in it, and Compose's default shares that slack out in
 * proportion to the font's own ascent and descent — which for a Latin face means most of it lands
 * above, since the ascent reserves room for accents the text rarely uses. Centring a line box that
 * way leaves the letters sitting visibly low: measured 2-3dp below the composer's icons, which were
 * dead centre. Splitting the slack evenly puts the ink where the eye expects it.
 */
private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    // Trim.Both is the tempting one and it is wrong here: trimming shrinks the field's measured
    // height, and a bottom-aligned row then pins the shorter box lower, putting the ink back where
    // it started. Measured 3.05dp low with it, 1.14dp without.
    trim = LineHeightStyle.Trim.None,
)

/**
 * How tall the composer may grow before it scrolls inside itself.
 *
 * Three lines: what the bar's resting height holds. The bar is tall enough by default — a wrapped
 * message spends the bar's own padding instead of growing it — and past three lines the field
 * scrolls within its height rather than climbing over the conversation it is answering, which is
 * the thing the writer is looking at while they type.
 */
private const val ChatDockMaxLines = 3

/** The scroll-to-latest button sits on the message list, not above it — so it casts no shadow. */
private val FlatFabElevation
    @Composable get() = FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
    )
