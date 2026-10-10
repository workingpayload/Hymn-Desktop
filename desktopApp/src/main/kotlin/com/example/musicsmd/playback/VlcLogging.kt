package com.example.musicsmd.playback

import com.example.musicsmd.motionart.MotionArtworkPlayer

/**
 * libVLC prints its own errors straight to the console, including routine ones such as HTTP/2
 * "Stream closed" when a stream is cancelled on a skip, seek or animated-artwork loop. The app
 * reports real playback failures itself, so libVLC is kept quiet unless `MUSICSM_VLC_DEBUG=1`.
 * Shared by [PlayerController] and [MotionArtworkPlayer].
 */
internal object VlcLogging {
    fun args(): Array<String> =
        if (System.getenv("MUSICSM_VLC_DEBUG") == "1") arrayOf("--verbose=2") else arrayOf("--quiet")
}
