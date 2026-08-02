package com.bedrud.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

data class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

/**
 * The app's bottom navigation, built on Material 3's [NavigationBar] / [NavigationBarItem] rather
 * than a hand-rolled row -- so it gets the standard animated active-indicator pill (behind the
 * icon), selected/unselected colour transitions, a bounded ripple, and the correct accessibility
 * semantics for free. Container colour, indicator, tonal elevation, and system-bar insets all come
 * from the M3 defaults wired to the theme's colorScheme (the indicator is `secondaryContainer` --
 * the muted-rose brand tone). Shared by the main tabs and the admin sub-tabs.
 */
@Composable
fun BedrudBottomNavigationBar(
    tabs: List<BottomNavTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
