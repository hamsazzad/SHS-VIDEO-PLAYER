package dev.anilbeesetti.nextplayer.feature.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.res.Configuration
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.extensions.registerForSuspendActivityResult
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.addSubtitleTrack
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.player.utils.ScreenshotUtil
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import android.view.SurfaceHolder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import kotlinx.coroutines.withContext

val LocalHidePlayerButtonsBackground = compositionLocalOf { false }

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    val playerPreferences get() = viewModel.uiState.value.playerPreferences

    private val onWindowAttributesChangedListener = CopyOnWriteArrayList<Consumer<WindowManager.LayoutParams?>>()

    private var isPlaybackFinished = false
    private var playInBackground: Boolean = false
    private var isIntentNew: Boolean = true

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var playerApi: PlayerApi

    private val playbackStateListener: Player.Listener = playbackStateListener()

    // LibVLC engine instance
    private lateinit var libVLC: LibVLC

    // VLC MediaPlayer — bridges the engine to the video output pipeline
    private lateinit var vlcMediaPlayer: VlcMediaPlayer

    // Dedicated SurfaceView for VLC video frame rendering
    private lateinit var vlcSurfaceView: SurfaceView

    // ── VLC controller suite ──────────────────────────────────────────────────

    /** Subtitle delay / font-size / color / shadow control. */
    lateinit var vlcSubtitleController: VlcSubtitleController
        private set

    /** Audio delay / 10-band EQ / passthrough / spatializer control. */
    lateinit var vlcAudioController: VlcAudioController
        private set

    /** Network stream loader — HLS / RTSP / RTMP / SMB / FTP / DLNA. */
    lateinit var vlcNetworkManager: VlcNetworkManager
        private set

    /** Chapter list retrieval and chapter-indexed seeking (fixes non-seekable files). */
    lateinit var vlcChapterController: VlcChapterController
        private set

    /** Aspect ratio and crop-mode control (Fit / Fill / 16:9 / 4:3 / Stretch). */
    lateinit var vlcAspectController: VlcAspectController
        private set

    /** True while HW decoding is active; flipped to false on decode error. */
    private var hwDecodingActive = true


    /**
     * Mirrors Media3 play/pause/seek events to the LibVLC engine.
     * This is what actually drives vlcMediaPlayer in sync with the existing UI.
     */
    private val vlcMirrorListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!::vlcMediaPlayer.isInitialized) return
            if (isPlaying) vlcMediaPlayer.play() else vlcMediaPlayer.pause()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!::vlcMediaPlayer.isInitialized) return
            // Forward any seek event (user drag, bookmark, A/B repeat) to VLC
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                vlcMediaPlayer.setTime(newPosition.positionMs)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Load the new media item into VLC when the playlist advances
            val uri = mediaItem?.localConfiguration?.uri ?: return
            loadVlcMedia(uri)
        }
    }

    private val subtitleFileSuspendLauncher = registerForSuspendActivityResult(OpenDocument())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── LibVLC engine — full hardware decoding + 4K/HDR/HEVC optimisation ─────
        libVLC = LibVLC(this, ArrayList<String>().apply {

            // Hardware codec priority chain:
            //  1. MediaCodec NDK (zero-copy, fastest path)         ← primary HW
            //  2. MediaCodec JNI (Java wrapper, slightly slower)   ← HW fallback
            //  3. iomx (legacy OpenMAX, older Snapdragon SoCs)     ← HW fallback
            //  4. avcodec / FFmpeg                                  ← SW final fallback
            // VLC tries each codec module in order and falls back automatically
            // whenever a codec refuses to open (e.g. missing HW support for HEVC Main10).
            add("--codec=mediacodec_ndk,mediacodec,iomx,avcodec")

            // Tell avcodec to use HW decoding for any codec it can handle
            // (complements the codec list above for mixed HW+SW pipelines)
            add("--avcodec-hw=any")

            // ── Threading — auto-detect optimal core count ────────────────────
            // 0 = let VLC query Runtime.availableProcessors(); prevents both
            // under-utilisation on octa-core SoCs and over-subscription on low-RAM devices
            add("--avcodec-threads=0")

            // ── Frame-drop / low-end device safety valves ─────────────────────
            // Drop frames that arrive too late to be displayed on time — keeps
            // audio in sync on devices where 4K decode is CPU-bound
            add("--drop-late-frames")
            // Skip non-reference B-frames under sustained load (level 2)
            // Level 0=none · 1=default · 2=B-frames · 3=non-ref · 4=non-key
            add("--avcodec-skip-frame=0")          // start conservative (no skips)
            add("--avcodec-skip-idct=0")           // no IDCT skipping by default
            // Enable hurry-up mode: VLC adjusts skip level dynamically
            // when the decoder falls behind schedule
            add("--avcodec-hurry-up")

            // ── MediaCodec-specific: direct rendering (zero-copy surface output) ─
            // Keeps decoded frames in GPU memory — critical for 4K to avoid
            // the CPU copy that would blow RAM on low-end devices
            add("--mediacodec-dr")

            // ── Buffer tuning ─────────────────────────────────────────────────
            // Local file caching: 1.5 s gives enough read-ahead for 4K bitrates
            // without excessive memory use (most 4K HDR = 40–80 Mbps peak)
            add("--file-caching=1500")
            add("--disk-caching=1500")
            // Network caches — generous defaults; VlcNetworkManager can override per-URL
            add("--live-caching=3000")
            add("--network-caching=3000")

            // ── Memory / runtime savings ──────────────────────────────────────
            add("--no-lua")              // disable Lua interpreter (~10 MB saved)
            add("--no-osd")             // no on-screen display (we render our own UI)
            add("--no-stats")
            add("--no-snapshot-preview")
        })

        // Initialize VLC MediaPlayer bound to the LibVLC instance
        vlcMediaPlayer = VlcMediaPlayer(libVLC)

        // Boot controller suite — lightweight stateless wrappers, instantiated once
        vlcSubtitleController  = VlcSubtitleController(vlcMediaPlayer)
        vlcAudioController     = VlcAudioController(vlcMediaPlayer)
        vlcNetworkManager      = VlcNetworkManager()
        vlcChapterController   = VlcChapterController(vlcMediaPlayer)
        vlcAspectController    = VlcAspectController(vlcMediaPlayer)

        // ── HW decode failure → automatic SW fallback ─────────────────────────
        // VLC fires EncounteredError when a codec module definitively fails.
        // On first error, rebuild the LibVLC engine with SW-only codec order
        // and reload the current media so playback continues uninterrupted.
        vlcMediaPlayer.setEventListener { event ->
            if (event.type == org.videolan.libvlc.MediaPlayer.Event.EncounteredError && hwDecodingActive) {
                hwDecodingActive = false
                runOnUiThread { retryWithSoftwareDecoding() }
            }
        }

        // Create a dedicated SurfaceView and wire VLC video output to it
        vlcSurfaceView = SurfaceView(this).also { sv ->
            sv.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addContentView(sv, sv.layoutParams)
            sv.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    // Link VLC video output to the surface so frames can be rendered
                    val vout = vlcMediaPlayer.vlcVout
                    vout.setVideoSurface(holder.surface, holder)
                    vout.attachViews()
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    // Notify VLC of surface dimension changes for correct scaling
                    vlcMediaPlayer.vlcVout.setWindowSize(width, height)
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    vlcMediaPlayer.vlcVout.detachViews()
                }
            })
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var player by remember { mutableStateOf<MediaController?>(null) }
            var showTrimDialog by remember { mutableStateOf(false) }

            LifecycleStartEffect(Unit) {
                maybeInitControllerFuture()
                lifecycleScope.launch {
                    player = controllerFuture?.await()
                }

                onStopOrDispose {
                    player = null
                }
            }

            CompositionLocalProvider(
                LocalHidePlayerButtonsBackground provides (uiState.playerPreferences?.hidePlayerButtonsBackground == true),
                LocalVlcSeekTo provides { ms: Long ->
                    // Drive VLC's timeline; this is what enables seeking for
                    // formats that Media3/ExoPlayer reports as non-seekable
                    if (::vlcMediaPlayer.isInitialized) vlcMediaPlayer.setTime(ms)
                },
            ) {
                NextPlayerTheme(darkTheme = true) {
                    MediaPlayerScreen(
                        player = player,
                        viewModel = viewModel,
                        playerPreferences = uiState.playerPreferences ?: return@NextPlayerTheme,
                        onSelectSubtitleClick = {
                            lifecycleScope.launch {
                                val uri = subtitleFileSuspendLauncher.launch(
                                    arrayOf(
                                        MimeTypes.APPLICATION_SUBRIP,
                                        MimeTypes.APPLICATION_TTML,
                                        MimeTypes.TEXT_VTT,
                                        MimeTypes.TEXT_SSA,
                                        MimeTypes.BASE_TYPE_APPLICATION + "/octet-stream",
                                        MimeTypes.BASE_TYPE_TEXT + "/*",
                                    ),
                                ) ?: return@launch
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                maybeInitControllerFuture()
                                controllerFuture?.await()?.addSubtitleTrack(uri)
                            }
                        },
                        onBackClick = { finishAndStopPlayerSession() },
                        onPlayInBackgroundClick = {
                            playInBackground = true
                            finish()
                        },
                        onScreenshotClick = { captureScreenshot() },
                        onShareClick = { shareCurrentVideo() },
                        onTrimClick = { showTrimDialog = true },
                        onVideoToAudioClick = { convertVideoToAudio() },
                        onReversePlayClick = { reversePlay() },
                    )

                    if (showTrimDialog) {
                        val currentPlayer = player
                        val videoUri = mediaController?.currentMediaItem?.localConfiguration?.uri ?: intent.data
                        TrimVideoDialog(
                            videoUri = videoUri,
                            durationMs = currentPlayer?.duration?.takeIf { it > 0 } ?: 0L,
                            currentPositionMs = currentPlayer?.currentPosition ?: 0L,
                            onDismiss = { showTrimDialog = false },
                            onTrimConfirmed = { startMs, endMs ->
                                showTrimDialog = false
                                if (videoUri != null) trimVideo(videoUri, startMs, endMs)
                            },
                        )
                    }
                }
            }
        }

        playerApi = PlayerApi(this)
    }

    /**
     * Called by Android whenever the Activity enters or exits PiP mode.
     *
     * When entering PiP, the Activity is paused (onPause fires) but NOT stopped.
     * We ensure VLC keeps rendering to the surface, which remains attached and
     * visible inside the PiP window, giving seamless background playback.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!::vlcMediaPlayer.isInitialized) return

        if (isInPictureInPictureMode) {
            // Surface stays attached — tell VLC to keep playing if it paused on
            // the Activity lifecycle signal (some OEM ROMs fire onPause before PiP)
            if (!vlcMediaPlayer.isPlaying) {
                vlcMediaPlayer.play()
            }
        }
        // Exiting PiP: Media3's existing state machine restores the correct
        // play/pause state via vlcMirrorListener — no extra handling needed here.
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            mediaController?.isPlaying == true
        ) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private fun convertVideoToAudio() {
        val videoUri = mediaController?.currentMediaItem?.localConfiguration?.uri ?: intent.data ?: run {
            Toast.makeText(this, "No video to convert", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Converting to audio...", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val extractor = MediaExtractor()
                    extractor.setDataSource(this@PlayerActivity, videoUri, null)
                    var audioTrackIndex = -1
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioTrackIndex = i
                            break
                        }
                    }
                    if (audioTrackIndex == -1) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PlayerActivity, "No audio track found in this video", Toast.LENGTH_SHORT).show()
                        }
                        extractor.release()
                        return@withContext
                    }
                    extractor.selectTrack(audioTrackIndex)
                    val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                    val originalName = videoUri.lastPathSegment?.substringBeforeLast(".") ?: "audio_${System.currentTimeMillis()}"
                    val outputName = "${originalName}_audio.m4a"
                    val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
                    val bufferInfo = MediaCodec.BufferInfo()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, outputName)
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                        }
                        val outputUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        if (outputUri == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@PlayerActivity, "Failed to create output file", Toast.LENGTH_SHORT).show()
                            }
                            extractor.release()
                            return@withContext
                        }
                        contentResolver.openFileDescriptor(outputUri, "w")?.use { pfd ->
                            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                            val audioTrackMuxer = muxer.addTrack(audioFormat)
                            muxer.start()
                            while (true) {
                                val chunkSize = extractor.readSampleData(buffer, 0)
                                if (chunkSize < 0) break
                                bufferInfo.size = chunkSize
                                bufferInfo.offset = 0
                                bufferInfo.presentationTimeUs = extractor.sampleTime
                                bufferInfo.flags = extractor.sampleFlags
                                muxer.writeSampleData(audioTrackMuxer, buffer, bufferInfo)
                                extractor.advance()
                            }
                            muxer.stop()
                            muxer.release()
                        }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                        dir.mkdirs()
                        val file = File(dir, outputName)
                        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        val audioTrackMuxer = muxer.addTrack(audioFormat)
                        muxer.start()
                        while (true) {
                            val chunkSize = extractor.readSampleData(buffer, 0)
                            if (chunkSize < 0) break
                            bufferInfo.size = chunkSize
                            bufferInfo.offset = 0
                            bufferInfo.presentationTimeUs = extractor.sampleTime
                            bufferInfo.flags = extractor.sampleFlags
                            muxer.writeSampleData(audioTrackMuxer, buffer, bufferInfo)
                            extractor.advance()
                        }
                        muxer.stop()
                        muxer.release()
                    }
                    extractor.release()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PlayerActivity, "Audio saved to Music: $outputName", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PlayerActivity, "Conversion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun trimVideo(videoUri: Uri, startMs: Long, endMs: Long) {
        Toast.makeText(this, "Trimming video...", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val extractor = MediaExtractor()
                    extractor.setDataSource(this@PlayerActivity, videoUri, null)

                    val startUs = startMs * 1000L
                    val endUs = endMs * 1000L
                    val originalName = videoUri.lastPathSegment?.substringBeforeLast(".") ?: "video_${System.currentTimeMillis()}"
                    val outputName = "${originalName}_trimmed.mp4"
                    val buffer = java.nio.ByteBuffer.allocate(2 * 1024 * 1024)
                    val bufferInfo = MediaCodec.BufferInfo()

                    val doTrim: (MediaMuxer) -> Unit = { muxer ->
                        val trackMap = mutableMapOf<Int, Int>()
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                                extractor.selectTrack(i)
                                trackMap[i] = muxer.addTrack(format)
                            }
                        }
                        muxer.start()

                        for ((srcTrack, dstTrack) in trackMap) {
                            extractor.unselectTrack(srcTrack)
                        }
                        for ((srcTrack, dstTrack) in trackMap) {
                            extractor.selectTrack(srcTrack)
                            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            while (true) {
                                val chunkSize = extractor.readSampleData(buffer, 0)
                                if (chunkSize < 0) break
                                val sampleTime = extractor.sampleTime
                                if (sampleTime > endUs) break
                                if (sampleTime >= startUs && extractor.sampleTrackIndex == srcTrack) {
                                    bufferInfo.size = chunkSize
                                    bufferInfo.offset = 0
                                    bufferInfo.presentationTimeUs = sampleTime - startUs
                                    bufferInfo.flags = extractor.sampleFlags
                                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                                }
                                extractor.advance()
                            }
                            extractor.unselectTrack(srcTrack)
                        }
                        muxer.stop()
                        muxer.release()
                        extractor.release()
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                        }
                        val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (outputUri == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@PlayerActivity, "Failed to create output file", Toast.LENGTH_SHORT).show()
                            }
                            extractor.release()
                            return@withContext
                        }
                        contentResolver.openFileDescriptor(outputUri, "w")?.use { pfd ->
                            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                            doTrim(muxer)
                        }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                        dir.mkdirs()
                        val file = File(dir, outputName)
                        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        doTrim(muxer)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PlayerActivity, "Trimmed video saved to Movies: $outputName", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PlayerActivity, "Trim failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun reversePlay() {
        val duration = mediaController?.duration ?: 0L
        val position = mediaController?.currentPosition ?: 0L
        if (duration > 0) {
            mediaController?.seekTo(duration - position)
            mediaController?.play()
            Toast.makeText(this, "Playing from end. Use 2x long-press speed for fast playback.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCurrentVideo() {
        val videoUri = mediaController?.currentMediaItem?.localConfiguration?.uri ?: intent.data ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(coreUiR.string.share_video)))
    }

    private fun captureScreenshot() {
        val decorView = window.decorView
        val surfaceView = findSurfaceView(decorView)

        if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val bitmap = Bitmap.createBitmap(
                surfaceView.width,
                surfaceView.height,
                Bitmap.Config.ARGB_8888,
            )
            try {
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            ScreenshotUtil.saveScreenshot(this, bitmap)
                        } else {
                            Toast.makeText(this, coreUiR.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, coreUiR.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                decorView.isDrawingCacheEnabled = true
                decorView.buildDrawingCache()
                val bitmap = Bitmap.createBitmap(decorView.drawingCache)
                decorView.isDrawingCacheEnabled = false
                ScreenshotUtil.saveScreenshot(this, bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, coreUiR.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findSurfaceView(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release VLC surface + engine resources
        if (::vlcMediaPlayer.isInitialized) {
            vlcMediaPlayer.vlcVout.detachViews()
            vlcMediaPlayer.release()
        }
        if (::libVLC.isInitialized) libVLC.release()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            maybeInitControllerFuture()
            mediaController = controllerFuture?.await()

            mediaController?.run {
                updateKeepScreenOnFlag()
                addListener(playbackStateListener)
                addListener(vlcMirrorListener)
                startPlayback()
            }
        }
    }

    override fun onStop() {
        mediaController?.run {
            viewModel.playWhenReady = playWhenReady
            removeListener(playbackStateListener)
            removeListener(vlcMirrorListener)
        }
        val shouldPlayInBackground = playInBackground || playerPreferences?.autoBackgroundPlay == true
        if (subtitleFileSuspendLauncher.isAwaitingResult || !shouldPlayInBackground) {
            mediaController?.pause()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            finish()
            if (!shouldPlayInBackground) {
                mediaController?.stopPlayerSession()
            }
        }

        controllerFuture?.run {
            MediaController.releaseFuture(this)
            controllerFuture = null
        }
        super.onStop()
    }

    private fun maybeInitControllerFuture() {
        if (controllerFuture == null) {
            val sessionToken = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
            controllerFuture = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        }
    }


    /**
     * Creates a LibVLC [Media] object from [uri] and assigns it to [vlcMediaPlayer].
     * Does NOT call play() — [vlcMirrorListener.onIsPlayingChanged] handles that
     * once Media3 transitions to the playing state.
     */
    /**
     * Called automatically when LibVLC reports a decode error while HW decoding is active.
     * Re-initialises the LibVLC engine with a SW-only codec list (avcodec / FFmpeg)
     * and reloads the current media so playback continues without user intervention.
     *
     * After this point [hwDecodingActive] remains false for the session — we don't
     * oscillate between HW and SW to prevent an error loop.
     */
    private fun retryWithSoftwareDecoding() {
        if (!::vlcMediaPlayer.isInitialized || !::libVLC.isInitialized) return
        val currentUri = intent.data ?: return

        // Release current engine
        vlcMediaPlayer.setEventListener(null)
        vlcMediaPlayer.stop()
        vlcMediaPlayer.vlcVout.detachViews()
        vlcMediaPlayer.release()
        libVLC.release()

        // Rebuild LibVLC with SW-only codec — avcodec (FFmpeg) handles virtually
        // every format and codec combination via pure software decoding
        libVLC = LibVLC(this, ArrayList<String>().apply {
            add("--codec=avcodec")        // software-only FFmpeg pipeline
            add("--avcodec-hw=none")      // explicitly disable HW acceleration
            add("--avcodec-threads=0")    // still use all cores for SW decode
            add("--drop-late-frames")
            add("--file-caching=2000")
            add("--no-stats")
            add("--no-snapshot-preview")
            add("--no-lua")
            add("--no-osd")
        })

        // Re-create player and re-attach surface
        vlcMediaPlayer = org.videolan.libvlc.MediaPlayer(libVLC)
        val vout = vlcMediaPlayer.vlcVout
        vlcSurfaceView.holder.surface?.let { surface ->
            vout.setVideoSurface(surface, vlcSurfaceView.holder)
            vout.attachViews()
        }

        // Re-boot controller suite against the new player instance
        vlcSubtitleController  = VlcSubtitleController(vlcMediaPlayer)
        vlcAudioController     = VlcAudioController(vlcMediaPlayer)
        vlcChapterController   = VlcChapterController(vlcMediaPlayer)
        vlcAspectController    = VlcAspectController(vlcMediaPlayer)
        // vlcNetworkManager is stateless — no rebuild needed

        // Reload the media with SW engine
        loadVlcMedia(currentUri)
    }

    /**
     * Creates a [Media] from a local or content [Uri] and assigns it to [vlcMediaPlayer].
     * Subtitle styling (font / color / shadow) and audio flags (passthrough / spatializer)
     * are injected as media options so they survive playlist transitions.
     */
    private fun loadVlcMedia(uri: Uri) {
        if (!::libVLC.isInitialized || !::vlcMediaPlayer.isInitialized) return
        val media = Media(libVLC, uri)

        // Inject per-media subtitle styling (font size, color, shadow)
        if (::vlcSubtitleController.isInitialized) vlcSubtitleController.applyToMedia(media)

        // Inject per-media audio flags (passthrough, spatializer)
        if (::vlcAudioController.isInitialized) vlcAudioController.applyToMedia(media)

        vlcMediaPlayer.media = media
        media.release() // ownership transferred to vlcMediaPlayer; safe to release wrapper

        // Re-apply current aspect mode to the new media surface
        if (::vlcAspectController.isInitialized) {
            vlcAspectController.applyMode(vlcAspectController.currentMode)
        }
    }

    /**
     * Load and play any network stream URL via LibVLC.
     * Protocol is auto-detected; buffering options are applied per protocol.
     *
     * Supported: HTTP/HTTPS, HLS (m3u8), RTSP (ip cameras), RTMP (live),
     *            SMB (Samba/Windows shares), FTP (local servers), DLNA/UPnP.
     *
     * Example:
     *   loadNetworkStream("rtsp://192.168.1.50/stream")
     *   loadNetworkStream("smb://NAS/Videos/movie.mkv")
     *   loadNetworkStream("http://cdn.example.com/live/stream.m3u8")
     */
    fun loadNetworkStream(url: String) {
        if (!::libVLC.isInitialized || !::vlcMediaPlayer.isInitialized) return

        val media = vlcNetworkManager.createMedia(url, libVLC)

        // Apply subtitle and audio settings to the network media as well
        if (::vlcSubtitleController.isInitialized) vlcSubtitleController.applyToMedia(media)
        if (::vlcAudioController.isInitialized) vlcAudioController.applyToMedia(media)

        vlcMediaPlayer.media = media
        media.release()
        vlcMediaPlayer.play()
    }

    private fun startPlayback() {
        val uri = intent.data ?: return
        viewModel.setCurrentVideoUri(uri.toString())

        val returningFromBackground = !isIntentNew && mediaController?.currentMediaItem != null
        val isNewUriTheCurrentMediaItem = mediaController?.currentMediaItem?.localConfiguration?.uri.toString() == uri.toString()

        if (returningFromBackground || isNewUriTheCurrentMediaItem) {
            mediaController?.prepare()
            mediaController?.playWhenReady = viewModel.playWhenReady
            return
        }

        isIntentNew = false

        lifecycleScope.launch {
            playVideo(uri)
        }
    }

    private suspend fun playVideo(uri: Uri) = withContext(Dispatchers.Default) {
        val mediaContentUri = getMediaContentUri(uri)
        val playlist = playerApi.getPlaylist().takeIf { it.isNotEmpty() }
            ?: mediaContentUri?.let { mediaUri ->
                viewModel.getPlaylistFromUri(mediaUri)
                    .map { it.uriString }
                    .toMutableList()
                    .apply {
                        if (!contains(mediaUri.toString())) {
                            add(index = 0, element = mediaUri.toString())
                        }
                    }
            } ?: listOf(uri.toString())

        val mediaItemIndexToPlay = playlist.indexOfFirst {
            it == (mediaContentUri ?: uri).toString()
        }.takeIf { it >= 0 } ?: 0

        val mediaItems = playlist.mapIndexed { index, uri ->
            MediaItem.Builder().apply {
                setUri(uri)
                setMediaId(uri)
                if (index == mediaItemIndexToPlay) {
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(playerApi.title)
                            setExtras(positionMs = playerApi.position?.toLong())
                        }.build(),
                    )
                    val apiSubs = playerApi.getSubs().map { subtitle ->
                        uriToSubtitleConfiguration(
                            uri = subtitle.uri,
                            subtitleEncoding = playerPreferences?.subtitleTextEncoding ?: "",
                            isSelected = subtitle.isSelected,
                        )
                    }
                    setSubtitleConfigurations(apiSubs)
                }
            }.build()
        }

        withContext(Dispatchers.Main) {
            mediaController?.run {
                setMediaItems(mediaItems, mediaItemIndexToPlay, playerApi.position?.toLong() ?: C.TIME_UNSET)
                playWhenReady = viewModel.playWhenReady
                prepare()
            }
            // Map the resolved URI into a LibVLC Media object so VLC is ready
            // to render frames as soon as the surface is attached
            val vlcUri = mediaContentUri ?: uri
            loadVlcMedia(vlcUri)
        }
    }

    private fun playbackStateListener() = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            intent.data = mediaItem?.localConfiguration?.uri
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            updateKeepScreenOnFlag()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_ENDED -> {
                    isPlaybackFinished = mediaController?.playbackState == Player.STATE_ENDED
                    finishAndStopPlayerSession()
                }
                else -> {}
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                if (mediaController?.repeatMode != Player.REPEAT_MODE_OFF) return
                isPlaybackFinished = true
                finishAndStopPlayerSession()
            }
        }
    }

    override fun finish() {
        if (playerApi.shouldReturnResult) {
            val result = playerApi.getResult(
                isPlaybackFinished = isPlaybackFinished,
                duration = mediaController?.duration ?: C.TIME_UNSET,
                position = mediaController?.currentPosition ?: C.TIME_UNSET,
            )
            setResult(RESULT_OK, result)
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            setIntent(intent)
            isIntentNew = true
            if (mediaController != null) {
                startPlayback()
            }
        }
    }

    private fun updateKeepScreenOnFlag() {
        if (mediaController?.isPlaying == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun finishAndStopPlayerSession() {
        finish()
        mediaController?.stopPlayerSession()
    }

    override fun onWindowAttributesChanged(params: WindowManager.LayoutParams?) {
        super.onWindowAttributesChanged(params)
        for (listener in onWindowAttributesChangedListener) {
            listener.accept(params)
        }
    }

    fun addOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.add(listener)
    }

    fun removeOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.remove(listener)
    }
}

@androidx.compose.runtime.Composable
private fun TrimVideoDialog(
    videoUri: Uri?,
    durationMs: Long,
    currentPositionMs: Long,
    onDismiss: () -> Unit,
    onTrimConfirmed: (startMs: Long, endMs: Long) -> Unit,
) {
    val safeDuration = if (durationMs > 0) durationMs else 60_000L
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(safeDuration) }

    LaunchedEffect(durationMs) {
        startMs = 0L
        endMs = safeDuration
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trim Video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (videoUri == null) {
                    Text("No video loaded.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Duration: ${formatTime(safeDuration)}", style = MaterialTheme.typography.bodySmall)

                    Text("Start: ${formatTime(startMs)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = startMs.toFloat(),
                        onValueChange = { startMs = it.toLong().coerceAtMost(endMs - 1000L) },
                        valueRange = 0f..safeDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("End: ${formatTime(endMs)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = endMs.toFloat(),
                        onValueChange = { endMs = it.toLong().coerceAtLeast(startMs + 1000L) },
                        valueRange = 0f..safeDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        "Trimmed length: ${formatTime(endMs - startMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Output will be saved to Movies folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTrimConfirmed(startMs, endMs) },
                enabled = videoUri != null && (endMs - startMs) >= 1000L,
            ) { Text("Trim & Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
