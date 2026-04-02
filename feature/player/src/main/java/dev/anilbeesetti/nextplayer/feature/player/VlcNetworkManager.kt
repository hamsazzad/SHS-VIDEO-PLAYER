package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer

/**
 * Handles network-source media loading for the LibVLC engine.
 *
 * Supported protocols:
 *  ─────────────────────────────────────────────────────────────────────
 *  Protocol      Scheme(s)            Use case
 *  ─────────────────────────────────────────────────────────────────────
 *  HLS (M3U8)   http(s)://GLOB.m3u8  Adaptive live & VOD streams
 *  RTSP          rtsp://              IP cameras, live broadcasts
 *  RTMP          rtmp(s)://           Live streaming platforms
 *  SMB / CIFS    smb://               Windows shares on local WiFi
 *  FTP           ftp://               FTP servers on local network
 *  DLNA / UPnP   http:// (UPnP)       DLNA media servers (Kodi, NAS)
 *  ─────────────────────────────────────────────────────────────────────
 *
 * Usage — call from PlayerActivity or VlcBridge:
 *   val manager = VlcNetworkManager()
 *   val media = manager.createMedia(url, libVLC)
 *   vlcMediaPlayer.media = media
 *   media.release()
 *   vlcMediaPlayer.play()
 */
class VlcNetworkManager {

    /** Configurable network buffer in milliseconds. Default: 3 000 ms. */
    var networkCachingMs: Int = 3_000

    /** Configurable RTSP jitter-buffer in milliseconds. Default: 1 000 ms. */
    var rtspCachingMs: Int = 1_000

    /** Maximum time to wait for an RTMP connection before giving up (ms). */
    var rtmpTimeoutMs: Int = 5_000

    // ── Protocol detection ────────────────────────────────────────────────────

    enum class StreamProtocol {
        HLS,       // HTTP Live Streaming — adaptive bitrate, .m3u8
        RTSP,      // Real-Time Streaming Protocol — cameras, live
        RTMP,      // Real-Time Messaging Protocol — streaming platforms
        SMB,       // Server Message Block — Windows / Samba shares
        FTP,       // File Transfer Protocol — local FTP servers
        DLNA,      // DLNA / UPnP served via HTTP
        HTTP_GENERIC, // Plain HTTP/HTTPS direct file download
    }

    fun detectProtocol(url: String): StreamProtocol {
        val lower = url.lowercase()
        return when {
            lower.startsWith("rtsp://")              -> StreamProtocol.RTSP
            lower.startsWith("rtmp://")  ||
                lower.startsWith("rtmps://") ||
                lower.startsWith("rtmpe://") ||
                lower.startsWith("rtmpt://")          -> StreamProtocol.RTMP
            lower.startsWith("smb://")               -> StreamProtocol.SMB
            lower.startsWith("ftp://")               -> StreamProtocol.FTP
            lower.contains(".m3u8")                  -> StreamProtocol.HLS
            lower.startsWith("http://") ||
                lower.startsWith("https://")          -> StreamProtocol.HTTP_GENERIC
            else                                      -> StreamProtocol.HTTP_GENERIC
        }
    }

    // ── Media creation ────────────────────────────────────────────────────────

    /**
     * Build a [Media] object for [url] with protocol-appropriate buffering options.
     *
     * The caller is responsible for calling [Media.release] after assigning the
     * media to the player (ownership is transferred to [VlcMediaPlayer]).
     */
    fun createMedia(url: String, libVLC: LibVLC): Media {
        val uri = Uri.parse(url)
        val media = Media(libVLC, uri)

        when (detectProtocol(url)) {
            StreamProtocol.HLS -> applyHlsOptions(media)
            StreamProtocol.RTSP -> applyRtspOptions(media)
            StreamProtocol.RTMP -> applyRtmpOptions(media)
            StreamProtocol.SMB -> applySmbOptions(media)
            StreamProtocol.FTP -> applyFtpOptions(media)
            StreamProtocol.DLNA,
            StreamProtocol.HTTP_GENERIC -> applyHttpOptions(media)
        }

        // Always parse the media metadata so VLC knows duration/tracks
        media.parse(Media.Parse.ParseNetwork.toInt())

        return media
    }

    /**
     * Convenience overload — load [url] directly into [player].
     * Applies options, assigns to player, releases wrapper, and starts playback.
     */
    fun loadAndPlay(url: String, player: VlcMediaPlayer, libVLC: LibVLC) {
        val media = createMedia(url, libVLC)
        player.media = media
        media.release()
        player.play()
    }

    // ── Protocol-specific option sets ─────────────────────────────────────────

    /**
     * HLS / M3U8 — adaptive live and VOD streams.
     * Uses a larger network buffer to absorb bitrate-switch latency.
     */
    private fun applyHlsOptions(media: Media) {
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":http-reconnect")          // auto-reconnect on drop
        media.addOption(":adaptive-maxwidth=1920")  // cap at 1080 p
        media.addOption(":adaptive-maxheight=1080")
        media.addOption(":hls-http-reconnect")
        media.addOption(":no-ts-trust-pcr")         // ignore broken PCR clocks
    }

    /**
     * RTSP — IP cameras, live broadcasts.
     * Uses TCP transport to avoid UDP packet loss.
     */
    private fun applyRtspOptions(media: Media) {
        media.addOption(":rtsp-tcp")                // prefer TCP over UDP
        media.addOption(":rtsp-caching=$rtspCachingMs")
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":clock-synchro=0")         // disable strict A/V sync for live
        media.addOption(":no-rtsp-reuse")
        media.addOption(":rtsp-frame-buffer-size=500000")
    }

    /**
     * RTMP — Real-Time Messaging Protocol (live streaming platforms).
     * Longer timeout to handle platform handshake latency.
     */
    private fun applyRtmpOptions(media: Media) {
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":rtmp-timeout=$rtmpTimeoutMs")
        media.addOption(":live-caching=$networkCachingMs")
    }

    /**
     * SMB — Windows / Samba network shares over local WiFi.
     * VLC uses its own SMB1/SMB2 client — no extra Android permissions needed.
     */
    private fun applySmbOptions(media: Media) {
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":smb-domain=WORKGROUP")
        media.addOption(":file-caching=1000")
    }

    /**
     * FTP — local network FTP servers.
     */
    private fun applyFtpOptions(media: Media) {
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":ftp-passive")             // passive mode for NAT/firewall compatibility
    }

    /**
     * Plain HTTP / HTTPS (including DLNA/UPnP content served over HTTP).
     */
    private fun applyHttpOptions(media: Media) {
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":http-reconnect")
        media.addOption(":http-forward-cookies")
    }
}
