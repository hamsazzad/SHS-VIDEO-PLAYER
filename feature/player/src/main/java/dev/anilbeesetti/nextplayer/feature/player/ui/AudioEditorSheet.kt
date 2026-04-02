package com.shs.videoplayer.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.shs.videoplayer.feature.player.SHSAudioController
import com.shs.videoplayer.feature.player.state.AudioEqualizerState
import kotlinx.coroutines.launch

// ─── Audio Editor Sheet ───────────────────────────────────────────────────────
//
// Bottom sheet rendered over the playing video (no playback interruption).
// Sections:
//   1. EQ Presets  — one-tap named presets via SHSAudioController
//   2. 10-Band EQ  — per-band sliders via AudioEqualizerState (system Audio FX)
//   3. Audio Delay — real-time +/- via SHSAudioController.setDelay()
//   4. Audio Tracks — list of available tracks from Media3 player

private data class EqPreset(
    val name: String,
    val preset: SHSAudioController.EqualizerPreset,
)

private val EQ_PRESETS = listOf(
    EqPreset("Flat",      SHSAudioController.EqualizerPreset.FLAT),
    EqPreset("Bass",      SHSAudioController.EqualizerPreset.BASS_BOOST),
    EqPreset("Rock",      SHSAudioController.EqualizerPreset.ROCK),
    EqPreset("Pop",       SHSAudioController.EqualizerPreset.POP),
    EqPreset("Jazz",      SHSAudioController.EqualizerPreset.JAZZ),
    EqPreset("Classical", SHSAudioController.EqualizerPreset.CLASSICAL),
    EqPreset("Vocal",     SHSAudioController.EqualizerPreset.VOCAL_BOOST),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorSheet(
    player: Player,
    shsAudioController: SHSAudioController,
    audioEqualizerState: AudioEqualizerState?,
    onDismiss: () -> Unit,
) {
    val scope      = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var delayMs        by remember { mutableLongStateOf(0L) }
    var selectedPreset by remember { mutableStateOf<SHSAudioController.EqualizerPreset?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Audio Editor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // ── EQ Presets ────────────────────────────────────────────────────
            Text("EQ Preset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(EQ_PRESETS) { item ->
                    FilterChip(
                        selected = selectedPreset == item.preset,
                        onClick = {
                            selectedPreset = item.preset
                            shsAudioController.setEqualizer(item.preset)
                        },
                        label = { Text(item.name) },
                    )
                }
            }

            HorizontalDivider()

            // ── 10-Band EQ (Android AudioFX) ──────────────────────────────────
            if (audioEqualizerState != null && audioEqualizerState.isReady &&
                audioEqualizerState.bandCount > 0
            ) {
                Text(
                    "Fine-tune EQ Bands",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                audioEqualizerState.bandLevels.forEachIndexed { idx, level ->
                    val freq = audioEqualizerState.bandFrequencies.getOrElse(idx) { "" }
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(freq, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${if (level >= 0) "+" else ""}${level / 100} dB",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { audioEqualizerState.setBandLevel(idx, it.toInt()) },
                            valueRange = audioEqualizerState.minLevel.toFloat()..audioEqualizerState.maxLevel.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextButton(
                    onClick = { audioEqualizerState.resetBands() },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Reset EQ bands") }
                HorizontalDivider()
            }

            // ── Audio Delay ───────────────────────────────────────────────────
            Text("Audio Delay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AudioDelayButton("-500ms") {
                    delayMs -= 500
                    shsAudioController.setDelay(delayMs)
                }
                AudioDelayButton("-50ms") {
                    delayMs -= 50
                    shsAudioController.setDelay(delayMs)
                }
                Text(
                    "${if (delayMs >= 0) "+" else ""}${delayMs} ms",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                AudioDelayButton("+50ms") {
                    delayMs += 50
                    shsAudioController.setDelay(delayMs)
                }
                AudioDelayButton("+500ms") {
                    delayMs += 500
                    shsAudioController.setDelay(delayMs)
                }
            }
            TextButton(
                onClick = { delayMs = 0; shsAudioController.setDelay(0) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Reset delay") }

            HorizontalDivider()

            // ── Audio Track Switcher ───────────────────────────────────────────
            val trackCount = player.currentTracks.groups.count { group ->
                group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
            }
            if (trackCount > 1) {
                Text(
                    "Audio Tracks ($trackCount available)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                var currentTrack by remember {
                    mutableStateOf(
                        player.currentTracks.groups.indexOfFirst { g ->
                            g.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && g.isSelected
                        },
                    )
                }
                val audioGroups = player.currentTracks.groups
                    .filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    audioGroups.forEachIndexed { idx, group ->
                        val label = buildString {
                            val fmt = group.getTrackFormat(0)
                            if (fmt.language != null) append("[${fmt.language}] ")
                            if (!fmt.label.isNullOrBlank()) append(fmt.label)
                            else append("Track ${idx + 1}")
                            if (fmt.channelCount > 0) append(" (${fmt.channelCount}ch)")
                        }
                        FilterChip(
                            selected = group.isSelected,
                            onClick = {
                                val params = player.trackSelectionParameters.buildUpon()
                                    .setPreferredAudioLanguage(
                                        group.getTrackFormat(0).language ?: "",
                                    )
                                    .build()
                                player.trackSelectionParameters = params
                                currentTrack = idx
                            },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                HorizontalDivider()
            }

            // ── Close ─────────────────────────────────────────────────────────
            Button(
                onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AudioDelayButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
