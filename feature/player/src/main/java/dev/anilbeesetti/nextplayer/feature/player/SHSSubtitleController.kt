package dev.anilbeesetti.nextplayer.feature.player

import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as SHSMediaPlayer

/**
 * Controls all subtitle-related behaviour through the LibVLC engine.
 *
 * Usage:
 *   controller.setDelay(500)          // subtitles 500 ms later
 *   controller.setFontSize(22)        // relative font size
 *   controller.setColor("#FFFF00")    // yellow text
 *   controller.setShadow(true, 180)   // shadow with 180/255 opacity
 *
 * Call [applyToMedia] every time a new [Media] object is created so that
 * font, color, and shadow settings survive playlist transitions.
 * [setDelay] applies instantly to the running player via [SHSMediaPlayer.spuDelay].
 */
class SHSSubtitleController(private val player: SHSMediaPlayer) {

    // ── Stored state — persisted across media reloads ─────────────────────────
    /** Relative font size passed to the FreeType text renderer (default 16). */
    var fontSize: Int = 16
        private set

    /** Subtitle text colour as a 0xRRGGBB integer (default white). */
    var textColor: Int = 0xFFFFFF
        private set

    /** Whether to render a drop shadow behind each subtitle glyph. */
    var shadowEnabled: Boolean = false
        private set

    /** Shadow opacity 0–255 (default 128, semi-transparent). */
    var shadowOpacity: Int = 128
        private set

    // ── Runtime controls ──────────────────────────────────────────────────────

    /**
     * Shift subtitle timing by [delayMs] milliseconds.
     * Positive = subtitles appear later (good when audio leads the subs).
     * Negative = subtitles appear earlier.
     * Applied immediately via [SHSMediaPlayer.spuDelay] (microseconds internally).
     */
    fun setDelay(delayMs: Long) {
        player.setSpuDelay(delayMs * 1_000L) // VLC expects microseconds
    }

    /**
     * Change the FreeType relative font size.
     * Typical range: 12 (large) to 24 (small). Default 16 = "normal".
     * Stored and re-applied on the next [applyToMedia] call.
     */
    fun setFontSize(sp: Int) {
        fontSize = sp.coerceIn(6, 36)
    }

    /**
     * Set subtitle text colour from a CSS-style hex string, e.g. "#FF4400" or "FF4400".
     * Stored and re-applied on the next [applyToMedia] call.
     */
    fun setColor(hex: String) {
        val clean = hex.trimStart('#')
        textColor = clean.toLongOrNull(16)?.toInt() ?: 0xFFFFFF
    }

    /**
     * Enable or disable the drop shadow behind subtitle glyphs.
     * [opacity] is 0 (transparent) – 255 (opaque).
     * Stored and re-applied on the next [applyToMedia] call.
     */
    fun setShadow(enabled: Boolean, opacity: Int = 128) {
        shadowEnabled = enabled
        shadowOpacity = opacity.coerceIn(0, 255)
    }

    // ── Media-option injection ─────────────────────────────────────────────────

    /**
     * Apply all stored subtitle-styling settings to [media] as VLC module options.
     * Must be called before [media] is assigned to the player.
     *
     * FreeType options are per-media so they survive playlist transitions without
     * requiring a full LibVLC re-initialisation.
     */
    fun applyToMedia(media: Media) {
        // Font size — smaller number = larger glyphs in VLC's relative scale
        media.addOption(":freetype-rel-fontsize=$fontSize")

        // Text colour in 0xRRGGBB form (VLC ignores the alpha channel here)
        media.addOption(":freetype-color=${textColor.toUnsignedHex()}")

        // Shadow — VLC renders a darkened copy offset by a few pixels
        if (shadowEnabled) {
            media.addOption(":freetype-shadow-opacity=$shadowOpacity")
        } else {
            media.addOption(":freetype-shadow-opacity=0")
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private fun Int.toUnsignedHex(): String = Integer.toHexString(this and 0xFFFFFF)
}
