package com.bedrud.app.ui.screens.meeting

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

/** Fire-and-forget moderation call; a thrown failure surfaces as a snackbar with [fallbackMessage]. */
internal fun moderate(
    scope: kotlinx.coroutines.CoroutineScope?,
    snackbarHostState: SnackbarHostState?,
    fallbackMessage: String,
    action: suspend () -> Unit,
) {
    scope?.launch {
        try {
            action()
        } catch (e: Exception) {
            // Show the caller's localized fallback rather than the raw (untranslated) exception text.
            snackbarHostState?.showSnackbar(fallbackMessage)
        }
    }
}
