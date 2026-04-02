package dev.shs.shsplayer.core.data.mappers

import dev.shs.shsplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.model.AudioStreamInfo

fun AudioStreamInfoEntity.toAudioStreamInfo() = AudioStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    sampleFormat = sampleFormat,
    sampleRate = sampleRate,
    channels = channels,
    channelLayout = channelLayout,
)
