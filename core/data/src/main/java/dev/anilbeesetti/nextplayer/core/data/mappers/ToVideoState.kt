package com.shs.videoplayer.core.data.mappers

import com.shs.videoplayer.core.data.models.VideoState
import com.shs.videoplayer.core.database.converter.UriListConverter
import com.shs.videoplayer.core.database.entities.MediumStateEntity

fun MediumStateEntity.toVideoState(): VideoState {
    return VideoState(
        path = uriString,
        position = playbackPosition.takeIf { it != 0L },
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
        playbackSpeed = playbackSpeed,
        externalSubs = UriListConverter.fromStringToList(externalSubs),
        videoScale = videoScale,
    )
}
