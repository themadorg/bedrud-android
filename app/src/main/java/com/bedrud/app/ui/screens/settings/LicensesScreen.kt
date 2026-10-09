package com.bedrud.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.R
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudCompactTopBar
import com.bedrud.app.ui.components.BedrudScaffoldContentInsets
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.typeCentered
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.style.LibraryActionBadges
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesDensity
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryBadges
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode

/** A list split into the projects the screen shows first and all the others. */
internal class LibrarySplit<T>(val main: List<T>, val others: List<T>)

/**
 * The few projects the app is built on, listed before the rest so the screen opens on what a
 * reader would recognise. Every other library stays one tap away: most of them are Apache 2.0,
 * which asks for its licence to travel with every copy, so none can be dropped from the screen.
 */
internal object MainProjects {
    /** In the order the screen lists them, by the ids the AboutLibraries plugin gives them. */
    private val ids = listOf(
        "io.livekit:livekit-android",
        "io.github.webrtc-sdk:android-prefixed",
        "androidx.compose.material3:material3",
        "font:vazirmatn",
        "font:roboto",
    )

    /** A main project the build no longer ships is left out rather than shown empty. */
    fun <T> split(libraries: List<T>, idOf: (T) -> String): LibrarySplit<T> {
        val byId = libraries.associateBy(idOf)
        return LibrarySplit(
            main = ids.mapNotNull { id -> byId[id] },
            others = libraries.filterNot { library -> idOf(library) in ids },
        )
    }
}

/** The row that opens every library after the main projects, with how many the app ships. */
@Composable
private fun AllLibrariesRow(count: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(R.string.settings_label_allLibraries),
                modifier = Modifier.typeCentered(LocalTextStyle.current)
            )
        },
        // The chevron of Settings' own row into this screen, for the same promise of more.
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val countStyle = MaterialTheme.typography.bodyMedium
                Text(
                    count.toString(),
                    style = countStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.typeCentered(countStyle)
                )
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // The library's rows sit 20dp in from each edge, 4dp past a ListItem's own 16dp, so the
        // row adds the difference to line its label up with the names above it.
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space4)
    )
}

private val NameAndLicenceOnly = LibraryBadges(author = false)

private val NoRowActions = LibraryActionBadges(
    sourceEnabled = false,
    websiteEnabled = false,
    sponsorEnabled = false,
    licenseEnabled = false
)

/**
 * The licence text a library ships under, or its licences' names and links where the generated
 * data carries no text: a few licences (the Android SDK's, public domain) come with a name only.
 */
private fun Library.licenceText(): String = strippedLicenseContent.ifBlank {
    licenses.joinToString(separator = "\n\n") { license ->
        listOfNotNull(license.name, license.url).joinToString(separator = "\n")
    }
}

/**
 * The app's own dialog for one library's licence, in place of the library's: the same title,
 * body and button as every other dialog in the app, instead of a second look for a dialog.
 */
@Composable
private fun LicenceDialog(library: Library, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(library.name) },
        // Licence texts are English in every language, so the text runs left to right even in a
        // right-to-left app; otherwise each line's closing punctuation jumps to its other end.
        text = {
            Text(
                library.licenceText(),
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            BedrudButton(
                text = stringResource(R.string.settings_button_closeLicense),
                variant = BedrudButtonVariant.GHOST,
                onClick = onDismiss,
            )
        }
    )
}

/**
 * Every licence the app ships under: each library's, read from the dependency graph at build
 * time, and each bundled font's, added by hand in `app/aboutlibraries/`.
 *
 * A row opens its licence text in a dialog. The row's own links to a project's source and
 * website stay off, since their labels are the library's English and the licence is what the
 * reader came for.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    var openLibrary by remember { mutableStateOf<Library?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val split = remember(libraries) {
        libraries?.let { libs -> MainProjects.split(libs.libraries) { library -> library.uniqueId } }
    }
    val shown = remember(libraries, split, showAll) {
        val libs = libraries ?: return@remember null
        val parts = split ?: return@remember null
        Libs(if (showAll) parts.main + parts.others else parts.main, libs.licenses)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = BedrudScaffoldContentInsets,
        topBar = {
            BedrudCompactTopBar(
                title = stringResource(R.string.settings_label_licenses),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LibrariesContainer(
            libraries = shown,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            // Two lines a row (name and version, then licence) rather than four, since the list
            // runs to well over a hundred once opened in full.
            density = LibrariesDensity.Compact,
            badges = NameAndLicenceOnly,
            footer = if (showAll || libraries == null) null else {
                {
                    item {
                        AllLibrariesRow(
                            count = libraries?.libraries?.size ?: 0,
                            onClick = { showAll = true }
                        )
                    }
                }
            },
            // The row only reports the tap; LicenceDialog below answers it.
            detailMode = LibraryDetailMode.None,
            onLibraryClick = { library ->
                openLibrary = library
                true
            },
            actionLabels = NoRowActions,
            // The library tints each licence's badge with a hue of its own (Apache violet, MIT
            // sky, and so on), colours the app's palette has no place for. The badge takes
            // BedrudBadge's colours instead, the same for every licence.
            variantColors = LibraryDefaults.m3VariantColors(
                licenseBadgeContainer = MaterialTheme.colorScheme.tertiaryContainer,
                licenseBadgeContent = MaterialTheme.colorScheme.onTertiaryContainer
            ),
        )
    }

    openLibrary?.let { library ->
        LicenceDialog(library = library, onDismiss = { openLibrary = null })
    }
}
