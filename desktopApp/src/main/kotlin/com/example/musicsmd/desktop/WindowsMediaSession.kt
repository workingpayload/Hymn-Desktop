package com.example.musicsmd.desktop

import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * Hymn's entry in Windows' media controls: the media flyout under the volume and quick settings,
 * the lock screen, and the keyboard's media keys, which Windows hands to the session it shows.
 *
 * This is WinRT's SystemMediaTransportControls, bound to Hymn's window and driven through its COM
 * ABI with JNA, since the JVM has no WinRT projection. Vtable slots follow the interfaces' IDL
 * (`windows.media.idl`); every interface starts with IInspectable's six methods.
 *
 * Not thread-safe: call [initThread], [create] and the setters on one thread. Button presses and
 * seeks from Windows arrive on Windows' own threads.
 */
internal class WindowsMediaSession private constructor(
    private val controls: Pointer,
    private val updater: Pointer,
    private val controls2: Pointer?,
    private val timeline: Pointer?,
    private val streamReferences: Pointer?,
    private val uris: Pointer?,
    // Windows calls these for as long as the session lives, so they are held here.
    @Suppress("unused") private val handlers: List<ComDelegate>,
    private val buttonToken: Long,
) {
    enum class Button { PLAY, PAUSE, STOP, NEXT, PREVIOUS }

    /** The cover Windows shows: a web image it fetches itself, or the bytes of a local one. */
    sealed interface Artwork {
        data class Link(val url: String) : Artwork
        class Image(val bytes: ByteArray) : Artwork
    }

    /** Shows or hides Hymn in the media controls. */
    fun setEnabled(enabled: Boolean) {
        call(controls, CONTROLS_PUT_IS_ENABLED, flag(enabled))
    }

    fun setPlaying(playing: Boolean) {
        call(controls, CONTROLS_PUT_PLAYBACK_STATUS, if (playing) STATUS_PLAYING else STATUS_PAUSED)
    }

    fun setTrack(title: String, artist: String, album: String?, artwork: Artwork?) {
        call(updater, UPDATER_CLEAR_ALL)
        call(updater, UPDATER_PUT_TYPE, TYPE_MUSIC)
        getPointer(updater, UPDATER_GET_MUSIC_PROPERTIES)?.let { music ->
            try {
                putString(music, MUSIC_PUT_TITLE, title)
                putString(music, MUSIC_PUT_ARTIST, artist)
                if (!album.isNullOrBlank()) {
                    queryInterface(music, IID_MUSIC_PROPERTIES_2)?.let { music2 ->
                        try {
                            putString(music2, MUSIC2_PUT_ALBUM_TITLE, album)
                        } finally {
                            release(music2)
                        }
                    }
                }
            } finally {
                release(music)
            }
        }
        artwork?.let(::streamReference)?.let { reference ->
            try {
                call(updater, UPDATER_PUT_THUMBNAIL, reference)
            } finally {
                release(reference)
            }
        }
        call(updater, UPDATER_UPDATE)
    }

    /** Where playback is, for the flyout's progress bar. Windows advances it while playing. */
    fun setTimeline(positionMs: Long, durationMs: Long) {
        val controls2 = controls2 ?: return
        val timeline = timeline ?: return
        if (durationMs <= 0L) return
        val end = durationMs * TICKS_PER_MS
        call(timeline, TIMELINE_PUT_START_TIME, 0L)
        call(timeline, TIMELINE_PUT_END_TIME, end)
        call(timeline, TIMELINE_PUT_MIN_SEEK_TIME, 0L)
        call(timeline, TIMELINE_PUT_MAX_SEEK_TIME, end)
        call(timeline, TIMELINE_PUT_POSITION, positionMs.coerceIn(0L, durationMs) * TICKS_PER_MS)
        call(controls2, CONTROLS2_UPDATE_TIMELINE, timeline)
    }

    fun close() {
        setEnabled(false)
        call(controls, CONTROLS_REMOVE_BUTTON_PRESSED, buttonToken)
        listOfNotNull(timeline, controls2, streamReferences, uris, updater, controls).forEach(::release)
    }

    private fun streamReference(artwork: Artwork): Pointer? = when (artwork) {
        is Artwork.Link -> linkReference(artwork.url)
        is Artwork.Image -> imageReference(artwork.bytes)
    }

    private fun linkReference(url: String): Pointer? {
        val uris = uris ?: return null
        val streamReferences = streamReferences ?: return null
        val uri = PointerByReference()
        if (withHString(url) { call(uris, URI_FACTORY_CREATE_URI, it, uri) } < 0) return null
        return try {
            PointerByReference().takeIf { call(streamReferences, STREAM_REFERENCE_CREATE_FROM_URI, uri.value, it) >= 0 }?.value
        } finally {
            release(uri.value)
        }
    }

    private fun imageReference(bytes: ByteArray): Pointer? {
        val streamReferences = streamReferences ?: return null
        val stream = shlwapi.SHCreateMemStream(bytes, bytes.size) ?: return null
        return try {
            val randomAccess = PointerByReference()
            if (shcore.CreateRandomAccessStreamOverStream(stream, BSOS_DEFAULT, IID_RANDOM_ACCESS_STREAM, randomAccess) < 0) return null
            try {
                PointerByReference().takeIf { call(streamReferences, STREAM_REFERENCE_CREATE_FROM_STREAM, randomAccess.value, it) >= 0 }?.value
            } finally {
                release(randomAccess.value)
            }
        } finally {
            release(stream)
        }
    }

    companion object {
        /** Joins the calling thread to WinRT's multithreaded apartment. */
        fun initThread() {
            combase.RoInitialize(RO_INIT_MULTITHREADED)
        }

        /**
         * The media controls for the window [hwnd], with every button Hymn uses switched on.
         * Throws if Windows has none to give (older Windows, or WinRT unavailable).
         */
        fun create(hwnd: Pointer, onButton: (Button) -> Unit, onSeek: (Long) -> Unit): WindowsMediaSession {
            val interop = activationFactory(SMTC_CLASS, IID_SMTC_INTEROP) ?: error("No media controls on this Windows")
            val controls = try {
                PointerByReference().also { check(call(interop, INTEROP_GET_FOR_WINDOW, hwnd, IID_SMTC, it), "GetForWindow") }.value
            } finally {
                release(interop)
            }
            val updater = getPointer(controls, CONTROLS_GET_DISPLAY_UPDATER) ?: error("No display updater")

            val buttons = ComDelegate(IID_BUTTON_PRESSED_HANDLER) { args ->
                val pressed = IntByReference()
                if (args != null && call(args, BUTTON_ARGS_GET_BUTTON, pressed) >= 0) {
                    BUTTONS[pressed.value]?.let(onButton)
                }
            }
            val token = LongByReference()
            check(call(controls, CONTROLS_ADD_BUTTON_PRESSED, buttons.pointer, token), "ButtonPressed")
            for (slot in listOf(CONTROLS_PUT_IS_PLAY_ENABLED, CONTROLS_PUT_IS_PAUSE_ENABLED, CONTROLS_PUT_IS_NEXT_ENABLED, CONTROLS_PUT_IS_PREVIOUS_ENABLED)) {
                call(controls, slot, flag(true))
            }

            // The progress bar and seeking from it came with the second interface; nice to have.
            val controls2 = queryInterface(controls, IID_SMTC_2)
            val handlers = mutableListOf(buttons)
            if (controls2 != null) {
                val seeks = ComDelegate(IID_POSITION_CHANGE_HANDLER) { args ->
                    val position = LongByReference()
                    if (args != null && call(args, POSITION_ARGS_GET_POSITION, position) >= 0) {
                        onSeek(position.value / TICKS_PER_MS)
                    }
                }
                if (call(controls2, CONTROLS2_ADD_POSITION_CHANGE_REQUESTED, seeks.pointer, LongByReference()) >= 0) handlers += seeks
            }
            val timeline = activateInstance(TIMELINE_CLASS)?.let { instance ->
                try {
                    queryInterface(instance, IID_TIMELINE_PROPERTIES)
                } finally {
                    release(instance)
                }
            }
            return WindowsMediaSession(
                controls = controls,
                updater = updater,
                controls2 = controls2,
                timeline = timeline,
                streamReferences = activationFactory(STREAM_REFERENCE_CLASS, IID_STREAM_REFERENCE_STATICS),
                uris = activationFactory(URI_CLASS, IID_URI_FACTORY),
                handlers = handlers,
                buttonToken = token.value,
            )
        }

        private const val SMTC_CLASS = "Windows.Media.SystemMediaTransportControls"
        private const val TIMELINE_CLASS = "Windows.Media.SystemMediaTransportControlsTimelineProperties"
        private const val STREAM_REFERENCE_CLASS = "Windows.Storage.Streams.RandomAccessStreamReference"
        private const val URI_CLASS = "Windows.Foundation.Uri"

        private val IID_SMTC_INTEROP = guid("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a")
        private val IID_SMTC = guid("99fa3ff4-1742-42a6-902e-087d41f965ec")
        private val IID_SMTC_2 = guid("ea98d2f6-7f3c-4af2-a586-72889808efb1")
        private val IID_MUSIC_PROPERTIES_2 = guid("00368462-97d3-44b9-b00f-008afcefaf18")
        private val IID_TIMELINE_PROPERTIES = guid("5125316a-c3a2-475b-8507-93534dc88f15")
        private val IID_STREAM_REFERENCE_STATICS = guid("857309dc-3fbf-4e7d-986f-ef3b1a07a964")
        private val IID_URI_FACTORY = guid("44a9796f-723e-4fdf-a218-033e75b0c084")
        private val IID_RANDOM_ACCESS_STREAM = guid("905a0fe1-bc53-11df-8c49-001e4fc686da")

        // TypedEventHandler<SystemMediaTransportControls, …EventArgs>: WinRT derives these
        // parameterised IIDs from the type names (a SHA-1 based UUID), computed once ahead of time.
        private val IID_BUTTON_PRESSED_HANDLER = guid("0557e996-7b23-5bae-aa81-ea0d671143a4")
        private val IID_POSITION_CHANGE_HANDLER = guid("44e34f15-bdc0-50a7-ace4-39e91fb753f1")

        // ISystemMediaTransportControlsInterop
        private const val INTEROP_GET_FOR_WINDOW = 6

        // ISystemMediaTransportControls
        private const val CONTROLS_PUT_PLAYBACK_STATUS = 7
        private const val CONTROLS_GET_DISPLAY_UPDATER = 8
        private const val CONTROLS_PUT_IS_ENABLED = 11
        private const val CONTROLS_PUT_IS_PLAY_ENABLED = 13
        private const val CONTROLS_PUT_IS_PAUSE_ENABLED = 17
        private const val CONTROLS_PUT_IS_PREVIOUS_ENABLED = 25
        private const val CONTROLS_PUT_IS_NEXT_ENABLED = 27
        private const val CONTROLS_ADD_BUTTON_PRESSED = 32
        private const val CONTROLS_REMOVE_BUTTON_PRESSED = 33

        // ISystemMediaTransportControls2
        private const val CONTROLS2_UPDATE_TIMELINE = 12
        private const val CONTROLS2_ADD_POSITION_CHANGE_REQUESTED = 13

        // ISystemMediaTransportControlsDisplayUpdater
        private const val UPDATER_PUT_TYPE = 7
        private const val UPDATER_PUT_THUMBNAIL = 11
        private const val UPDATER_GET_MUSIC_PROPERTIES = 12
        private const val UPDATER_CLEAR_ALL = 16
        private const val UPDATER_UPDATE = 17

        // IMusicDisplayProperties, IMusicDisplayProperties2
        private const val MUSIC_PUT_TITLE = 7
        private const val MUSIC_PUT_ARTIST = 11
        private const val MUSIC2_PUT_ALBUM_TITLE = 7

        // ISystemMediaTransportControlsTimelineProperties
        private const val TIMELINE_PUT_START_TIME = 7
        private const val TIMELINE_PUT_END_TIME = 9
        private const val TIMELINE_PUT_MIN_SEEK_TIME = 11
        private const val TIMELINE_PUT_MAX_SEEK_TIME = 13
        private const val TIMELINE_PUT_POSITION = 15

        // Event args, the URI factory and RandomAccessStreamReference's statics
        private const val BUTTON_ARGS_GET_BUTTON = 6
        private const val POSITION_ARGS_GET_POSITION = 6
        private const val URI_FACTORY_CREATE_URI = 6
        private const val STREAM_REFERENCE_CREATE_FROM_URI = 7
        private const val STREAM_REFERENCE_CREATE_FROM_STREAM = 8

        private const val STATUS_PLAYING = 3
        private const val STATUS_PAUSED = 4
        private const val TYPE_MUSIC = 1
        private const val RO_INIT_MULTITHREADED = 1
        private const val BSOS_DEFAULT = 0

        /** WinRT TimeSpans count 100 ns ticks. */
        private const val TICKS_PER_MS = 10_000L

        /** SystemMediaTransportControlsButton values Hymn acts on. */
        private val BUTTONS = mapOf(0 to Button.PLAY, 1 to Button.PAUSE, 2 to Button.STOP, 6 to Button.NEXT, 7 to Button.PREVIOUS)

        private interface Combase : StdCallLibrary {
            fun RoInitialize(initType: Int): Int
            fun RoGetActivationFactory(activatableClassId: Pointer?, iid: Pointer, factory: PointerByReference): Int
            fun RoActivateInstance(activatableClassId: Pointer?, instance: PointerByReference): Int
            fun WindowsCreateString(source: WString?, length: Int, string: PointerByReference): Int
            fun WindowsDeleteString(string: Pointer?): Int
        }

        private interface Shlwapi : StdCallLibrary {
            fun SHCreateMemStream(init: ByteArray, size: Int): Pointer?
        }

        private interface Shcore : StdCallLibrary {
            fun CreateRandomAccessStreamOverStream(stream: Pointer, options: Int, riid: Pointer, out: PointerByReference): Int
        }

        private val combase: Combase by lazy { Native.load("combase", Combase::class.java) }
        private val shlwapi: Shlwapi by lazy { Native.load("shlwapi", Shlwapi::class.java) }
        private val shcore: Shcore by lazy { Native.load("shcore", Shcore::class.java) }

        private fun activationFactory(className: String, iid: Pointer): Pointer? {
            val factory = PointerByReference()
            return if (withHString(className) { combase.RoGetActivationFactory(it, iid, factory) } >= 0) factory.value else null
        }

        private fun activateInstance(className: String): Pointer? {
            val instance = PointerByReference()
            return if (withHString(className) { combase.RoActivateInstance(it, instance) } >= 0) instance.value else null
        }

        private inline fun withHString(text: String, block: (Pointer?) -> Int): Int {
            val handle = PointerByReference()
            val created = combase.WindowsCreateString(WString(text), text.length, handle)
            if (created < 0) return created
            return try {
                block(handle.value)
            } finally {
                combase.WindowsDeleteString(handle.value)
            }
        }

        private fun putString(target: Pointer, slot: Int, text: String) {
            withHString(text) { call(target, slot, it) }
        }

        /** Calls the method in vtable [slot] of the COM object [target]; returns its HRESULT. */
        private fun call(target: Pointer, slot: Int, vararg args: Any?): Int {
            val method = target.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
            return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf(target, *args))
        }

        private fun getPointer(target: Pointer, slot: Int): Pointer? {
            val out = PointerByReference()
            return if (call(target, slot, out) >= 0) out.value else null
        }

        private fun queryInterface(target: Pointer, iid: Pointer): Pointer? = getPointerWith(target, QUERY_INTERFACE, iid)

        private fun getPointerWith(target: Pointer, slot: Int, arg: Any?): Pointer? {
            val out = PointerByReference()
            return if (call(target, slot, arg, out) >= 0) out.value else null
        }

        private fun release(target: Pointer?) {
            if (target != null) call(target, RELEASE)
        }

        private fun check(hresult: Int, what: String) {
            if (hresult < 0) error("$what failed: 0x%08x".format(hresult))
        }

        private fun flag(value: Boolean) = if (value) 1 else 0

        internal fun guid(text: String): Memory {
            val hex = text.replace("-", "")
            return Memory(16).apply {
                setInt(0, hex.substring(0, 8).toLong(16).toInt())
                setShort(4, hex.substring(8, 12).toInt(16).toShort())
                setShort(6, hex.substring(12, 16).toInt(16).toShort())
                for (i in 0 until 8) setByte(8L + i, hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte())
            }
        }

        private const val QUERY_INTERFACE = 0
        private const val RELEASE = 2
    }
}

/**
 * A minimal COM object for a WinRT event handler delegate: IUnknown plus Invoke(sender, args).
 * It lives as long as its owner and never frees itself, so reference counts are not tracked. It
 * says it is agile, so Windows calls it straight from its own threads.
 */
internal class ComDelegate(iid: Memory, private val onInvoke: (args: Pointer?) -> Unit) {
    private interface QueryInterface : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int
    }

    private interface RefCount : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }

    private interface Invoke : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    private val accepted = listOf(iid.getByteArray(0, 16), IUNKNOWN, IAGILE_OBJECT)

    private val queryInterface = object : QueryInterface {
        override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
            val asked = riid.getByteArray(0, 16)
            return if (accepted.any { it.contentEquals(asked) }) {
                out.setPointer(0, self)
                S_OK
            } else {
                out.setPointer(0, null)
                E_NOINTERFACE
            }
        }
    }
    private val refCount = object : RefCount {
        override fun invoke(self: Pointer): Int = 1
    }
    private val invoke = object : Invoke {
        override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
            runCatching { onInvoke(args) }.onFailure { System.err.println("Media controls: ${it.message}") }
            return S_OK
        }
    }

    private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
        listOf(queryInterface, refCount, refCount, invoke).forEachIndexed { index, callback ->
            setPointer(index.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callback))
        }
    }

    val pointer: Pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }

    private companion object {
        const val S_OK = 0
        const val E_NOINTERFACE = 0x80004002.toInt()
        val IUNKNOWN = WindowsMediaSession.guid("00000000-0000-0000-c000-000000000046").getByteArray(0, 16)
        val IAGILE_OBJECT = WindowsMediaSession.guid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90").getByteArray(0, 16)
    }
}
