package dev.anilbeesetti.nextplayer.feature.player.state

import dev.anilbeesetti.nextplayer.feature.player.LocalVlcSeekTo

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
@Composable
fun rememberSeekGestureState(
    player: Player,
    sensitivity: Float = 0.5f,
    enableSeekGesture: Boolean,
): SeekGestureState {
    // Consume the VLC seek bridge provided by PlayerActivity so that
    // SeekGestureState can drive vlcMediaPlayer.setTime() alongside Media3.
    val vlcSeekTo = LocalVlcSeekTo.current
    val seekGestureState = remember {
        SeekGestureState(
            player = player,
            sensitivity = sensitivity,
            enableSeekGesture = enableSeekGesture,
            vlcSeekTo = vlcSeekTo,
        )
    }
    return seekGestureState
}

@Stable
class SeekGestureState(
    private val player: Player,
    private val enableSeekGesture: Boolean = true,
    private val sensitivity: Float = 0.5f,
    /**
     * Optional VLC seek function from [LocalVlcSeekTo].
     * When present, seeking bypasses Media3's [Player.isCurrentMediaItemSeekable]
     * restriction, enabling frame-accurate scrubbing for TS, non-indexed MKV, etc.
     */
    private val vlcSeekTo: ((Long) -> Unit)? = null,
) {
    var isSeeking: Boolean by mutableStateOf(false)
        private set

    var seekStartPosition: Long? by mutableStateOf(null)
        private set

    var seekAmount: Long? by mutableStateOf(null)
        private set

    private var seekStartX = 0f

    fun onSeek(value: Long) {
        if (!isSeeking) {
            isSeeking = true
            seekStartPosition = player.currentPosition
            player.setIsScrubbingModeEnabled(true)
        }

        val safeDuration = if (player.duration >= 0) player.duration else Long.MAX_VALUE
        seekAmount = (value - seekStartPosition!!).coerceIn(
            minimumValue = 0 - seekStartPosition!!,
            maximumValue = safeDuration - seekStartPosition!!,
        )

        val seekTarget = value.coerceIn(0L, safeDuration)
        // Drive VLC's timeline directly — setTime() works even for formats
        // that ExoPlayer cannot seek in (TS streams, non-indexed MKV, etc.)
        vlcSeekTo?.invoke(seekTarget)

        if (value > player.currentPosition) {
            player.seekTo(value.coerceAtMost(if (player.duration >= 0) player.duration else 0L))
        } else {
            player.seekTo(value.coerceAtLeast(0L))
        }
    }

    fun onSeekEnd() {
        reset()
    }

    fun onDragStart(offset: Offset) {
        if (!enableSeekGesture) return
        if (player.currentPosition == C.TIME_UNSET) return
        if (player.duration == C.TIME_UNSET) return
        // When VLC engine is wired, allow dragging even on formats that
        // Media3 reports as non-seekable (e.g. MPEG-TS, some MKV/AVI files)
        if (vlcSeekTo == null && !player.isCurrentMediaItemSeekable) return

        isSeeking = true
        seekStartX = offset.x
        seekStartPosition = player.currentPosition

        player.setIsScrubbingModeEnabled(true)
    }

    @OptIn(UnstableApi::class)
    fun onDrag(change: PointerInputChange, dragAmount: Float) {
        if (seekStartPosition == null) return
        if (player.duration == C.TIME_UNSET) return
        // VLC can seek even when Media3 cannot — skip the restriction if VLC is wired
        if (vlcSeekTo == null && !player.isCurrentMediaItemSeekable) return
        if (player.currentPosition <= 0L && dragAmount < 0) return
        if (player.currentPosition >= player.duration && dragAmount > 0) return
        if (change.isConsumed) return

        val newPosition = seekStartPosition!! + ((change.position.x - seekStartX) * (sensitivity * 100)).toInt()
        val safeDuration = if (player.duration >= 0) player.duration else Long.MAX_VALUE
        seekAmount = (newPosition - seekStartPosition!!).coerceIn(
            minimumValue = 0 - seekStartPosition!!,
            maximumValue = safeDuration - seekStartPosition!!,
        )

        val seekTarget = newPosition.coerceIn(0L, safeDuration)
        // Use vlcMediaPlayer.setTime() for frame-accurate seeking via VLC's
        // own demuxer — this is the core of the "unseekable video" fix
        vlcSeekTo?.invoke(seekTarget)
        player.seekTo(newPosition.coerceIn(0L, player.duration))
    }

    fun onDragEnd() {
        reset()
    }

    private fun reset() {
        player.setIsScrubbingModeEnabled(false)
        isSeeking = false
        seekStartPosition = null
        seekAmount = null

        seekStartX = 0f
    }
}

val SeekGestureState.seekAmountFormatted: String
    get() {
        val seekAmount = seekAmount ?: return ""
        val sign = if (seekAmount < 0) "-" else "+"
        return sign + abs(seekAmount).milliseconds.formatted()
    }

val SeekGestureState.seekToPositionFormated: String
    get() {
        val position = seekStartPosition ?: return ""
        val seekAmount = seekAmount ?: return ""
        return (position + seekAmount).milliseconds.formatted()
    }
