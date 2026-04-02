package com.shs.videoplayer.feature.player.model

import android.net.Uri

data class Subtitle(
    val name: String?,
    val uri: Uri,
    val isSelected: Boolean,
)
