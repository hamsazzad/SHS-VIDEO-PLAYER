package dev.shs.shsplayer.core.data.mappers

import dev.shs.shsplayer.core.common.Utils
import dev.shs.shsplayer.core.database.entities.AudioStreamInfoEntity
import dev.shs.shsplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.shs.shsplayer.core.database.relations.MediumWithInfo
import dev.shs.shsplayer.core.model.Video
import java.util.Date

fun MediumWithInfo.toVideo() = Video(
    id = mediumEntity.mediaStoreId,
    path = mediumEntity.path,
    parentPath = mediumEntity.parentPath,
    duration = mediumEntity.duration,
    uriString = mediumEntity.uriString,
    nameWithExtension = mediumEntity.name,
    width = mediumEntity.width,
    height = mediumEntity.height,
    size = mediumEntity.size,
    dateModified = mediumEntity.modified,
    format = mediumEntity.format,
    thumbnailPath = mediumEntity.thumbnailPath,
    playbackPosition = mediumStateEntity?.playbackPosition ?: 0L,
    lastPlayedAt = mediumStateEntity?.lastPlayedTime?.let { Date(it) },
    formattedDuration = Utils.formatDurationMillis(mediumEntity.duration),
    formattedFileSize = Utils.formatFileSize(mediumEntity.size),
    videoStream = videoStreamInfo?.toVideoStreamInfo(),
    audioStreams = audioStreamsInfo.map(AudioStreamInfoEntity::toAudioStreamInfo),
    subtitleStreams = subtitleStreamsInfo.map(SubtitleStreamInfoEntity::toSubtitleStreamInfo),
)
