package dev.anilbeesetti.nextplayer.feature.player.buttons

import android.content.Context

// ─── Persistence ─────────────────────────────────────────────────────────────
//
// Persists the user's preferred button order and visibility in SharedPreferences.
// The default order mirrors the original ControlsTopView arrangement so first-time
// users see no change until they explicitly enter Edit Mode.

object PlayerButtonLayout {

    private const val PREFS          = "player_button_layout"
    private const val KEY_TOP_ORDER  = "top_bar_order"
    private const val KEY_HIDDEN     = "hidden_buttons"
    private const val SEP            = ","

    /** Default visible top-bar button order — matches original ControlsTopView. */
    val DEFAULT_TOP: List<PlayerButtonId> = listOf(
        PlayerButtonId.PLAYLIST,
        PlayerButtonId.SCREENSHOT,
        PlayerButtonId.SPEED,
        PlayerButtonId.AUDIO,
        PlayerButtonId.SUBTITLE,
        PlayerButtonId.SUBTITLE_SEARCH,
        PlayerButtonId.SUBTITLE_EDITOR,
        PlayerButtonId.AUDIO_EDITOR,
        PlayerButtonId.MENU,
    )

    // ── Order ─────────────────────────────────────────────────────────────────

    /** Returns the user-customised top-bar button order, or [DEFAULT_TOP]
     *  when the user has never modified the layout. */
    fun getTopBarOrder(ctx: Context): List<PlayerButtonId> {
        val raw = prefs(ctx).getString(KEY_TOP_ORDER, null) ?: return DEFAULT_TOP
        return raw.split(SEP)
            .mapNotNull { name -> runCatching { PlayerButtonId.valueOf(name) }.getOrNull() }
            .takeIf { it.isNotEmpty() } ?: DEFAULT_TOP
    }

    /** Persists a new button order.
     *  The list should contain only TOP-bar button IDs. */
    fun saveTopBarOrder(ctx: Context, ids: List<PlayerButtonId>) =
        prefs(ctx).edit()
            .putString(KEY_TOP_ORDER, ids.joinToString(SEP) { it.name })
            .apply()

    // ── Visibility ────────────────────────────────────────────────────────────

    /** Returns the set of buttons that the user has chosen to hide. */
    fun getHiddenButtons(ctx: Context): Set<PlayerButtonId> =
        (prefs(ctx).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())
            .mapNotNull { name -> runCatching { PlayerButtonId.valueOf(name) }.getOrNull() }
            .toSet()

    /** Shows or hides [id] by updating the hidden-buttons set. */
    fun setButtonVisible(ctx: Context, id: PlayerButtonId, visible: Boolean) {
        val prefs = prefs(ctx)
        val current = (prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()).toMutableSet()
        if (visible) current.remove(id.name) else current.add(id.name)
        prefs.edit().putStringSet(KEY_HIDDEN, current).apply()
    }

    /** Resets both order and visibility to factory defaults. */
    fun reset(ctx: Context) =
        prefs(ctx).edit()
            .remove(KEY_TOP_ORDER)
            .remove(KEY_HIDDEN)
            .apply()

    // ── Internal ──────────────────────────────────────────────────────────────
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
