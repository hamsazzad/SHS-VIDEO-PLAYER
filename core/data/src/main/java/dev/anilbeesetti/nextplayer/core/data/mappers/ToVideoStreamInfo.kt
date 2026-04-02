package com.shs.videoplayer.core.data.mappers

import com.shs.videoplayer.core.database.entities.VideoStreamInfoEntity
import com.shs.videoplayer.core.model.VideoStreamInfo

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
