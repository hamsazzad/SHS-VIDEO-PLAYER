package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.feature.player.subtitles.SubtitleDownloadResult
import dev.anilbeesetti.nextplayer.feature.player.subtitles.SubtitleResult
import dev.anilbeesetti.nextplayer.feature.player.subtitles.SubtitleSearchResult
import dev.anilbeesetti.nextplayer.feature.player.subtitles.SubtitleSearchService
import kotlinx.coroutines.launch
import java.io.File

// ─── Subtitle Search Sheet ────────────────────────────────────────────────────
//
// Bottom sheet that appears over the playing video.
// Queries OpenSubtitles.org with the video filename,
// shows results, and downloads the selected .srt/.vtt file.
// The downloaded file path is returned via [onSubtitleDownloaded] so
// PlayerActivity can load it directly into LibVLC.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSearchSheet(
    initialQuery: String = "",
    cacheDir: File,
    onSubtitleDownloaded: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query        by rememberSaveable { mutableStateOf(initialQuery) }
    var isSearching  by remember { mutableStateOf(false) }
    var results      by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var statusMsg    by remember { mutableStateOf("") }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(Icons.Rounded.Subtitles, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text(
                    "Online Subtitle Search",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                "Searching OpenSubtitles.org. Results are sorted by popularity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Search bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Movie / TV show name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                )
                Button(
                    onClick = {
                        if (query.isBlank()) return@Button
                        scope.launch {
                            isSearching = true
                            statusMsg   = ""
                            results     = emptyList()
                            when (val r = SubtitleSearchService.search(query)) {
                                is SubtitleSearchResult.Success -> {
                                    results   = r.results
                                    statusMsg = if (r.results.isEmpty()) "No results found." else ""
                                }
                                is SubtitleSearchResult.Error -> statusMsg = r.message
                            }
                            isSearching = false
                        }
                    },
                    enabled = !isSearching,
                ) {
                    if (isSearching) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                    ) else Text("Search")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (statusMsg.isNotBlank()) {
                Text(statusMsg, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Results list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(results, key = { it.id }) { result ->
                    val isDownloading = downloadingId == result.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDownloading) {
                                scope.launch {
                                    downloadingId = result.id
                                    statusMsg = "Downloading…"
                                    val dl = SubtitleSearchService.download(
                                        fileId    = result.fileId,
                                        cacheDir  = cacheDir,
                                        filename  = query.take(40),
                                    )
                                    downloadingId = null
                                    when (dl) {
                                        is SubtitleDownloadResult.Success -> {
                                            statusMsg = "Downloaded: ${dl.file.name}"
                                            onSubtitleDownloaded(dl.file)
                                            sheetState.hide()
                                            onDismiss()
                                        }
                                        is SubtitleDownloadResult.Error -> {
                                            statusMsg = dl.message
                                        }
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    result.releaseName.ifBlank { query },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LangChip(result.language.uppercase())
                                    Text(
                                        "${result.format.uppercase()} · ${result.downloadCount} dl",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun LangChip(lang: String) {
    Text(
        lang,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
