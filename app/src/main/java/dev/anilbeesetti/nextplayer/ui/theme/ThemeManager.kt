package dev.anilbeesetti.nextplayer.ui.theme

import android.content.Context
import android.net.Uri

// ─── Data ─────────────────────────────────────────────────────────────────────

data class PredefinedTheme(
    val name: String,
    val hexColor: String,
)

// ─── Manager ──────────────────────────────────────────────────────────────────

object ThemeManager {

    private const val PREFS           = "shs_theme_prefs"
    private const val KEY_ACCENT_HEX  = "accent_hex"
    private const val KEY_BG_URI      = "bg_image_uri"
    private const val KEY_BG_DIM      = "bg_dim_alpha"

    // Eight curated seed colors covering popular design families.
    // Each color is a full Material-3 seed that generates a coherent
    // tonal palette when passed to colorSchemeFromSeed().
    val predefinedThemes: List<PredefinedTheme> = listOf(
        PredefinedTheme("Default",    "#6750A4"),   // Material Purple
        PredefinedTheme("Ocean",      "#1565C0"),   // Deep Blue
        PredefinedTheme("Forest",     "#2E7D32"),   // Dark Green
        PredefinedTheme("Sunset",     "#B71C1C"),   // Deep Red
        PredefinedTheme("Amber",      "#E65100"),   // Burnt Orange
        PredefinedTheme("Teal",       "#00695C"),   // Dark Teal
        PredefinedTheme("Rose",       "#AD1457"),   // Hot Pink
        PredefinedTheme("Slate",      "#37474F"),   // Blue Grey
    )

    // ── Accent color ──────────────────────────────────────────────────────────

    /** Returns the stored hex color string (e.g. "#6750A4") or null for the
     *  default Material-3 purple shipped with the app. */
    fun getAccentHex(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACCENT_HEX, null)

    /** Persists [hex] as the app-wide accent seed color.
     *  Pass null to revert to the default theme. */
    fun setAccentHex(ctx: Context, hex: String?) =
        prefs(ctx).edit().putString(KEY_ACCENT_HEX, hex).apply()

    /** Parse [hex] to a Color Int (0xFFrrggbb) or null if unparseable. */
    fun parseHex(hex: String): Int? {
        val clean = hex.trimStart('#')
        if (clean.length != 6) return null
        return runCatching { clean.toLong(16).toInt() or 0xFF000000.toInt() }.getOrNull()
    }

    // ── Background image ──────────────────────────────────────────────────────

    /** Returns the Uri pointing to the user-chosen background image,
     *  or null when no custom background has been set. */
    fun getBackgroundImageUri(ctx: Context): Uri? {
        val raw = prefs(ctx).getString(KEY_BG_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** Persists [uri] as the global background image.
     *  Pass null to clear the background image. */
    fun setBackgroundImageUri(ctx: Context, uri: Uri?) =
        prefs(ctx).edit().putString(KEY_BG_URI, uri?.toString()).apply()

    // ── Background dim ────────────────────────────────────────────────────────

    /** Returns the overlay dim opacity (0.0 = transparent, 1.0 = opaque black).
     *  Default 0.55 keeps text readable over most background images. */
    fun getBgDimAlpha(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_BG_DIM, 0.55f)

    fun setBgDimAlpha(ctx: Context, alpha: Float) =
        prefs(ctx).edit().putFloat(KEY_BG_DIM, alpha.coerceIn(0f, 0.9f)).apply()

    // ── Internal ──────────────────────────────────────────────────────────────
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
