package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that provides a VLC-backed seek function (position in ms).
 * Consumed by SeekGestureState to drive vlcMediaPlayer.setTime() alongside
 * Media3, enabling precise seeking even for formats that Media3 marks as
 * non-seekable (TS streams, non-indexed MKV, etc.).
 */
val LocalSHSSeekTo = compositionLocalOf<((Long) -> Unit)?> { null }
