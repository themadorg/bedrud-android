package com.bedrud.app.ui.screens.meeting

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.theme.Dimens
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val LOCAL_IDENTITY = "identity-local"
private const val REMOTE_IDENTITY = "identity-remote"

/**
 * A participant's tile and the grid that lays the tiles out: what a tile says about the person on
 * it, what a press on it does, and how the grid folds a crowd into an overflow tile.
 */
class MeetingVideoGridTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** What the tiles asked for, in the order they asked. */
    private val requests = mutableListOf<String>()

    private var room: Room? = null

    private fun string(resource: Int): String = context.getString(resource)

    private fun participant(identity: String, name: String = identity, isMicOff: Boolean = false) =
        ParticipantTileState(
            identity = identity,
            name = name,
            avatarUrl = null,
            cameraTrack = null,
            screenShare = null,
            isMicOff = isMicOff,
        )

    private fun showTile(
        state: ParticipantTileState,
        isLocalParticipant: Boolean = false,
        isPinned: Boolean = false,
        isLocalDeafened: Boolean = false,
        deafenedIdentities: Set<String> = emptySet(),
    ) {
        compose.setThemedContentAt(FontScales.Default) {
            ParticipantTile(
                state = state,
                isLocalParticipant = isLocalParticipant,
                isPinned = isPinned,
                isLocalDeafened = isLocalDeafened,
                deafenedIdentities = deafenedIdentities,
                onOpenParticipantActions = { identity -> requests += "actions:$identity" },
                onExpand = { identity -> requests += "expand:$identity" },
                modifier = Modifier.size(Dimens.meetingTileAvatar * 3),
            )
        }
    }

    private fun showGrid(participantCount: Int) {
        val tiles = (1..participantCount).map { number -> participant("identity-$number", name = "Member $number") }
        val liveKitRoom = LiveKit.create(context.applicationContext as Application).also { room = it }
        compose.setThemedContentAt(FontScales.Default) {
            MeetingVideoGrid(
                tiles = tiles,
                room = liveKitRoom,
                localIdentity = tiles.first().identity,
                disabledVideoIdentities = emptySet(),
                hideAllIncomingVideo = false,
                pinnedIdentity = null,
                speakingLevels = emptyMap(),
                onOpenParticipantActions = {},
                onExpandTile = {},
                onOverflowClick = { requests += "overflow" },
            )
        }
    }

    @After
    fun releaseRoom() {
        room?.release()
    }

    // ── A tile ───────────────────────────────────────────────────────────────

    @Test
    fun shouldOpenSomeoneElsesActionsOnALongPress() {
        showTile(participant(REMOTE_IDENTITY))

        compose.onNodeWithText(REMOTE_IDENTITY).performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals(listOf("actions:$REMOTE_IDENTITY"), requests)
    }

    /** Every action on a participant is about someone else, so your own tile offers none. */
    @Test
    fun shouldOfferNoActionsOnYourOwnTile() {
        showTile(participant(LOCAL_IDENTITY), isLocalParticipant = true)

        compose.onNodeWithText(LOCAL_IDENTITY).performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun shouldExpandATileOnADoubleTap() {
        showTile(participant(REMOTE_IDENTITY))

        compose.onNodeWithText(REMOTE_IDENTITY).performTouchInput { doubleClick() }
        compose.waitForIdle()

        assertEquals(listOf("expand:$REMOTE_IDENTITY"), requests)
    }

    /** A screen reader cannot double tap a tile, so expanding it is offered as an action of its own. */
    @Test
    fun shouldExpandATileThroughItsAccessibilityAction() {
        showTile(participant(REMOTE_IDENTITY))

        val actions = compose.onNodeWithText(REMOTE_IDENTITY).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val expand = actions.single { action -> action.label == string(R.string.meeting_contentDescription_tileFullscreen) }
        compose.runOnUiThread { expand.action() }
        compose.waitForIdle()

        assertEquals(listOf("expand:$REMOTE_IDENTITY"), requests)
    }

    @Test
    fun shouldMarkAMutedParticipant() {
        showTile(participant(REMOTE_IDENTITY, isMicOff = true))

        compose.onNodeWithContentDescription(string(R.string.meeting_contentDescription_micOff)).assertExists()
    }

    @Test
    fun shouldMarkAPinnedTile() {
        showTile(participant(REMOTE_IDENTITY), isPinned = true)

        compose.onNodeWithContentDescription(string(R.string.meeting_action_pin)).assertExists()
    }

    /** Someone else is deafened when they have said so, whatever this device's own state. */
    @Test
    fun shouldShowSomeoneElseDeafenedOnlyOnceTheyAnnounceIt() {
        showTile(participant(REMOTE_IDENTITY), isLocalDeafened = true, deafenedIdentities = setOf(REMOTE_IDENTITY))

        compose.onNodeWithContentDescription(string(R.string.meeting_contentDescription_deafened)).assertExists()
    }

    @Test
    fun shouldNotShowSomeoneElseDeafenedForYourOwnDeafen() {
        showTile(participant(REMOTE_IDENTITY), isLocalDeafened = true)

        compose.onNodeWithContentDescription(string(R.string.meeting_contentDescription_deafened)).assertDoesNotExist()
    }

    /** Your own tile follows your own tap, without waiting for the announcement to come back. */
    @Test
    fun shouldShowYourOwnDeafenFromYourOwnState() {
        showTile(participant(LOCAL_IDENTITY), isLocalParticipant = true, isLocalDeafened = true)

        compose.onNodeWithContentDescription(string(R.string.meeting_contentDescription_deafened)).assertExists()
    }

    // ── The grid ─────────────────────────────────────────────────────────────

    @Test
    fun shouldShowSixParticipantsWithoutAnOverflowTile() {
        showGrid(participantCount = 6)

        compose.onNodeWithText("Member 6").assertExists()
        compose.onNodeWithText("+", substring = true).assertDoesNotExist()
    }

    /** From the seventh on, the sixth slot counts everyone the grid has no room for. */
    @Test
    fun shouldFoldEveryoneFromTheSixthOnIntoAnOverflowTile() {
        showGrid(participantCount = 7)

        compose.onNodeWithText("Member 5").assertExists()
        compose.onNodeWithText("Member 6").assertDoesNotExist()
        compose.onNodeWithText("+2").performClick()
        compose.waitForIdle()

        assertEquals(listOf("overflow"), requests)
    }
}
