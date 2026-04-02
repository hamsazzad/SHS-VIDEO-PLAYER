package com.shs.videoplayer.core.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.shs.videoplayer.core.media.services.LocalMediaService
import com.shs.videoplayer.core.media.services.MediaService
import com.shs.videoplayer.core.media.sync.LocalMediaInfoSynchronizer
import com.shs.videoplayer.core.media.sync.LocalMediaSynchronizer
import com.shs.videoplayer.core.media.sync.MediaInfoSynchronizer
import com.shs.videoplayer.core.media.sync.MediaSynchronizer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MediaModule {

    @Binds
    @Singleton
    fun bindsMediaSynchronizer(
        mediaSynchronizer: LocalMediaSynchronizer,
    ): MediaSynchronizer

    @Binds
    @Singleton
    fun bindsMediaInfoSynchronizer(
        mediaInfoSynchronizer: LocalMediaInfoSynchronizer,
    ): MediaInfoSynchronizer

    @Binds
    @Singleton
    fun bindMediaService(
        mediaService: LocalMediaService,
    ): MediaService
}
