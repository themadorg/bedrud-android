package com.bedrud.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing + sizing tokens. Every gap, padding, component height, and icon size in the UI comes
 * from here instead of a raw `n.dp` literal, so the layout rhythm stays consistent and tunable.
 *
 * Spacing follows a 4dp base grid.
 */
object Dimens {
    // ── Spacing scale (4dp grid) ──
    val space2 = 2.dp
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp
    val space56 = 56.dp

    // ── Layout ──
    val screenPadding = 24.dp
    val screenPaddingCompact = 16.dp
    val maxContentWidth = 480.dp   // keep forms readable on tablets/foldables

    // ── Components ──
    val buttonHeight = 48.dp
    val buttonHeightLarge = 56.dp
    val minTouchTarget = 48.dp
    val fieldMinHeight = 56.dp
    val cardPadding = 16.dp
    val serverCardMinHeight = 112.dp   // roomy tap target for the server-choice cards
    val borderThin = 1.dp
    val borderStrong = 2.dp

    // ── Bottom sheets ──
    // M3 list-item heights: a sheet action row is one-line, or two-line when it carries a
    // supporting line explaining what the action does.
    val sheetRowHeight = 56.dp
    val sheetRowHeightTwoLine = 72.dp
    val sheetPadding = 16.dp           // M3 list-item horizontal inset, also the sheet's own gutter

    // ── Top bar ──
    val topBarHeight = 64.dp           // M3 small-top-app-bar content height (below the status bar)

    // ── Rooms list ──
    val roomCardMinHeight = 72.dp      // uniform card height: 48dp trailing control + 2×12dp padding
    val roomListBottomSpace = 88.dp    // trailing space so the last card clears the create-room FAB

    // ── Icons / avatars ──
    val iconXs = 16.dp
    val iconSm = 18.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 56.dp                 // empty-state illustrations
    val avatar = 40.dp
    val avatarLg = 44.dp               // top-bar profile avatar circle (inside a 48dp touch target)

    // ── Brand ──
    val brandMark = 72.dp

    // ── Meeting ──
    /**
     * The inset every floating surface on the call screen shares — the tile grid, the screenshare
     * strip and the controls bar alike — so their edges line up into one column rather than
     * stepping in and out down the screen. Equal to [meetingTileGap] on purpose: the gap between
     * tiles and the gap to the screen edge are the same rhythm.
     */
    val meetingScreenMargin = 8.dp
    val meetingTileGap = 8.dp            // gap between video tiles in the grid
    /**
     * How far the tile grid stops short of the bottom, to clear the floating controls bar.
     *
     * The bar takes 84dp off that edge — 72dp tall (12 + 48 + 12) sitting 12dp above the safe area
     * — so this is that plus the gap wanted between the last tile and the bar. Written as the sum
     * rather than a round number, because it went stale twice while the bar was being resized.
     */
    val meetingGridBottomSpace = 96.dp
    val meetingTileAvatar = 56.dp        // avatar circle inside a video tile
    val meetingTileAction = 32.dp        // tile corner action touch circle
    val meetingBadgeIcon = 14.dp         // mic-off / speaking badges on the tile name chip
    val meetingSpeakingRingMin = 1.5.dp  // room-hears-you ring at the quietest reported level
    val meetingSpeakingRingMax = 3.dp    // ...and at the loudest
    val meetingIndicatorDot = 8.dp       // top-bar dots (recording, reconnecting)
    val meetingBarPaddingH = 10.dp       // controls bar inner horizontal padding
    // Bottom inset of the controls bar, mirroring the drag-handle block above the row so the
    // 48dp controls sit centred. 12 + 48 + 12 puts the bar at 72dp — the same height as the chat
    // dock that replaces it, so the bottom edge does not jump when chat opens.
    val meetingBarPaddingV = 12.dp
    val meetingBarItemGap = 8.dp         // gap between controls bar buttons
    val meetingMediaButtonWidth = 56.dp  // camera/mic toggles (wider, rectangular)
    val meetingMediaButtonHeight = 48.dp
    val meetingCircleButton = 44.dp      // secondary round controls (share, chat)
    val meetingEndCallWidth = 56.dp      // hang-up pill width — equal to the camera toggle so the sides stay symmetric
    val meetingBarIconMedia = 22.dp
    val meetingBarIconSm = 20.dp
    val meetingBarIconLg = 24.dp
    val meetingHandleWidth = 32.dp       // controls-bar drag handle (M3 sheet handle metrics)
    val meetingHandleHeight = 4.dp
    // The mic pill's status arc. Thinner than a normal strong border: the arc travels around the
    // pill, and movement already draws the eye — weight on top of it only shouts over chrome that
    // is otherwise tonal surfaces and hairlines.
    val meetingMicRingStroke = 1.dp
    val meetingHandleSwipeThreshold = 24.dp // upward drag distance that opens the options sheet
    val meetingFullscreenAvatar = 96.dp  // avatar circle when a participant is viewed fullscreen
    val meetingMicPillMaxWidth = 136.dp  // mic pill cap (both modes): sides never squeeze at this width
    val meetingSliderThumb = 20.dp       // compact round slider thumb (in-call sliders)
    val meetingSliderTrack = 4.dp        // compact slider track height

    // ── Meeting: connection-failed and kicked states ──
    val meetingStateIcon = 72.dp             // the single large icon these full-screen states lead with
    val meetingStatePadding = 32.dp          // gutter for their centred explanatory text
    val meetingErrorDetailMaxHeight = 140.dp // raw server error scrolls past this rather than growing

    // ── Meeting: chat ──
    val chatListEdgeGap = 12.dp          // breathing room above the first run and below the last
    val chatUploadIndicator = 14.dp      // inline upload spinner, sized to the label beside it
    val chatUploadIndicatorStroke = 2.dp // ...and thinned to match, the M3 default being far heavier
    val chatAvatar = 28.dp               // sender circle, drawn once per run of messages
    val chatClusterGap = 12.dp           // between one person's run and the next
    val chatBubbleGap = 2.dp             // between bubbles inside a single run
    val chatImageMaxHeight = 240.dp      // a tall picture is cropped rather than owning the panel
    val chatReactionTarget = 40.dp       // one emoji in the picker, sized for a thumb rather than for the glyph
    // The pill is deliberately narrower than the eight reactions it holds, and scrolls: a row
    // stretched across the panel reads as a band over the conversation rather than as something
    // floating against one message, and the far end of it is out of thumb reach anyway. Five and a
    // half targets plus the row's own padding, so the sixth is half-showing and says there is more
    // behind the edge without needing an arrow to announce it.
    val chatReactionPickerMaxWidth = 228.dp
    // What the popup leaves free at each side, so a narrow window shrinks the pill rather than
    // pushing it off screen — a small phone in split screen has less to give than the cap asks for.
    val chatReactionPickerInset = 32.dp
    // The long-press menu straddles the bubble: this is the air between each surface and the message
    // between them, enough to read as two separate cards rather than one interrupted.
    val chatMenuGap = 8.dp
    // A card, not a banner: without a cap the rows stretch to the panel's full width, and "Copy"
    // alone across the screen reads as a section header rather than as something to tap. Sized to
    // the longest label it holds rather than to the panel, which keeps it beside the message it
    // belongs to instead of over the whole conversation. The floor stops a single row collapsing
    // into a lozenge.
    val chatMenuMinWidth = 150.dp
    val chatMenuMaxWidth = 200.dp
    val chatMenuIcon = 20.dp             // trailing symbol, kept under the label's cap height
    // A reaction chip's height is set, not left to the emoji: an emoji glyph fills its line box, so
    // padding alone left its ink about a pixel from the edge and the chip read as cramped. Kept
    // clearly under a bubble's own height — a badge hanging off a message, not a second message.
    val chatReactionChip = 24.dp
    val chatReactionChipMinWidth = 40.dp // so a single emoji is a pill rather than a circle
    // The composer is one raised bar inset from the panel's edges, not a band across the bottom, and
    // it is deliberately shorter than a Material text field: a call's chat dock competes with the
    // conversation above it, where a form field is the main event on its screen.
    val chatDockBar = 44.dp
    val chatDockIcon = 36.dp             // icon targets inside that bar, sized to it rather than to the 48dp default
    val chatPollWidth = 220.dp           // a poll sets its own width, so its bars are comparable between messages
    val chatPollOption = 36.dp           // one answer row, tall enough to tap without reading as a button

    // ── Meeting: invite sheet ──
    val inviteGridMaxHeight = 280.dp     // participant avatar grid scrolls beyond this
    val inviteTargetSize = 56.dp         // share-target circles (send, copy, QR, apps)
    val inviteQrSize = 240.dp            // rendered room-link QR code
}
