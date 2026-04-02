package dev.shs.shsplayer.core.data.mappers

import dev.shs.shsplayer.core.data.models.VideoState
import dev.shs.shsplayer.core.database.converter.UriListConverter
import dev.shs.shsplayer.core.database.entities.MediumStateEntity

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
