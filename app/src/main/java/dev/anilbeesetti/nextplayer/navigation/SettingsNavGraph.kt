package com.shs.videoplayer.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.shs.videoplayer.settings.Setting
import com.shs.videoplayer.settings.navigation.aboutPreferencesScreen
import com.shs.videoplayer.settings.navigation.appearancePreferencesScreen
import com.shs.videoplayer.settings.navigation.audioPreferencesScreen
import com.shs.videoplayer.settings.navigation.decoderPreferencesScreen
import com.shs.videoplayer.settings.navigation.folderPreferencesScreen
import com.shs.videoplayer.settings.navigation.generalPreferencesScreen
import com.shs.videoplayer.settings.navigation.librariesScreen
import com.shs.videoplayer.settings.navigation.mediaLibraryPreferencesScreen
import com.shs.videoplayer.settings.navigation.navigateToAboutPreferences
import com.shs.videoplayer.settings.navigation.navigateToAppearancePreferences
import com.shs.videoplayer.settings.navigation.navigateToAudioPreferences
import com.shs.videoplayer.settings.navigation.navigateToDecoderPreferences
import com.shs.videoplayer.settings.navigation.navigateToFolderPreferencesScreen
import com.shs.videoplayer.settings.navigation.navigateToGeneralPreferences
import com.shs.videoplayer.settings.navigation.navigateToLibraries
import com.shs.videoplayer.settings.navigation.navigateToMediaLibraryPreferencesScreen
import com.shs.videoplayer.settings.navigation.navigateToPlayerPreferences
import com.shs.videoplayer.settings.navigation.navigateToSubtitlePreferences
import com.shs.videoplayer.settings.navigation.playerPreferencesScreen
import com.shs.videoplayer.settings.navigation.settingsNavigationRoute
import com.shs.videoplayer.settings.navigation.settingsScreen
import com.shs.videoplayer.settings.navigation.subtitlePreferencesScreen

const val SETTINGS_ROUTE = "settings_nav_route"

fun NavGraphBuilder.settingsNavGraph(
    navController: NavHostController,
) {
    navigation(
        startDestination = settingsNavigationRoute,
        route = SETTINGS_ROUTE,
    ) {
        settingsScreen(
            onNavigateUp = navController::navigateUp,
            onItemClick = { setting ->
                when (setting) {
                    Setting.APPEARANCE -> navController.navigateToAppearancePreferences()
                    Setting.MEDIA_LIBRARY -> navController.navigateToMediaLibraryPreferencesScreen()
                    Setting.PLAYER -> navController.navigateToPlayerPreferences()
                    Setting.DECODER -> navController.navigateToDecoderPreferences()
                    Setting.AUDIO -> navController.navigateToAudioPreferences()
                    Setting.SUBTITLE -> navController.navigateToSubtitlePreferences()
                    Setting.GENERAL -> navController.navigateToGeneralPreferences()
                    Setting.ABOUT -> navController.navigateToAboutPreferences()
                }
            },
        )
        appearancePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        mediaLibraryPreferencesScreen(
            onNavigateUp = navController::navigateUp,
            onFolderSettingClick = navController::navigateToFolderPreferencesScreen,
        )
        folderPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        playerPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        decoderPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        audioPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        subtitlePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        generalPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        aboutPreferencesScreen(
            onLibrariesClick = navController::navigateToLibraries,
            onNavigateUp = navController::navigateUp,
        )
        librariesScreen(
            onNavigateUp = navController::navigateUp,
        )
    }
}
