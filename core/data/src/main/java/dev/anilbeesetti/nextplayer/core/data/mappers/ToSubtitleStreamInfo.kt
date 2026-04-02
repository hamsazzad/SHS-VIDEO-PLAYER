package com.shs.videoplayer.core.data.mappers

import com.shs.videoplayer.core.database.entities.SubtitleStreamInfoEntity
import com.shs.videoplayer.core.model.SubtitleStreamInfo

fun SubtitleStreamInfoEntity.toSubtitleStreamInfo() = SubtitleStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
)
