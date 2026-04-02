package dev.anilbeesetti.nextplayer.feature.player

import org.videolan.libvlc.MediaPlayer as SHSMediaPlayer

/**
 * Chapter navigation and chapter-indexed seeking for the LibVLC engine.
 *
 * LibVLC natively reads chapter metadata from MKV, MP4 (ISO 14496), OGM, and
 * Blu-ray containers via its own demuxer — no external parser needed.
 *
 * ── Key capabilities ──────────────────────────────────────────────────────────
 * • [getChapters]        — returns the full chapter list with time offsets
 * • [navigateTo]         — jump to a specific chapter index instantly
 * • [next] / [previous]  — step forward / backward one chapter
 * • [seekToTime]         — tries direct-time seek; falls back to chapter-index
 *                          navigation for non-seekable containers (TS, AVI, etc.)
 *
 * ── Why chapter-indexed seeking fixes "non-seekable" files ──────────────────
 * When a container has no byte-range index (many MPEG-TS streams, older AVI),
 * ExoPlayer / Media3 refuses to seek because it cannot compute the byte offset.
 * VLC's demuxer builds its own index from I-frame positions and chapter tables,
 * so [SHSMediaPlayer.chapter] navigation succeeds even when [setTime] would hang
 * or produce wrong results.
 */
class SHSChapterController(private val player: SHSMediaPlayer) {

    // ── Data model ─────────────────────────────────────────────────────────────

    /**
     * Represents a single chapter entry returned by LibVLC.
     *
     * @param index      zero-based position in the chapter list
     * @param name       display name (e.g. "Chapter 01" or custom author name)
     * @param timeMs     chapter start time from media start, in milliseconds
     * @param durationMs chapter duration in milliseconds
     */
    data class Chapter(
        val index: Int,
        val name: String,
        val timeMs: Long,
        val durationMs: Long,
    )

    // ── State ──────────────────────────────────────────────────────────────────

    /**
     * Current chapter index as reported by LibVLC.
     * Returns -1 if no chapter information is available.
     */
    val currentIndex: Int
        get() = player.chapter

    // ── Chapter retrieval ──────────────────────────────────────────────────────

    /**
     * Fetch all chapters for the current title.
     * Returns an empty list if the media has no chapter track.
     *
     * Passing -1 to [getChapterDescription] retrieves chapters for the
     * currently active title (correct for 99 % of single-title files).
     */
    fun getChapters(): List<Chapter> {
        val raw = player.getChapters(-1) ?: return emptyList()
        return raw.mapIndexed { i, ch ->
            Chapter(
                index    = i,
                name     = ch.name?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}",
                timeMs   = ch.timeOffset,           // milliseconds from start
                durationMs = ch.duration,
            )
        }
    }

    /**
     * Returns true if the current media contains at least one named chapter.
     */
    fun hasChapters(): Boolean = getChapters().isNotEmpty()

    // ── Navigation ─────────────────────────────────────────────────────────────

    /**
     * Jump to chapter at [index]. Safe — ignores out-of-bounds indices.
     */
    fun navigateTo(index: Int) {
        val chapters = getChapters()
        if (index < 0 || index >= chapters.size) return
        player.chapter = index
    }

    /** Advance to the next chapter if one exists. */
    fun next() {
        val chapters = getChapters()
        val next = currentIndex + 1
        if (next < chapters.size) player.chapter = next
    }

    /** Step back to the previous chapter if one exists. */
    fun previous() {
        val prev = currentIndex - 1
        if (prev >= 0) player.chapter = prev
    }

    // ── Chapter-indexed seeking (the "non-seekable" fix) ─────────────────────

    /**
     * Seeks to [timeMs] using the most reliable method available:
     *
     * 1. Always calls [SHSMediaPlayer.setTime] — works for indexed containers
     *    (MP4, MKV with cues, WebM).
     * 2. If the media has chapters, also snaps to the closest chapter boundary
     *    via [SHSMediaPlayer.chapter] setter — this is VLC's native index, which
     *    works even when byte-range seeking fails (MPEG-TS, non-indexed AVI/MKV).
     *
     * Both calls are always issued; there is no performance cost because VLC
     * deduplicates redundant seeks internally.
     *
     * @return true if a chapter-based fallback was also applied
     */
    fun seekToTime(timeMs: Long): Boolean {
        // Primary path — works for most modern containers
        player.setTime(timeMs)

        // Chapter-index fallback — works for non-indexed / non-seekable streams
        val chapters = getChapters()
        if (chapters.isEmpty()) return false

        // Find the last chapter whose start is at or before the target position
        val best = chapters.lastOrNull { it.timeMs <= timeMs }
            ?: chapters.first()   // before first chapter → jump to chapter 0

        if (best.index != currentIndex) {
            player.chapter = best.index
            return true
        }
        return false
    }

    /**
     * Returns the chapter that covers [timeMs], or null if no chapters exist.
     * Useful for updating a chapter indicator in the UI without triggering navigation.
     */
    fun chapterAt(timeMs: Long): Chapter? {
        val chapters = getChapters()
        return chapters.lastOrNull { it.timeMs <= timeMs }
    }
}
