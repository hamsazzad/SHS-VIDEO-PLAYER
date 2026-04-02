package dev.anilbeesetti.nextplayer.feature.player

import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as SHSMediaPlayer

/**
 * Controls all audio-related behaviour through the LibVLC engine.
 *
 *  ┌─────────────────────────────────────────────────────────┐
 *  │ Feature              │ LibVLC API                        │
 *  ├─────────────────────────────────────────────────────────┤
 *  │ Audio delay          │ SHSMediaPlayer.audioDelay (µs)    │
 *  │ 10-Band Equalizer    │ MediaPlayer.Equalizer + setEq()   │
 *  │ Audio passthrough    │ media option ":spdif"             │
 *  │ Spatializer (3-D)    │ media option ":audio-filter=…"    │
 *  └─────────────────────────────────────────────────────────┘
 *
 * Call [applyToMedia] every time a new [Media] is created so that
 * passthrough and spatializer flags persist across playlist transitions.
 */
class SHSAudioController(private val player: SHSMediaPlayer) {

    // ── State ─────────────────────────────────────────────────────────────────
    var passthroughEnabled: Boolean = false
        private set

    var spatializerEnabled: Boolean = false
        private set

    // ── Audio Delay ───────────────────────────────────────────────────────────

    /**
     * Shift audio timing by [delayMs] milliseconds.
     * Positive = audio plays later (good when sound leads video).
     * Negative = audio plays earlier.
     * Applied immediately to the running player.
     */
    fun setDelay(delayMs: Long) {
        player.audioDelay = delayMs * 1_000L // VLC expects microseconds
    }

    // ── 10-Band Equalizer ─────────────────────────────────────────────────────

    /**
     * Named equalizer presets.
     * Frequency centres: 60 | 170 | 310 | 600 | 1k | 3k | 6k | 12k | 14k | 16k Hz
     */
    enum class EqualizerPreset {
        FLAT,
        BASS_BOOST,
        ROCK,
        POP,
        JAZZ,
        CLASSICAL,
        VOCAL_BOOST,
    }

    /**
     * Apply a named [preset] to the LibVLC equalizer and attach it to the player.
     * The equalizer remains active until [disableEqualizer] is called.
     */
    fun setEqualizer(preset: EqualizerPreset) {
        val eq = SHSMediaPlayer.Equalizer.create()
        val (preAmp, bands) = presetData(preset)
        eq.setPreAmp(preAmp)
        bands.forEachIndexed { index, gain ->
            eq.setAmp(index, gain)
        }
        player.setEqualizer(eq)
    }

    /**
     * Apply custom equalizer values directly.
     * [preAmp] — pre-amplification in dB (range −20 to +20).
     * [bands]  — exactly 10 gain values in dB for bands
     *            60 / 170 / 310 / 600 / 1k / 3k / 6k / 12k / 14k / 16k Hz.
     */
    fun setCustomEqualizer(preAmp: Float, bands: FloatArray) {
        require(bands.size == 10) { "Exactly 10 band values required" }
        val eq = SHSMediaPlayer.Equalizer.create()
        eq.setPreAmp(preAmp)
        bands.forEachIndexed { index, gain ->
            eq.setAmp(index, gain)
        }
        player.setEqualizer(eq)
    }

    /** Detach the equalizer and restore flat audio response. */
    fun disableEqualizer() {
        player.setEqualizer(null)
    }

    // ── Audio Passthrough ─────────────────────────────────────────────────────

    /**
     * Toggle SPDIF/bitstream passthrough for encoded audio formats
     * (EAC3, Dolby Digital, DTS, TrueHD).
     * When enabled the encoded bitstream is sent directly to the receiver
     * without any software decoding, preserving full fidelity.
     * Stored and injected into each new [Media] via [applyToMedia].
     */
    fun setPassthrough(enabled: Boolean) {
        passthroughEnabled = enabled
    }

    // ── 3-D Spatializer ───────────────────────────────────────────────────────

    /**
     * Enable or disable the libVLC spatializer filter for virtual surround
     * sound through stereo headphones.
     * Uses VLC's built-in Freeverb-based room simulation:
     *   room-size 80 % / stereo width 100 % / wet mix 80 %
     * Stored and injected into each new [Media] via [applyToMedia].
     */
    fun setSpatializer(enabled: Boolean) {
        spatializerEnabled = enabled
    }

    // ── Media-option injection ─────────────────────────────────────────────────

    /**
     * Inject stored passthrough and spatializer options into [media].
     * Must be called before [media] is assigned to the player.
     */
    fun applyToMedia(media: Media) {
        if (passthroughEnabled) {
            // Raw bitstream passthrough — receiver handles decoding
            media.addOption(":spdif")
        }

        if (spatializerEnabled) {
            // Chain the spatializer into VLC's audio filter graph
            media.addOption(":audio-filter=spatializer")
            // Freeverb room model parameters
            media.addOption(":spatializer-roomsize=80")   // 0–100, room reverberation size
            media.addOption(":spatializer-width=100")     // stereo field width
            media.addOption(":spatializer-wet=80")        // wet/dry mix (80 = mostly effect)
            media.addOption(":spatializer-dry=20")
            media.addOption(":spatializer-damp=50")       // high-frequency absorption
        }
    }

    // ── Preset data ────────────────────────────────────────────────────────────

    /**
     * Returns (preAmpDb, [10 band gains in dB]) for each [EqualizerPreset].
     * Band order: 60 | 170 | 310 | 600 | 1k | 3k | 6k | 12k | 14k | 16k Hz
     */
    private fun presetData(preset: EqualizerPreset): Pair<Float, FloatArray> = when (preset) {
        EqualizerPreset.FLAT -> 0f to floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        )

        EqualizerPreset.BASS_BOOST -> 4f to floatArrayOf(
            //  60   170   310   600   1k    3k    6k   12k   14k   16k
             8.0f, 7.0f, 5.5f, 3.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        )

        EqualizerPreset.ROCK -> 3f to floatArrayOf(
            //  60   170   310   600   1k    3k    6k   12k   14k   16k
             5.5f, 4.0f, 3.0f, 0.5f,-1.5f, 0.0f, 3.0f, 5.0f, 5.5f, 6.0f,
        )

        EqualizerPreset.POP -> 2f to floatArrayOf(
            //  60   170   310   600   1k    3k    6k   12k   14k   16k
             2.0f, 3.5f, 3.0f, 1.5f, 0.0f,-1.0f, 0.5f, 2.0f, 2.5f, 3.0f,
        )

        EqualizerPreset.JAZZ -> 2f to floatArrayOf(
            //  60   170   310   600   1k    3k    6k   12k   14k   16k
             4.0f, 3.0f, 1.5f, 2.0f,-1.5f,-1.5f, 0.0f, 2.0f, 3.0f, 4.0f,
        )

        EqualizerPreset.CLASSICAL -> 0f to floatArrayOf(
            //  60   170   310   600   1k    3k    6k   12k   14k   16k
             4.5f, 3.5f, 3.0f, 0.0f, 0.0f, 0.0f,-2.0f,-3.0f,-3.5f,-4.0f,
        )

        EqualizerPreset.VOCAL_BOOST -> 2f to floatArrayOf(
            //  60   170   310   600   1k    3k    6k   12k   14k   16k
            -1.5f, 0.0f, 2.5f, 5.0f, 5.5f, 4.0f, 2.0f, 0.0f,-1.0f,-2.0f,
        )
    }
}
