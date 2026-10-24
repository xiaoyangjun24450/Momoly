package me.rerere.momoly.ui.chat

import android.net.Uri

data class Attachment(
    val uri: Uri,
    val isImage: Boolean,
)
