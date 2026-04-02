package dev.shs.shsplayer.core.data.mappers

import dev.shs.shsplayer.core.database.entities.VideoStreamInfoEntity
import dev.shs.shsplayer.core.model.VideoStreamInfo

fun VideoStreamInfoEntity.toVideoStreamInfo() = VideoStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    frameRate = frameRate,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
)
