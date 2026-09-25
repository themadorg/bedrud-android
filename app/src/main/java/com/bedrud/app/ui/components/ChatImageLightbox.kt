package com.bedrud.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.bedrud.app.R
import com.bedrud.app.core.chat.ChatImageSaver
import com.bedrud.app.core.chat.ChatSaveFailure
import com.bedrud.app.core.chat.ChatSaveResult
import com.bedrud.app.ui.theme.Alpha
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.theme.bedrudColors
import com.bedrud.app.ui.theme.typeCentered
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatImageLightbox(
    url: String?,
    serverURL: String,
    accessToken: String?,
    onClose: () -> Unit,
) {
    if (url == null) return

    // Back is the dialog's own: dismissOnBackPress routes it to onDismissRequest, since the dialog
    // window holds focus and an activity-level BackHandler never hears it.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val saver = remember { ChatImageSaver(context.contentResolver) }

        var isSaving by remember { mutableStateOf(false) }
        var outcome by remember { mutableStateOf<String?>(null) }

        val savedMessage = stringResource(R.string.meeting_chat_imageSaved)
        val unreachableMessage = stringResource(R.string.meeting_chat_imageSaveUnreachable)
        val unwritableMessage = stringResource(R.string.meeting_chat_imageSaveUnwritable)

        val save: () -> Unit = {
            scope.launch {
                isSaving = true
                outcome = null
                outcome = when (val result = saver.save(url, serverURL, accessToken)) {
                    ChatSaveResult.Saved -> savedMessage
                    is ChatSaveResult.Failure -> when (result.reason) {
                        ChatSaveFailure.Unreachable -> unreachableMessage
                        ChatSaveFailure.Unwritable -> unwritableMessage
                    }
                }
                isSaving = false
            }
        }

        // Writing through MediaStore needs no permission from Android 10 onward. Below that it does,
        // so it is asked for at the moment it is needed rather than on the way into a call.
        val storageLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) save() else outcome = unwritableMessage
        }
        val onSaveClick: () -> Unit = {
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                save()
            }
        }

        // The word said once, then taken back — the picture is what the screen is for.
        LaunchedEffect(outcome) {
            if (outcome != null) {
                delay(Motion.lightboxOutcomeNoticeMs)
                outcome = null
            }
        }

        val onScrim = MaterialTheme.bedrudColors.onScrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = Alpha.lightboxScrim))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
        ) {
            ChatImage(
                url = url,
                serverURL = serverURL,
                accessToken = accessToken,
                contentDescription = stringResource(R.string.meeting_chat_sharedImage),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.space16, vertical = Dimens.space56)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
                contentScale = ContentScale.Fit,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(Dimens.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(Dimens.space12)
                            .size(Dimens.chatUploadIndicator),
                        strokeWidth = Dimens.chatUploadIndicatorStroke,
                        color = onScrim,
                    )
                } else {
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.meeting_chat_saveImage),
                            tint = onScrim,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.meeting_contentDescription_closeImagePreview),
                        tint = onScrim,
                    )
                }
            }

            outcome?.let { message ->
                val noticeStyle = MaterialTheme.typography.labelLarge
                Text(
                    text = message,
                    style = noticeStyle,
                    // The inverse pair, as a snackbar uses: white text on inverseSurface vanished in
                    // dark theme, where inverseSurface is the light one.
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(Dimens.space16)
                        // A snackbar's colours and a snackbar's corners: it is one in all but name.
                        .background(
                            MaterialTheme.colorScheme.inverseSurface.copy(alpha = Alpha.lightboxNotice),
                            BedrudShapeTokens.snackbar,
                        )
                        .padding(horizontal = Dimens.space16, vertical = Dimens.space8)
                        // Last in the chain, so it moves the letters inside the notice, not the notice.
                        .typeCentered(noticeStyle),
                )
            }
        }
    }
}
