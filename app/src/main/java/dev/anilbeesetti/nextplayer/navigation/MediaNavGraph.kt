package com.shs.videoplayer.navigation

import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.shs.videoplayer.feature.player.PlayerActivity
import com.shs.videoplayer.feature.player.utils.PlayerApi
import com.shs.videoplayer.feature.videopicker.navigation.MediaPickerRoute
import com.shs.videoplayer.feature.videopicker.navigation.mediaPickerScreen
import com.shs.videoplayer.feature.videopicker.navigation.navigateToMediaPickerScreen
import com.shs.videoplayer.settings.navigation.navigateToSettings
import com.shs.videoplayer.ui.moveToVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object MediaRootRoute

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris.first()
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onSettingsClick = navController::navigateToSettings,
            onMoveToPrivacyFolder = { uris ->
                CoroutineScope(Dispatchers.IO).launch {
                    uris.forEach { uri -> moveToVault(context, uri, "videos") }
                }
            },
        )
    }
}
