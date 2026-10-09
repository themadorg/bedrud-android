package com.bedrud.app.ui.screens.instance

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.models.Instance
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.centre
import com.bedrud.app.testutil.clipsLines
import com.bedrud.app.testutil.isPaint
import com.bedrud.app.testutil.nearestOf
import com.bedrud.app.testutil.rowsWhere
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.testutil.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

private const val LongServerName = "A self-hosted server whose name runs far past one line"

/** A pixel and a half: the ink's own bounds and the offset applied to it each round to a pixel. */
private const val PositionTolerancePx = 1.5f

/** The server switcher's edit mode: removing a saved server, and asking before it goes. */
class InstanceSwitcherSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inUseLabel = context.getString(R.string.instance_status_inUse)

    // Named apart from every label the switcher draws, so a match on its name is never a match on
    // a label, and with no letter hanging below the line, so its letters' middle is its capitals'.
    private val serverInUse = Instance(id = "bedrud", serverURL = "https://bedrud.example.com", displayName = "Bedrud")
    private val officeServer = Instance(id = "office", serverURL = "https://office.example.com", displayName = "Office")
    private val labServer = Instance(id = "lab", serverURL = "http://lab.example.com", displayName = "Lab")

    private var servers by mutableStateOf(listOf(serverInUse, officeServer, labServer))
    private val selected = mutableListOf<Instance>()
    private val removed = mutableListOf<Instance>()

    // The paints the switcher's rows are drawn with, read from the theme each test runs in.
    private var sheetColor = Color.Unspecified
    private var nameColor = Color.Unspecified
    private var addressColor = Color.Unspecified
    private var badgeColor = Color.Unspecified

    private fun showSwitcher() {
        compose.setThemedContentAt(FontScales.Default) {
            // What BedrudBottomSheet's container resolves to (DESIGN.md, Bottom sheets).
            sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
            nameColor = MaterialTheme.colorScheme.onSurface
            addressColor = MaterialTheme.colorScheme.onSurfaceVariant
            badgeColor = MaterialTheme.colorScheme.tertiaryContainer
            // On a surface of the sheet's own colour, as it is drawn in the app. The surface also
            // supplies the content colour the server name takes, as the sheet's does; a bare
            // background leaves the name in the default black, which only passes in light theme.
            Surface(color = sheetColor) {
                Column {
                    InstanceSwitcherContent(
                        instances = servers,
                        activeId = serverInUse.id,
                        serverAfterRemoving = { id -> servers.firstOrNull { it.id != id } },
                        onSelect = { selected += it },
                        onRemove = { server ->
                            removed += server
                            servers = servers - server
                        },
                        onAddInstance = {},
                    )
                }
            }
        }
    }

    private fun removeButton(server: Instance): SemanticsNodeInteraction =
        compose.onNodeWithContentDescription(
            context.getString(R.string.instance_contentDescription_removeServer, server.displayName)
        )

    private fun dialogTitle(server: Instance): SemanticsNodeInteraction =
        compose.onNodeWithText(context.getString(R.string.instance_dialog_removeTitle, server.displayName))

    private fun startEditing() {
        compose.onNodeWithText(context.getString(R.string.instance_button_edit)).performClick()
    }

    private fun askToRemove(server: Instance) {
        startEditing()
        removeButton(server).performClick()
    }

    private fun confirmRemoval() {
        compose.onNodeWithText(context.getString(R.string.common_action_remove)).performClick()
    }

    @Test
    fun shouldBadgeServerInUse() {
        showSwitcher()

        compose.onNode(hasText(serverInUse.displayName) and hasText(inUseLabel)).assertIsDisplayed()
    }

    /** The pill is measured against the name's letters, not its line box, on the rendered pixels. */
    @Test
    fun shouldCentreBadgeOnServerNameLetters() {
        showSwitcher()
        val row = compose.onNode(hasText(serverInUse.displayName) and hasText(inUseLabel))
        val rowLeft = row.getUnclippedBoundsInRoot().left
        val nameBounds = compose.onNodeWithText(serverInUse.displayName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val image = row.captureToImage().toPixelMap()
        val nameStart = with(compose.density) { (nameBounds.left - rowLeft).roundToPx() }
        val nameEnd = with(compose.density) { (nameBounds.right - rowLeft).roundToPx() }

        val paints = listOf(nameColor, addressColor, sheetColor, badgeColor)
        val name = image.rowsWhere(nameStart until nameEnd) { it.nearestOf(paints) == nameColor }
        val pill = image.rowsWhere(nameEnd until image.width) { it.isPaint(badgeColor) }

        assertEquals("name at rows $name, pill at rows $pill", name.centre, pill.centre, PositionTolerancePx)
    }

    @Test
    fun shouldBadgeNoOtherServer() {
        showSwitcher()

        compose.onAllNodesWithText(inUseLabel).assertCountEquals(1)
        compose.onNode(hasText(officeServer.displayName) and hasText(inUseLabel)).assertDoesNotExist()
    }

    @Test
    fun shouldMarkServerInUseWithoutTick() {
        showSwitcher()

        compose.onNodeWithContentDescription(inUseLabel).assertDoesNotExist()
    }

    @Test
    fun shouldKeepBadgeWholeBesideLongServerNameAtLargestFontScale() {
        servers = listOf(serverInUse.copy(displayName = LongServerName), officeServer)
        compose.setThemedContentAt(FontScales.Largest) {
            Column {
                InstanceSwitcherContent(
                    instances = servers,
                    activeId = serverInUse.id,
                    serverAfterRemoving = { null },
                    onSelect = {},
                    onRemove = {},
                    onAddInstance = {},
                )
            }
        }

        val badge = compose.onNodeWithText(inUseLabel, useUnmergedTree = true).textLayout()

        assertEquals("badge wrapped", 1, badge.lineCount)
        assertFalse("badge clipped", badge.clipsLines)
    }

    @Test
    fun shouldKeepBadgeWhileEditing() {
        showSwitcher()

        startEditing()

        compose.onNodeWithText(inUseLabel).assertIsDisplayed()
    }

    @Test
    fun shouldOfferNoRemoveButtonUntilEditIsTapped() {
        showSwitcher()

        removeButton(officeServer).assertDoesNotExist()
    }

    @Test
    fun shouldOfferRemoveButtonForEveryServerWhileEditing() {
        showSwitcher()

        startEditing()

        listOf(serverInUse, officeServer, labServer).forEach { removeButton(it).assertIsDisplayed() }
    }

    @Test
    fun shouldSwitchServerWhenRowIsTapped() {
        showSwitcher()

        compose.onNodeWithText(officeServer.displayName).performClick()

        assertEquals(listOf(officeServer), selected)
    }

    @Test
    fun shouldNotSwitchServerWhenRowIsTappedWhileEditing() {
        showSwitcher()
        startEditing()

        compose.onNodeWithText(officeServer.displayName).performClick()

        assertEquals(emptyList<Instance>(), selected)
    }

    @Test
    fun shouldKeepAddServerWhileEditing() {
        showSwitcher()

        startEditing()

        compose.onNodeWithText(context.getString(R.string.instance_button_addServer)).assertIsDisplayed()
    }

    @Test
    fun shouldAskBeforeRemovingServer() {
        showSwitcher()

        askToRemove(officeServer)

        dialogTitle(officeServer).assertIsDisplayed()
        assertEquals(emptyList<Instance>(), removed)
    }

    @Test
    fun shouldRemoveServerOnceRemovalIsConfirmed() {
        showSwitcher()
        askToRemove(officeServer)

        confirmRemoval()

        assertEquals(listOf(officeServer), removed)
        dialogTitle(officeServer).assertDoesNotExist()
    }

    @Test
    fun shouldKeepServerWhenRemovalIsCancelled() {
        showSwitcher()
        askToRemove(officeServer)

        compose.onNodeWithText(context.getString(R.string.common_button_cancel)).performClick()

        assertEquals(emptyList<Instance>(), removed)
        dialogTitle(officeServer).assertDoesNotExist()
    }

    @Test
    fun shouldSayWhatIsLostWhenAnotherServerIsRemoved() {
        showSwitcher()

        askToRemove(officeServer)

        compose.onNodeWithText(context.getString(R.string.instance_dialog_removeMessage)).assertIsDisplayed()
    }

    @Test
    fun shouldNameServerTakingOverWhenServerInUseIsRemoved() {
        showSwitcher()

        askToRemove(serverInUse)

        compose.onNodeWithText(
            context.getString(R.string.instance_dialog_removeActiveMessage, officeServer.displayName)
        ).assertIsDisplayed()
    }

    @Test
    fun shouldSayAddServerFollowsWhenOnlyServerIsRemoved() {
        servers = listOf(serverInUse)
        showSwitcher()

        askToRemove(serverInUse)

        compose.onNodeWithText(context.getString(R.string.instance_dialog_removeLastMessage)).assertIsDisplayed()
    }

    @Test
    fun shouldStayEditingAfterAnotherServerIsRemoved() {
        showSwitcher()
        askToRemove(officeServer)

        confirmRemoval()

        compose.onNodeWithText(context.getString(R.string.instance_button_done)).assertIsDisplayed()
        removeButton(labServer).assertIsDisplayed()
    }

    @Test
    fun shouldStopEditingWhenDoneIsTapped() {
        showSwitcher()
        startEditing()

        compose.onNodeWithText(context.getString(R.string.instance_button_done)).performClick()

        removeButton(officeServer).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.instance_button_edit)).assertIsDisplayed()
    }
}
