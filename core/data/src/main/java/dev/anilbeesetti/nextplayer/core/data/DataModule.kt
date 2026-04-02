package com.shs.videoplayer.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.shs.videoplayer.core.data.repository.LocalMediaRepository
import com.shs.videoplayer.core.data.repository.LocalPreferencesRepository
import com.shs.videoplayer.core.data.repository.MediaRepository
import com.shs.videoplayer.core.data.repository.PreferencesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsPreferencesRepository(
        preferencesRepository: LocalPreferencesRepository,
    ): PreferencesRepository
}
