package com.example.musicsmd.desktop

import com.example.musicsmd.player.AppViewModel
import com.example.musicsmd.settings.SettingsStore
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Window
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media keys, and on Windows Hymn's place in the system media controls (the flyout under the volume
 * and quick settings, and the lock screen), all following the "media keys" setting.
 *
 * On Windows the media controls also bring the keyboard's media keys, routed by Windows to the
 * player it shows; elsewhere, or if Windows has no media controls to give, the keys are heard
 * through JNativeHook instead. Never both, or each key press would act twice.
 */
class MediaKeyController(
    private val settingsStore: SettingsStore,
    private val viewModel: AppViewModel,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val windowHandle = CompletableDeferred<Pointer?>()

    // WinRT objects are used from this one thread.
    private val winrtThread = Executors.newSingleThreadExecutor { task -> Thread(task, "media-controls").apply { isDaemon = true } }
    private val winrt = winrtThread.asCoroutineDispatcher()

    @Volatile private var session: WindowsMediaSession? = null
    @Volatile private var hookRegistered = false
    private var loggedFailure = false

    private val listener = object : NativeKeyListener {
        override fun nativeKeyPressed(event: NativeKeyEvent) {
            when (event.keyCode) {
                NativeKeyEvent.VC_MEDIA_PLAY -> viewModel.togglePlayPause()
                NativeKeyEvent.VC_MEDIA_NEXT -> viewModel.playNextInQueue()
                NativeKeyEvent.VC_MEDIA_PREVIOUS -> viewModel.playPreviousInQueue()
                NativeKeyEvent.VC_MEDIA_STOP -> viewModel.pause()
            }
        }
    }

    fun start() {
        silenceLogger()
        scope.launch {
            val session = if (isWindows) openSession() else null
            if (session != null) mirrorPlayback(session) else followSettingWithHook()
        }
    }

    /** Windows ties the media controls to a window: Hymn's main one, once it is on screen. */
    fun attachWindow(window: Window) {
        windowHandle.complete(runCatching { Native.getWindowPointer(window) }.getOrNull())
    }

    fun dispose() {
        unregisterHook()
        scope.cancel()
        session?.let { open -> winrtThread.submit { runCatching { open.close() } } }
        winrtThread.shutdown()
        runCatching { winrtThread.awaitTermination(1, TimeUnit.SECONDS) }
    }

    private suspend fun openSession(): WindowsMediaSession? {
        val hwnd = windowHandle.await() ?: return null
        return withContext(winrt) {
            runCatching {
                WindowsMediaSession.initThread()
                WindowsMediaSession.create(hwnd, onButton = ::onButton, onSeek = viewModel::seekTo)
            }.onFailure { error ->
                System.err.println("Windows media controls unavailable, using media key hook: ${error.message}")
            }.getOrNull()
        }?.also { session = it }
    }

    private suspend fun mirrorPlayback(session: WindowsMediaSession) {
        settingsStore.settings.map { it.mediaKeys }.distinctUntilChanged().collectLatest { enabled ->
            if (!enabled) {
                withContext(winrt) { session.setEnabled(false) }
                return@collectLatest
            }
            coroutineScope {
                launch {
                    viewModel.playback.map { it.currentSong }.distinctUntilChanged().collectLatest { song ->
                        if (song == null) {
                            withContext(winrt) { session.setEnabled(false) }
                            return@collectLatest
                        }
                        val artwork = song.artworkUrl?.takeIf { it.isNotBlank() }?.let { artworkFor(it) }
                        withContext(winrt) {
                            session.setTrack(song.title, song.artist, song.album, artwork)
                            session.setEnabled(true)
                        }
                    }
                }
                launch {
                    viewModel.playback.map { it.isPlaying to it.durationMs }.distinctUntilChanged().collectLatest { (playing, durationMs) ->
                        withContext(winrt) { session.setPlaying(playing) }
                        // Windows moves the progress bar on by itself while playing; this catches
                        // seeks and drift.
                        while (true) {
                            withContext(winrt) { session.setTimeline(viewModel.position.value, durationMs) }
                            delay(TIMELINE_REFRESH_MS)
                        }
                    }
                }
            }
        }
    }

    /** Web covers Windows fetches itself; a local file's cover is handed over as bytes. */
    private suspend fun artworkFor(url: String): WindowsMediaSession.Artwork? =
        if (url.startsWith("http://") || url.startsWith("https://")) {
            WindowsMediaSession.Artwork.Link(url)
        } else {
            withContext(Dispatchers.IO) {
                runCatching { URI(url).toURL().openStream().use { it.readNBytes(MAX_ARTWORK_BYTES + 1) } }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() && it.size <= MAX_ARTWORK_BYTES }
                    ?.let { WindowsMediaSession.Artwork.Image(it) }
            }
        }

    private fun onButton(button: WindowsMediaSession.Button) {
        val playing = viewModel.playback.value.isPlaying
        when (button) {
            WindowsMediaSession.Button.PLAY -> if (!playing) viewModel.togglePlayPause()
            WindowsMediaSession.Button.PAUSE, WindowsMediaSession.Button.STOP -> if (playing) viewModel.pause()
            WindowsMediaSession.Button.NEXT -> viewModel.playNextInQueue()
            WindowsMediaSession.Button.PREVIOUS -> viewModel.playPreviousInQueue()
        }
    }

    private suspend fun followSettingWithHook() {
        settingsStore.settings.map { it.mediaKeys }.distinctUntilChanged().collect { enabled ->
            if (enabled) registerHook() else unregisterHook()
        }
    }

    private fun registerHook() {
        if (hookRegistered) return
        runCatching {
            if (!GlobalScreen.isNativeHookRegistered()) GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
            hookRegistered = true
        }.onFailure { error ->
            if (!loggedFailure) {
                loggedFailure = true
                System.err.println("Media keys unavailable: ${error.message}")
            }
        }
    }

    private fun unregisterHook() {
        if (!hookRegistered) return
        runCatching { GlobalScreen.removeNativeKeyListener(listener) }
        runCatching { if (GlobalScreen.isNativeHookRegistered()) GlobalScreen.unregisterNativeHook() }
        hookRegistered = false
    }

    private fun silenceLogger() {
        val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
        logger.level = Level.OFF
        logger.useParentHandlers = false
    }

    private companion object {
        val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
        const val TIMELINE_REFRESH_MS = 5_000L
        const val MAX_ARTWORK_BYTES = 4 * 1024 * 1024
    }
}
