package com.example.musicsmd.desktop

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File

/**
 * Gives an installer-installed Hymn the app ID of its Start-menu entry, so Windows' media flyout
 * can name it "Hymn", with its icon, instead of "Unknown app".
 *
 * Windows names an unpackaged app after the Start-menu shortcut that launches it, and identifies a
 * shortcut without an explicit ID by its target's path under a known folder:
 * `{ProgramFiles GUID}\Hymn\Hymn.exe`. The media controls only see the process, which on its own
 * reports a bare `Hymn.exe`; setting the same ID on the process lets the two meet. Since it is the
 * ID Windows already gives the shortcut, taskbar pins keep grouping with the window.
 *
 * Skipped for the Microsoft Store build, which Windows already knows by its package, and for
 * development runs through `java.exe`. Must run before any window is created.
 */
object WindowsAppIdentity {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private interface Kernel32Packages : StdCallLibrary {
        fun GetCurrentPackageFullName(length: IntByReference, name: CharArray?): Int
    }

    fun apply() {
        if (!isWindows) return
        runCatching {
            if (hasPackageIdentity()) return
            val exe = ProcessHandle.current().info().command().orElse(null) ?: return
            if (File(exe).name.lowercase() in DEVELOPMENT_LAUNCHERS) return
            val folders = KNOWN_FOLDERS.mapNotNull { (guid, folder) ->
                runCatching { Shell32Util.getKnownFolderPath(folder) }.getOrNull()?.let { guid to it }
            }
            Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(WString(startMenuId(exe, folders)))
        }
    }

    /** The ID Windows gives a shortcut to [exePath]: its path under the deepest known folder. */
    internal fun startMenuId(exePath: String, folders: List<Pair<String, String>>): String {
        val match = folders
            .map { (guid, dir) -> guid to dir.trimEnd('\\') }
            .filter { (_, dir) -> dir.isNotEmpty() && exePath.startsWith("$dir\\", ignoreCase = true) }
            .maxByOrNull { (_, dir) -> dir.length }
            ?: return exePath
        val (guid, dir) = match
        return "{$guid}${exePath.substring(dir.length)}"
    }

    private fun hasPackageIdentity(): Boolean {
        val kernel32 = Native.load("kernel32", Kernel32Packages::class.java)
        return kernel32.GetCurrentPackageFullName(IntByReference(0), null) != APPMODEL_ERROR_NO_PACKAGE
    }

    private const val APPMODEL_ERROR_NO_PACKAGE = 15700
    private val DEVELOPMENT_LAUNCHERS = setOf("java.exe", "javaw.exe")
    private val KNOWN_FOLDERS = listOf(
        "6D809377-6AF0-444B-8957-A3773F02200E" to KnownFolders.FOLDERID_ProgramFilesX64,
        "7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E" to KnownFolders.FOLDERID_ProgramFilesX86,
    )
}
