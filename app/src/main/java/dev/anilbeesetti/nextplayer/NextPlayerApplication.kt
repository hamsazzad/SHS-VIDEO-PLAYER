package com.shs.videoplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.shs.videoplayer.core.common.di.ApplicationScope
import com.shs.videoplayer.core.data.repository.PreferencesRepository
import com.shs.videoplayer.crash.CrashActivity
import com.shs.videoplayer.crash.GlobalExceptionHandler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@HiltAndroidApp
class NextPlayerApplication : Application() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
    }
}
