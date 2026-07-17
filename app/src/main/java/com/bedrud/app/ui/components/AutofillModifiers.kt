package com.bedrud.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

fun Modifier.autofillType(type: ContentType): Modifier = semantics {
    contentType = type
}