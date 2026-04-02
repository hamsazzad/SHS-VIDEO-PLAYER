package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anilbeesetti.nextplayer.feature.player.SHSSubtitleController
import kotlinx.coroutines.launch

// ─── Subtitle Editor Sheet ────────────────────────────────────────────────────
//
// Renders as a translucent bottom sheet over the playing video.
// Controls:
//   • Delay ±50 ms steps — applied in real-time via SHSSubtitleController
//   • Text color picker  — 8 preset colors + apply button
//   • Font size slider   — small (12) → large (28)
//   • Shadow toggle
// All changes are applied immediately without interrupting playback.

private val SUBTITLE_COLORS = listOf(
    Color.White   to "#FFFFFF",
    Color.Yellow  to "#FFFF00",
    Color(0xFF00FF00) to "#00FF00",  // Lime
    Color.Cyan    to "#00FFFF",
    Color(0xFFFF8C00) to "#FF8C00",  // Orange
    Color(0xFFFF69B4) to "#FF69B4",  // Pink
    Color(0xFF87CEEB) to "#87CEEB",  // Sky blue
    Color.Gray    to "#808080",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleEditorSheet(
    subtitleController: SHSSubtitleController,
    onDismiss: () -> Unit,
) {
    val scope      = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var delayMs    by remember { mutableLongStateOf(0L) }
    var fontSize   by remember { mutableStateOf(subtitleController.fontSize.toFloat()) }
    var colorHex   by remember { mutableStateOf("#FFFFFF") }
    var shadow     by remember { mutableStateOf(subtitleController.shadowEnabled) }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Subtitle Editor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // ── Preview strip ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sample subtitle text",
                    color = SUBTITLE_COLORS.find { it.second == colorHex }?.first ?: Color.White,
                    fontSize = (28 - fontSize.toInt() + 12).sp,  // invert VLC scale for visual
                    fontWeight = if (shadow) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }

            HorizontalDivider()

            // ── Subtitle delay ────────────────────────────────────────────────
            Text("Subtitle Delay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // -500 ms
                DelayButton("-500ms") {
                    delayMs -= 500
                    subtitleController.setDelay(delayMs)
                }
                // -50 ms
                DelayButton("-50ms") {
                    delayMs -= 50
                    subtitleController.setDelay(delayMs)
                }
                // Current value display
                Text(
                    "${if (delayMs >= 0) "+" else ""}${delayMs} ms",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                // +50 ms
                DelayButton("+50ms") {
                    delayMs += 50
                    subtitleController.setDelay(delayMs)
                }
                // +500 ms
                DelayButton("+500ms") {
                    delayMs += 500
                    subtitleController.setDelay(delayMs)
                }
            }
            TextButton(
                onClick = { delayMs = 0; subtitleController.setDelay(0) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Reset delay") }

            HorizontalDivider()

            // ── Text color ────────────────────────────────────────────────────
            Text("Text Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SUBTITLE_COLORS.forEach { (color, hex) ->
                    val isSelected = colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape),
                            )
                            .clickable {
                                colorHex = hex
                                subtitleController.setColor(hex)
                            },
                    )
                }
            }

            HorizontalDivider()

            // ── Font size ─────────────────────────────────────────────────────
            Text(
                "Font Size: ${fontSize.toInt()} (VLC scale — smaller number = larger glyphs)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = fontSize,
                onValueChange = {
                    fontSize = it
                    subtitleController.setFontSize(it.toInt())
                },
                valueRange = 12f..28f,
                steps = 15,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Large", style = MaterialTheme.typography.labelSmall)
                Text("Small", style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider()

            // ── Shadow toggle ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Text Shadow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Switch(
                    checked = shadow,
                    onCheckedChange = {
                        shadow = it
                        subtitleController.setShadow(it, 160)
                    },
                )
            }

            // ── Close ─────────────────────────────────────────────────────────
            Button(
                onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun DelayButton(label: String, onClick: () -> Unit) {
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
