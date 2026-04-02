package dev.anilbeesetti.nextplayer.feature.player

import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer

/**
 * Controls video aspect ratio and crop mode for the LibVLC render pipeline.
 *
 * LibVLC's two key levers:
 *   • [VlcMediaPlayer.aspectRatio] — force a specific pixel aspect ratio string
 *     ("16:9", "4:3", "1:1", …) or null for source-native
 *   • [VlcMediaPlayer.scale]       — 0f = auto-fit inside surface; 1f = 100 %
 *     pixel crop (fill and crop edges); values > 1 zoom further
 *
 * ── Modes ────────────────────────────────────────────────────────────────────
 *  FIT        BEST_FIT — letterbox / pillarbox, no cropping, source ratio kept
 *  FILL       Scale to fill the surface entirely, cropping edges if needed
 *  RATIO_16_9 Force widescreen (cinema/TV standard)
 *  RATIO_4_3  Force classic TV / older content ratio
 *  RATIO_21_9 Force ultra-wide cinema scope
 *  STRETCH    Ignore aspect ratio entirely — stretch to fill window
 *
 * [applyFromContentScale] bridges the existing [VideoContentScale] enum used by
 * the Media3/ExoPlayer layer so VLC stays in sync without changing any Compose UI.
 */
class VlcAspectController(private val player: VlcMediaPlayer) {

    // ── Aspect mode enum ──────────────────────────────────────────────────────

    enum class AspectMode(
        /** Human-readable label shown in the UI. */
        val label: String,
    ) {
        FIT("Fit"),
        FILL("Fill"),
        RATIO_16_9("16:9"),
        RATIO_4_3("4:3"),
        RATIO_21_9("21:9"),
        STRETCH("Stretch"),
    }

    // ── State ─────────────────────────────────────────────────────────────────

    var currentMode: AspectMode = AspectMode.FIT
        private set

    // ── Application ───────────────────────────────────────────────────────────

    /**
     * Apply [mode] immediately to the running [VlcMediaPlayer].
     * Safe to call at any time — VLC applies the change to the next decoded frame.
     */
    fun applyMode(mode: AspectMode) {
        currentMode = mode
        when (mode) {
            AspectMode.FIT -> {
                // Let VLC compute the aspect from the stream; fit inside surface
                player.aspectRatio = null
                player.scale = 0f
            }
            AspectMode.FILL -> {
                // scale=1f → 100 % zoom so the video fills the surface,
                // cropping equal amounts from the long edges
                player.aspectRatio = null
                player.scale = 1f
            }
            AspectMode.RATIO_16_9 -> {
                player.aspectRatio = "16:9"
                player.scale = 0f
            }
            AspectMode.RATIO_4_3 -> {
                player.aspectRatio = "4:3"
                player.scale = 0f
            }
            AspectMode.RATIO_21_9 -> {
                player.aspectRatio = "21:9"
                player.scale = 0f
            }
            AspectMode.STRETCH -> {
                // Force 1:1 pixel mapping so VLC stretches to fill the surface
                player.aspectRatio = "1:1"
                player.scale = 0f
            }
        }
    }

    /**
     * Cycle through all modes in declaration order.
     * Convenient for a single "cycle aspect ratio" button.
     */
    fun cycleMode() {
        val modes = AspectMode.entries
        val next = (modes.indexOf(currentMode) + 1) % modes.size
        applyMode(modes[next])
    }

    /**
     * Bridge from the existing [VideoContentScale] enum (used by Media3/Compose UI)
     * so VLC mirrors whatever scale the user has already selected.
     *
     * Called from [PlayerActivity.loadVlcMedia] and whenever the user switches
     * scale via the existing controls.
     */
    fun applyFromContentScale(scale: VideoContentScale) = when (scale) {
        VideoContentScale.BEST_FIT       -> applyMode(AspectMode.FIT)
        VideoContentScale.CROP           -> applyMode(AspectMode.FILL)
        VideoContentScale.STRETCH        -> applyMode(AspectMode.STRETCH)
        VideoContentScale.HUNDRED_PERCENT -> applyMode(AspectMode.FILL)
    }
}
