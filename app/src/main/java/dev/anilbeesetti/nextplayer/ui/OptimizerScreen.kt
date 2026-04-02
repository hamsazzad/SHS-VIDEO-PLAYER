package com.shs.videoplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shs.videoplayer.core.ui.designsystem.NextIcons
import com.shs.videoplayer.optimizer.CacheScanResult
import com.shs.videoplayer.optimizer.SafeCacheScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── States ────────────────────────────────────────────────────────────────────

private enum class OptimizerState {
    IDLE,       // Fresh — no scan yet
    SCANNING,   // Scanning in progress
    SCANNED,    // Scan complete, showing results
    CLEANING,   // Deletion in progress
    DONE,       // Clean complete, show success
}

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var state       by remember { mutableStateOf(OptimizerState.IDLE) }
    var scanResult  by remember { mutableStateOf<CacheScanResult?>(null) }
    var freedBytes  by remember { mutableStateOf(0L) }
    var freedFiles  by remember { mutableStateOf(0) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    // Auto-scan on first entry
    LaunchedEffect(Unit) {
        state = OptimizerState.SCANNING
        scanResult = SafeCacheScanner.scan(context)
        state = OptimizerState.SCANNED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Optimizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Main content ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                // ── Hero card ─────────────────────────────────────────────────
                HeroCard(state = state, scanResult = scanResult)

                // ── Breakdown cards ───────────────────────────────────────────
                AnimatedVisibility(
                    visible = state == OptimizerState.SCANNED && scanResult != null,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                ) {
                    scanResult?.let { r ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Cache Breakdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            CacheRow(
                                icon = Icons.Rounded.Delete,
                                label = "App Internal Cache",
                                bytes = r.internalCacheBytes,
                                totalBytes = r.totalBytes,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            CacheRow(
                                icon = Icons.Rounded.FolderDelete,
                                label = "External Cache",
                                bytes = r.externalCacheBytes,
                                totalBytes = r.totalBytes,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            CacheRow(
                                icon = Icons.Rounded.Image,
                                label = "VLC Thumbnails",
                                bytes = r.vlcThumbnailBytes,
                                totalBytes = r.totalBytes,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            CacheRow(
                                icon = Icons.Rounded.Tune,
                                label = "Temp Playback Files",
                                bytes = r.tempPlaybackBytes,
                                totalBytes = r.totalBytes,
                                color = Color(0xFF9C27B0),
                            )
                            CacheRow(
                                icon = Icons.Rounded.Subtitles,
                                label = "Subtitle Cache",
                                bytes = r.subtitleCacheBytes,
                                totalBytes = r.totalBytes,
                                color = Color(0xFFE65100),
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Total reclaimable",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    SafeCacheScanner.formatBytes(r.totalBytes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (r.totalBytes > 0)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // ── Notice card ───────────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp),
                        )
                        Text(
                            "Only SHS Player's own cache and temporary files are scanned and removed. " +
                                "No other apps, user media, or system files are touched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(
                        errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // ── Action buttons ────────────────────────────────────────────
                when (state) {
                    OptimizerState.SCANNED -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    state = OptimizerState.CLEANING
                                    val r = runCatching { SafeCacheScanner.clean(context) }
                                    if (r.isSuccess) {
                                        val res = r.getOrThrow()
                                        freedBytes = res.freedBytes
                                        freedFiles = res.deletedFiles
                                        if (res.errors > 0) {
                                            errorMsg = "${res.errors} file(s) could not be deleted."
                                        }
                                        state = OptimizerState.DONE
                                    } else {
                                        errorMsg = r.exceptionOrNull()?.message ?: "Unknown error"
                                        state = OptimizerState.SCANNED
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            enabled = scanResult != null,
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if ((scanResult?.totalBytes ?: 0L) > 0)
                                    "Optimize App  (${SafeCacheScanner.formatBytes(scanResult!!.totalBytes)})"
                                else
                                    "Nothing to Clean",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    OptimizerState.CLEANING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("Cleaning up...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OptimizerState.DONE -> {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    errorMsg = null
                                    state = OptimizerState.SCANNING
                                    scanResult = SafeCacheScanner.scan(context)
                                    state = OptimizerState.SCANNED
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Scan Again", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> { /* IDLE / SCANNING handled in HeroCard */ }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Success overlay ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = state == OptimizerState.DONE,
                enter = fadeIn(tween(300)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit  = fadeOut(tween(200)) + scaleOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                SuccessOverlay(freedBytes = freedBytes, freedFiles = freedFiles)
            }
        }
    }
}

// ─── Hero Card ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(state: OptimizerState, scanResult: CacheScanResult?) {
    val totalBytes = scanResult?.totalBytes ?: 0L
    val progress   by animateFloatAsState(
        targetValue  = if (state == OptimizerState.SCANNING) 0f
                       else if (totalBytes > 0) 1f else 0f,
        animationSpec = tween(800),
        label        = "scan_progress",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    ),
            ) {
                when (state) {
                    OptimizerState.SCANNING -> CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    OptimizerState.DONE ->
                        Icon(Icons.Rounded.Check, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp))
                    else ->
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                }
            }

            Text(
                text = when (state) {
                    OptimizerState.IDLE, OptimizerState.SCANNING -> "Scanning…"
                    OptimizerState.SCANNED ->
                        if (totalBytes > 0) SafeCacheScanner.formatBytes(totalBytes)
                        else "No Junk Found"
                    OptimizerState.CLEANING -> "Cleaning…"
                    OptimizerState.DONE     -> "All Clean!"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(
                text = when (state) {
                    OptimizerState.SCANNING -> "Scanning app cache directories…"
                    OptimizerState.SCANNED  ->
                        if (totalBytes > 0) "Junk files found — tap Optimize to free space"
                        else "Your app is already clean"
                    OptimizerState.CLEANING -> "Removing temporary and cache files…"
                    OptimizerState.DONE     -> "Player cache has been successfully cleared"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            if (state == OptimizerState.SCANNING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

// ─── Cache Row ─────────────────────────────────────────────────────────────────

@Composable
private fun CacheRow(
    icon: ImageVector,
    label: String,
    bytes: Long,
    totalBytes: Long,
    color: Color,
) {
    val fraction = if (totalBytes > 0) bytes.toFloat() / totalBytes else 0f
    val progress by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "cache_row_$label",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = color.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color,
                            modifier = Modifier.size(18.dp))
                    }
                }
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text(
                    if (bytes > 0) SafeCacheScanner.formatBytes(bytes) else "0 B",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (bytes > 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (bytes > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (bytes > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.12f),
                )
            }
        }
    }
}

// ─── Success Overlay ───────────────────────────────────────────────────────────

@Composable
private fun SuccessOverlay(freedBytes: Long, freedFiles: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Animated check circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32).copy(alpha = 0.12f)),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(48.dp),
                )
            }

            Text(
                "Player Optimized!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                textAlign = TextAlign.Center,
            )

            Text(
                buildString {
                    if (freedBytes > 0) {
                        append(SafeCacheScanner.formatBytes(freedBytes))
                        append(" freed")
                    } else {
                        append("Cache cleared")
                    }
                    if (freedFiles > 0) append(" · $freedFiles files removed")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Text(
                "SHS Player is running at peak performance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
