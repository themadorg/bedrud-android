package com.bedrud.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsAtLeast
import com.bedrud.app.ui.theme.BedrudTheme
import com.bedrud.app.ui.theme.Dimens
import org.junit.Rule
import org.junit.Test

/** How [BedrudSwitchRow] and [BedrudSwitchListItem] answer a tap anywhere on the row. */
class BedrudSwitchRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showRow(listItem: Boolean, enabled: Boolean = true) {
        compose.setContent {
            BedrudTheme {
                var checked by remember { mutableStateOf(false) }
                if (listItem) {
                    BedrudSwitchListItem(
                        label = Label,
                        checked = checked,
                        onCheckedChange = { checked = it },
                        enabled = enabled,
                    )
                } else {
                    BedrudSwitchRow(
                        label = Label,
                        checked = checked,
                        onCheckedChange = { checked = it },
                        enabled = enabled,
                    )
                }
            }
        }
    }

    private fun row() = compose.onNode(isToggleable())

    @Test
    fun shouldToggleFormRowFromItsLabel() {
        showRow(listItem = false)

        compose.onNodeWithText(Label).performClick()

        row().assertIsOn()
    }

    @Test
    fun shouldToggleListItemFromItsLabel() {
        showRow(listItem = true)

        compose.onNodeWithText(Label).performClick()

        row().assertIsOn()
    }

    @Test
    fun shouldIgnoreLabelTapWhenDisabled() {
        showRow(listItem = false, enabled = false)

        compose.onNodeWithText(Label).performClick()

        row().assertIsOff()
    }

    /** One switch for a screen reader, named by its label, rather than a label and a bare switch. */
    @Test
    fun shouldExposeOneSwitchCarryingItsLabel() {
        showRow(listItem = true)

        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        row().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onAllNodesWithText(Label).assertCountEquals(1)
    }

    /**
     * The switch inside no longer takes a click, so it stops reserving its 48dp touch target; the
     * row keeps that height itself, or the form's rows close up.
     */
    @Test
    fun shouldKeepFormRowAtTouchTargetHeight() {
        showRow(listItem = false)

        row().assertHeightIsAtLeast(Dimens.minTouchTarget + Dimens.space4 * 2)
    }

    private companion object {
        const val Label = "Allow chat"
    }
}
