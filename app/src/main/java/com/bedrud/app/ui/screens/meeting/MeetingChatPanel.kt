package com.bedrud.app.ui.screens.meeting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
 * What the composer is doing beyond holding text — the state its two homes share.
 *
 * The composer's controls live in the call bar while the conversation lives in the panel above
 * it, and an upload started from one is reported in the other: the "+" launches the picker, the
 * spinner and any failure line sit at the foot of the conversation, and send stays disabled until
 * the picture is up. One object holds that thread so the two places cannot disagree about it.
 */
@Stable
class MeetingChatComposerState internal constructor() {
    /** True while a picked image is travelling to the server. Disables attach and send. */
    var isUploading by mutableStateOf(false)
        internal set

    /** Why the last upload failed, in the reader's words — or null. Shown by the conversation. */
    var uploadError by mutableStateOf<String?>(null)
        internal set

    /** Whether the poll composer sheet is up. The bar raises it; the conversation renders it. */
    var isComposingPoll by mutableStateOf(false)

    // Snapshot state, not a plain var: [canAttach] is read in composition, and the room (and so
    // the picker) arrives after the first frame — a plain var would change without invalidating
    // the "+" menu that read it, and the attach entry would stay missing until an unrelated
    // recomposition happened by.
    internal var attach: (() -> Unit)? by mutableStateOf(null)

    /** Whether this room can carry images at all — false until the room is known. */
    val canAttach: Boolean get() = attach != null

    /** Opens the system image picker; the upload and the send both follow from the result. */
    fun attachImage() {
        attach?.invoke()
    }

    /**
     * Forgets a reported failure. Called when chat closes: the state outlives the panel so an
     * upload can finish in the background, but a failure line from an hour ago reappearing on
     * the next open would read as news.
     */
    fun dismissError() {
        uploadError = null
    }
}

/**
 * Creates the composer's shared state and wires the image-upload flow into it.
 *
 * Attachments are a two-step send — the image goes to the server first, and only the URL it comes
 * back with travels to the other participants — so [onSendAttachment] is separate from the plain
 * text send. The caption travels with the picture: whatever [input] holds when the upload lands is
 * sent along and cleared, same as pressing send.
 */
@Composable
fun rememberMeetingChatComposerState(
    imageContext: ChatImageContext?,
    input: String,
    onInputChange: (String) -> Unit,
    onSendAttachment: (String, ChatAttachment) -> Unit,
): MeetingChatComposerState {
    val state = remember { MeetingChatComposerState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The upload outlives recompositions, so it must read the caption and the callbacks as they
    // are when it *finishes*, not as they were when the picker was launched.
    val currentInput by rememberUpdatedState(input)
    val currentOnInputChange by rememberUpdatedState(onInputChange)
    val currentOnSendAttachment by rememberUpdatedState(onSendAttachment)

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
            state.isUploading = true
            state.uploadError = null
            when (val result = uploader.upload(imageContext.roomId, uri)) {
                is ChatUploadResult.Success -> {
                    currentOnSendAttachment(currentInput.trim(), result.attachment)
                    currentOnInputChange("")
                }
                is ChatUploadResult.Failure -> {
                    state.uploadError = when (val reason = result.reason) {
                        ChatUploadFailure.Unreadable -> unreadableMessage
                        ChatUploadFailure.Unreachable -> unreachableMessage
                        is ChatUploadFailure.Rejected -> rejectedFormat.format(reason.code)
                    }
                }
            }
            state.isUploading = false
        }
    }

    state.attach = if (imageContext != null) {
        {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    } else {
        null
    }
    return state
}

/**
 * The in-call conversation: the messages so far, and the overlays they open.
 *
 * The dock that adds to them is not here — it lives in the call bar below, whose controls morph
 * into the composer while chat is open. This panel is what unfolds above that bar: the list, the
 * jump-to-latest button, the upload spinner and failure line at its foot, and the sheets a message
 * can open (poll results, reactions, the lightbox, the poll composer).
 *
 * Reactions and votes name a message that may be anybody's — [onToggleReaction] and [onVote] —
 * where [onSendPoll] adds one of this reader's own.
 *
 * [canParticipate] is false when this person may not send (chat off for the room, or a moderator
 * block): the conversation stays readable, but reactions and votes close with the dock.
 */
@Composable
fun MeetingChatConversation(
    messages: List<ChatMessage>,
    currentIdentity: String,
    composer: MeetingChatComposerState,
    onToggleReaction: (String, String) -> Unit,
    onVote: (String, String) -> Unit,
    onSendPoll: (ChatPoll) -> Unit,
    resolveName: (String) -> String,
    imageContext: ChatImageContext?,
    canParticipate: Boolean,
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

    // A device with no browser at all cannot open a link, and a tap that silently does nothing
    // reads as a broken link rather than as a missing app. Shares the notice line below.
    var linkError by remember { mutableStateOf<String?>(null) }
    val linkUnopenable = stringResource(R.string.meeting_chat_linkUnopenable)
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    // The message whose results are open, rather than the poll itself: votes keep arriving while
    // the sheet is up, and holding the poll would freeze the numbers at the moment it opened.
    var resultsMessageId by remember { mutableStateOf<String?>(null) }
    var reactionsMessageId by remember { mutableStateOf<String?>(null) }

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
                        canParticipate = canParticipate,
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

        if (composer.isUploading) {
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
        (composer.uploadError ?: linkError)?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Dimens.space12, vertical = Dimens.space2),
            )
        }
    }

    ChatImageLightbox(
        url = previewImageUrl,
        serverURL = imageContext?.serverURL.orEmpty(),
        accessToken = imageContext?.accessToken,
        onClose = { previewImageUrl = null },
    )

    if (composer.isComposingPoll) {
        MeetingChatPollSheet(
            onDismiss = { composer.isComposingPoll = false },
            onCreate = { poll ->
                composer.isComposingPoll = false
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
 * The field the mic pill becomes: the composer's middle, spanning whatever the bar's centre gives
 * it.
 *
 * A bare field on the bar's own surface, not a boxed one: the call bar holds its controls directly
 * and the composer holds its text the same way. (It wore the mic pill's chrome for one build; one
 * pill beside two more read as chrome arguing with itself, and the inner edge bought nothing the
 * bar's own edge was not already providing.)
 *
 * Its insets live INSIDE the decoration, not around the field: a text field's tap-to-focus region
 * is its own measured bounds, so padding outside it would leave strips of the slot drawn as input
 * but dead to the finger. The min height makes the field span the whole 48dp control band —
 * anywhere in the middle of the bar is the field. In the merged bar the band is the buttons' own
 * 48dp, not the sheet-era 72: the panel's handle now sits above the row, so the bar's resting
 * height is the same in both of its modes and the morph never changes it. A second line grows the
 * bar from there; past [ChatDockMaxLines] the field scrolls within its own height rather than
 * climbing over the conversation it is answering.
 *
 * propagateMinConstraints hands the slot's full size INTO the field, which is what makes its
 * tap-to-focus region span the whole band — `weight` plus a min height on the field itself sized
 * its layout but left its pointer region hugging the text, and the slot's edges went dead to the
 * finger.
 */
@Composable
internal fun ChatComposerField(
    input: String,
    onInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    Box(
        modifier = modifier.heightIn(min = Dimens.meetingMediaButtonHeight),
        propagateMinConstraints = true,
    ) {
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            // Wraps and grows the bar with it. Single-line scrolled the text sideways instead,
            // which hides the start of the sentence being written — the one part a writer needs
            // to see to finish it.
            maxLines = ChatDockMaxLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textDirection = BidiUtils.textDirection(input),
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
            // The placeholder and the field are stacked in a box that aligns them, rather than
            // emitted as bare siblings: left to the slot's own placement the two sat on slightly
            // different lines, and the hint read about a pixel lower than the text that replaces
            // it.
            decorationBox = { field ->
                // This box IS the control band: CenterStart centres a single line in it, and a
                // grown field takes it past the minimum, where the row's bottom alignment takes
                // over.
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
                    field()
                }
            },
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
 * button is. In the morph that is also its arrival: it fades in exactly where the camera key
 * fades out, the same shape in the same corner changing its job. The fill is the media-off tone
 * because that is the only fill that survives the dark palette (`colors.button` sits two values
 * out of 255 from the bar); the state-language cost of borrowing the "off" tone for an always-live
 * control was weighed against an invisible button, and visibility won.
 */
@Composable
internal fun ComposerAddButton(
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
 * role colour differs, because one ends the call and the other does not. The morph trades one for
 * the other in place: the pill at the bar's end keeps its seat and its shape while its colour
 * crosses from the error role to the accent, which is the whole state change told on one control.
 *
 * With nothing to send it keeps the pill — an empty composer still shows where send is — as a
 * `button` fill lifted by the mic pill's resting shadow, with the icon dimmed. The shadow is what
 * makes it visible at all: that fill sits two values out of 255 from the bar in the dark palette.
 * Deliberately NOT the media-off fill: on the call bar that fill marks a control that is off *and
 * tappable* (the muted mic, the stopped camera — tapping turns them back on), and an inert pill
 * wearing it would invite exactly the tap it ignores.
 */
@Composable
internal fun DockSendButton(
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
internal fun ChatSendDisabledNotice(@StringRes reason: Int) {
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
 * Three lines: past that the field scrolls within its height rather than climbing over the
 * conversation it is answering, which is the thing the writer is looking at while they type.
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
