package com.bedrud.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.ui.theme.typeCentered

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
                    // The style the item provides for its label slot, read rather than restated so
                    // the correction is measured from the type M3 actually renders here.
                    val labelStyle = LocalTextStyle.current
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The bar is a fixed height and the label is centred under an icon, so the
                        // font box's asymmetry shows as an uneven gap between the two. Per style,
                        // never per string: correcting each tab's own word moved "Settings" 4px
                        // off the baseline "Rooms" and "Profile" sat on, because its descender
                        // carries its ink centre down.
                        modifier = Modifier.typeCentered(labelStyle),
                    )
                },
            )
        }
    }
}
