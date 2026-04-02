package dev.anilbeesetti.nextplayer.feature.player.buttons

// ─── Button identity ──────────────────────────────────────────────────────────
//
// Each enum value maps to exactly one button in the player control bar.
// TOP_BAR buttons appear in ControlsTopView (row at the top of the video).
// BOTTOM_BAR buttons appear in ControlsBottomView (row at the bottom).
//
// New buttons should be added here and wired into PlayerButtonLayout.DEFAULT_*.

enum class PlayerButtonId(
    val label: String,
    val bar: PlayerButtonBar,
) {
    // ── Top bar ───────────────────────────────────────────────────────────────
    PLAYLIST("Playlist",   PlayerButtonBar.TOP),
    SCREENSHOT("Screenshot", PlayerButtonBar.TOP),
    SPEED("Speed",         PlayerButtonBar.TOP),
    AUDIO("Audio Track",   PlayerButtonBar.TOP),
    SUBTITLE("Subtitles",  PlayerButtonBar.TOP),
    SUBTITLE_SEARCH("Sub Search", PlayerButtonBar.TOP),
    AUDIO_EDITOR("Audio EQ", PlayerButtonBar.TOP),
    SUBTITLE_EDITOR("Sub Edit", PlayerButtonBar.TOP),
    MENU("More",           PlayerButtonBar.TOP),

    // ── Bottom bar ────────────────────────────────────────────────────────────
    SCALE("Scale",         PlayerButtonBar.BOTTOM),
    LOCK("Lock",           PlayerButtonBar.BOTTOM),
    PIP("PiP",             PlayerButtonBar.BOTTOM),
    ROTATE("Rotate",       PlayerButtonBar.BOTTOM),
    BACKGROUND("Background", PlayerButtonBar.BOTTOM),
}

enum class PlayerButtonBar { TOP, BOTTOM }
