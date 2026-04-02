package com.shs.videoplayer.feature.videopicker.screens

import com.shs.videoplayer.core.model.Folder

sealed interface MediaState {
    data object Loading : MediaState
    data class Success(val data: Folder?) : MediaState
}
