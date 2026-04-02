package com.shs.videoplayer.feature.player.subtitles

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ─── Data models ──────────────────────────────────────────────────────────────

data class SubtitleResult(
    val id: String,
    val language: String,
    val releaseName: String,
    val downloadUrl: String,
    val format: String,             // "srt" | "vtt" | "ass" | etc.
    val downloadCount: Int,
    val fileId: String,
)

sealed class SubtitleSearchResult {
    data class Success(val results: List<SubtitleResult>) : SubtitleSearchResult()
    data class Error(val message: String) : SubtitleSearchResult()
}

sealed class SubtitleDownloadResult {
    data class Success(val file: File) : SubtitleDownloadResult()
    data class Error(val message: String) : SubtitleDownloadResult()
}

// ─── Service ──────────────────────────────────────────────────────────────────

object SubtitleSearchService {

    private const val TAG = "SubtitleSearchService"

    // OpenSubtitles.org REST API v1 — anonymous access, no API key required for
    // search. Download links are direct when publicly available.
    //
    // Endpoint: https://opensubtitles.org/api/v1/subtitles
    // Docs:     https://opensubtitles.stoplight.io/docs/opensubtitles-api
    private const val SEARCH_BASE = "https://api.opensubtitles.com/api/v1/subtitles"
    private const val APP_API_KEY  = "shs_player_v1"   // public anonymous key

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Search OpenSubtitles for subtitles matching [query] (typically the video filename
     * without extension) in [languages] (comma-separated BCP-47 codes, e.g. "en,fr").
     *
     * Runs on [Dispatchers.IO]. Returns up to 20 results sorted by download count.
     */
    suspend fun search(
        query: String,
        languages: String = "en",
        page: Int = 1,
    ): SubtitleSearchResult = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$SEARCH_BASE?query=$encoded&languages=$languages&page=$page"
            val json = getJson(url) ?: return@withContext SubtitleSearchResult.Error("No response from server")

            val dataArray: JSONArray = json.optJSONArray("data")
                ?: return@withContext SubtitleSearchResult.Success(emptyList())

            val results = mutableListOf<SubtitleResult>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val attrs = item.optJSONObject("attributes") ?: continue
                val files = attrs.optJSONArray("files") ?: continue
                val file0 = if (files.length() > 0) files.getJSONObject(0) else continue
                val fileId = file0.optString("file_id", "")
                if (fileId.isBlank()) continue

                results.add(
                    SubtitleResult(
                        id           = item.optString("id", ""),
                        language     = attrs.optString("language", "?"),
                        releaseName  = attrs.optString("release", attrs.optString("feature_details.movie_name", query)),
                        downloadUrl  = "",          // fetched separately at download time
                        format       = file0.optString("file_name", "srt")
                            .substringAfterLast('.', "srt").lowercase(),
                        downloadCount = attrs.optInt("download_count", 0),
                        fileId       = fileId,
                    ),
                )
            }
            // Sort by download count — most popular first
            SubtitleSearchResult.Success(results.sortedByDescending { it.downloadCount })
        } catch (e: Exception) {
            Log.e(TAG, "search() failed", e)
            SubtitleSearchResult.Error("Search failed: ${e.message}")
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Downloads the subtitle identified by [fileId] into [cacheDir].
     * Returns the downloaded [File] so the caller can load it into LibVLC.
     *
     * The OpenSubtitles download endpoint returns a temporary signed URL;
     * we follow it to get the actual content.
     */
    suspend fun download(
        fileId: String,
        cacheDir: File,
        filename: String = "downloaded_subtitle",
    ): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: get the signed download link
            val body = """{"file_id":$fileId}"""
            val linkJson = postJson("https://api.opensubtitles.com/api/v1/download", body)
                ?: return@withContext SubtitleDownloadResult.Error("Could not obtain download link")

            val signedUrl = linkJson.optString("link", "")
            if (signedUrl.isBlank()) {
                return@withContext SubtitleDownloadResult.Error("No download URL in response")
            }

            // Step 2: download raw subtitle bytes
            val ext  = signedUrl.substringAfterLast('.', "srt").take(10)
            val dest = File(cacheDir, "${filename.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}.$ext")
            URL(signedUrl).openStream().use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            SubtitleDownloadResult.Success(dest)
        } catch (e: Exception) {
            Log.e(TAG, "download() failed", e)
            SubtitleDownloadResult.Error("Download failed: ${e.message}")
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun getJson(urlStr: String): JSONObject? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("Api-Key", APP_API_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "SHSPlayer/1.0")
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000
            if (conn.responseCode == 200) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                Log.w(TAG, "GET $urlStr → HTTP ${conn.responseCode}")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(urlStr: String, body: String): JSONObject? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Api-Key", APP_API_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "SHSPlayer/1.0")
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200 || conn.responseCode == 201) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                Log.w(TAG, "POST $urlStr → HTTP ${conn.responseCode}")
                null
            }
        } finally {
            conn.disconnect()
        }
    }
}
