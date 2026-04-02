package dev.shs.shsplayer.core.data.repository

import android.net.Uri
import dev.shs.shsplayer.core.data.models.VideoState
import dev.shs.shsplayer.core.model.Folder
import dev.shs.shsplayer.core.model.Video
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getVideosFlow(): Flow<List<Video>>
    fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>>
    fun getFoldersFlow(): Flow<List<Folder>>

    suspend fun getVideoByUri(uri: String): Video?
    suspend fun getVideoState(uri: String): VideoState?

    suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long)
    suspend fun updateMediumPosition(uri: String, position: Long)
    suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float)
    suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int)
    suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int)
    suspend fun updateMediumZoom(uri: String, zoom: Float)

    suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri)
}
