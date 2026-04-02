package com.shs.videoplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.shs.videoplayer.core.ui.R
import com.shs.videoplayer.core.ui.extensions.copy
import com.shs.videoplayer.feature.player.buttons.PlayerButton
import com.shs.videoplayer.feature.player.buttons.PlayerButtonId
import com.shs.videoplayer.feature.player.buttons.PlayerButtonLayout

@OptIn(UnstableApi::class)
@Composable
fun ControlsTopView(
    modifier: Modifier = Modifier,
    title: String,
    /** Increment externally to force a re-read of PlayerButtonLayout from SharedPreferences. */
    layoutVersion: Int = 0,
    onAudioClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onPlaybackSpeedClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {},
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    onSubtitleSearchClick: () -> Unit = {},
    onSubtitleEditorClick: () -> Unit = {},
    onAudioEditorClick: () -> Unit = {},
    onEditButtonOrderClick: () -> Unit = {},
) {
    val context           = LocalContext.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    // Re-read user layout from SharedPreferences when layoutVersion changes.
    val orderedIds = key(layoutVersion) {
        remember { PlayerButtonLayout.getTopBarOrder(context) }
    }
    val hiddenIds = key(layoutVersion) {
        remember { PlayerButtonLayout.getHiddenButtons(context) }
    }

    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            .displayCutoutPadding()
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(horizontal = 8.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Back button — always first, not user-configurable
        PlayerButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = null,
            )
        }
        // Video title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Dynamically-ordered top-bar buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            orderedIds.filter { it !in hiddenIds }.forEach { id ->
                when (id) {
                    PlayerButtonId.PLAYLIST -> PlayerButton(onClick = onPlaylistClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist),
                            contentDescription = stringResource(R.string.playlist),
                        )
                    }
                    PlayerButtonId.SCREENSHOT -> PlayerButton(onClick = onScreenshotClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_screenshot),
                            contentDescription = stringResource(R.string.screenshot),
                        )
                    }
                    PlayerButtonId.SPEED -> PlayerButton(onClick = onPlaybackSpeedClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_speed),
                            contentDescription = null,
                        )
                    }
                    PlayerButtonId.AUDIO -> PlayerButton(onClick = onAudioClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_audio_track),
                            contentDescription = null,
                        )
                    }
                    PlayerButtonId.SUBTITLE -> PlayerButton(onClick = onSubtitleClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_subtitle_track),
                            contentDescription = null,
                        )
                    }
                    PlayerButtonId.SUBTITLE_SEARCH -> PlayerButton(onClick = onSubtitleSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_subtitle_track),
                            contentDescription = "Search subtitles online",
                        )
                    }
                    PlayerButtonId.SUBTITLE_EDITOR -> PlayerButton(onClick = onSubtitleEditorClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_subtitle_track),
                            contentDescription = "Edit subtitle style",
                        )
                    }
                    PlayerButtonId.AUDIO_EDITOR -> PlayerButton(onClick = onAudioEditorClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_audio_track),
                            contentDescription = "Audio equalizer",
                        )
                    }
                    PlayerButtonId.MENU -> PlayerButton(onClick = onMenuClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    // Bottom-bar only buttons — skipped here
                    else -> Unit
                }
            }
        }
    }
}
