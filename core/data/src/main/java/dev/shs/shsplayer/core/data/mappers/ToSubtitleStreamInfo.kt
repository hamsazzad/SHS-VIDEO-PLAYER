package dev.shs.shsplayer.core.data.mappers

import dev.shs.shsplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.shs.shsplayer.core.model.SubtitleStreamInfo

fun SubtitleStreamInfoEntity.toSubtitleStreamInfo() = SubtitleStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
)
