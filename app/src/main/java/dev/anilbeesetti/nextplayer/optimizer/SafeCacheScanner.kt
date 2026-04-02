package dev.anilbeesetti.nextplayer.optimizer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─── Data ─────────────────────────────────────────────────────────────────────

data class CacheScanResult(
    val internalCacheBytes: Long,
    val externalCacheBytes: Long,
    val vlcThumbnailBytes: Long,
    val tempPlaybackBytes: Long,
    val subtitleCacheBytes: Long,
    val totalBytes: Long = internalCacheBytes + externalCacheBytes +
        vlcThumbnailBytes + tempPlaybackBytes + subtitleCacheBytes,
)

data class CleanResult(
    val freedBytes: Long,
    val deletedFiles: Int,
    val errors: Int,
)

// ─── Scanner ──────────────────────────────────────────────────────────────────
//
// POLICY: This class ONLY accesses directories that belong exclusively to this
// app (returned by Context-scoped accessors or sub-paths thereof).
// It NEVER reads other apps' data, system directories, or user media files.
// All I/O is on Dispatchers.IO to keep the main thread responsive.

object SafeCacheScanner {

    private const val TAG = "SafeCacheScanner"

    // ── Scan ──────────────────────────────────────────────────────────────────

    /**
     * Measures the total size of all cleanable app-owned cache categories.
     * Returns a [CacheScanResult] with per-category byte counts.
     *
     * Safe directories scanned (all owned by this app):
     *  1. [Context.getCacheDir]           — Android internal cache partition
     *  2. [Context.getExternalCacheDir]   — SD-card / external cache (may be null)
     *  3. filesDir/.vlc/                  — LibVLC thumbnail and artwork cache
     *  4. filesDir/temp/                  — Our own temporary playback extracts
     *  5. cacheDir/subtitles/             — Downloaded .srt files from SubtitleSearchService
     */
    suspend fun scan(context: Context): CacheScanResult = withContext(Dispatchers.IO) {
        val internalCache  = measureDir(context.cacheDir, exclude = setOf("subtitles"))
        val externalCache  = measureDir(context.externalCacheDir)
        val vlcThumbnails  = measureDir(File(context.filesDir, ".vlc"))
                           + measureDir(File(context.filesDir, "vlc"))
                           + measureDir(File(context.cacheDir, "vlc-thumbs"))
        val tempPlayback   = measureDir(File(context.filesDir, "temp"))
                           + measureDir(File(context.cacheDir, "temp"))
                           + measureDir(File(context.externalCacheDir, "temp"))
        val subCache       = measureDir(File(context.cacheDir, "subtitles"))

        CacheScanResult(
            internalCacheBytes  = internalCache,
            externalCacheBytes  = externalCache,
            vlcThumbnailBytes   = vlcThumbnails,
            tempPlaybackBytes   = tempPlayback,
            subtitleCacheBytes  = subCache,
        )
    }

    // ── Clean ─────────────────────────────────────────────────────────────────

    /**
     * Deletes all cleanable app-owned cache files.
     * Returns a [CleanResult] with freed bytes, deleted file count, and errors.
     *
     * This function is intentionally conservative:
     *  — It only deletes **files**, never top-level cache directories themselves,
     *    so the OS can recreate them as needed.
     *  — Directories are emptied recursively but the root dir is kept.
     */
    suspend fun clean(context: Context): CleanResult = withContext(Dispatchers.IO) {
        var freed   = 0L
        var deleted = 0
        var errors  = 0

        fun deleteDir(dir: File?) {
            dir?.walkBottomUp()?.forEach { f ->
                if (f == dir) return@forEach      // keep root
                val size = if (f.isFile) f.length() else 0L
                val ok   = f.delete()
                if (ok) {
                    freed   += size
                    if (f.isFile) deleted++
                } else if (f.exists()) {
                    Log.w(TAG, "Could not delete: ${f.absolutePath}")
                    errors++
                }
            }
        }

        // 1. Internal cache (excluding subtitles — user may want to keep them)
        context.cacheDir?.listFiles()
            ?.filterNot { it.name == "subtitles" }
            ?.forEach { deleteDir(it) }

        // 2. External cache
        deleteDir(context.externalCacheDir)

        // 3. VLC thumbnail caches
        deleteDir(File(context.filesDir, ".vlc"))
        deleteDir(File(context.filesDir, "vlc"))
        deleteDir(File(context.cacheDir, "vlc-thumbs"))

        // 4. Temp playback files
        deleteDir(File(context.filesDir, "temp"))
        deleteDir(File(context.cacheDir, "temp"))
        deleteDir(File(context.externalCacheDir, "temp"))

        // 5. Subtitle cache
        deleteDir(File(context.cacheDir, "subtitles"))

        CleanResult(freedBytes = freed, deletedFiles = deleted, errors = errors)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun measureDir(dir: File?, exclude: Set<String> = emptySet()): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { f -> f.isFile && f.parentFile?.name !in exclude }
            .sumOf { it.length() }
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /** Format a byte count as a human-readable string (B / KB / MB / GB). */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1_024L                -> "$bytes B"
        bytes < 1_048_576L            -> "%.1f KB".format(bytes / 1_024f)
        bytes < 1_073_741_824L        -> "%.2f MB".format(bytes / 1_048_576f)
        else                          -> "%.2f GB".format(bytes / 1_073_741_824f)
    }
}
