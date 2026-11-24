package com.example.musicsmd.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsAppIdentityTest {

    private val folders = listOf(
        "6D809377-6AF0-444B-8957-A3773F02200E" to "C:\\Program Files",
        "7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E" to "C:\\Program Files (x86)",
    )

    @Test
    fun installedExeIsNamedByItsKnownFolder() {
        assertEquals(
            "{6D809377-6AF0-444B-8957-A3773F02200E}\\Hymn\\Hymn.exe",
            WindowsAppIdentity.startMenuId("C:\\Program Files\\Hymn\\Hymn.exe", folders),
        )
        // "Program Files (x86)" starts with "Program Files" but is its own folder.
        assertEquals(
            "{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}\\Hymn\\Hymn.exe",
            WindowsAppIdentity.startMenuId("c:\\program files (x86)\\Hymn\\Hymn.exe", folders),
        )
    }

    @Test
    fun exeOutsideKnownFoldersKeepsItsPath() {
        val path = "C:\\Users\\me\\AppData\\Local\\Hymn\\Hymn.exe"
        assertEquals(path, WindowsAppIdentity.startMenuId(path, folders))
    }
}
