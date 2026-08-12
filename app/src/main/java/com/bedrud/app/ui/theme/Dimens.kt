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
    val meetingTileGap = 8.dp            // gap between video tiles in the grid
    val meetingGridBottomSpace = 104.dp  // grid clearance above the floating controls bar (incl. its drag handle)
    val meetingTileAvatar = 56.dp        // avatar circle inside a video tile
    val meetingTileAction = 32.dp        // tile corner action (fullscreen) touch circle
    val meetingBadgeIcon = 14.dp         // mic/camera-off badges on the tile name chip
    val meetingIndicatorDot = 8.dp       // top-bar dots (recording, reconnecting)
    val meetingBarPaddingH = 10.dp       // controls bar inner horizontal padding
    val meetingBarPaddingV = 8.dp        // controls bar inner vertical padding
    val meetingBarItemGap = 8.dp         // gap between controls bar buttons
    val meetingMediaButtonWidth = 52.dp  // camera/mic toggles (wider, rectangular)
    val meetingMediaButtonHeight = 44.dp
    val meetingCircleButton = 40.dp      // secondary round controls (share, chat)
    val meetingEndCallButton = 52.dp     // hang-up circle
    val meetingBarIconMedia = 22.dp
    val meetingBarIconSm = 20.dp
    val meetingBarIconLg = 24.dp
    val meetingHandleWidth = 32.dp       // controls-bar drag handle (M3 sheet handle metrics)
    val meetingHandleHeight = 4.dp
    val meetingHandleSwipeThreshold = 24.dp // upward drag distance that opens the options sheet
    val meetingFullscreenAvatar = 96.dp  // avatar circle when a participant is viewed fullscreen
}
