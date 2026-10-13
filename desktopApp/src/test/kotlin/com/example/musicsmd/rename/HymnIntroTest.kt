package com.example.musicsmd.rename

import com.example.musicsmd.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HymnIntroTest {

    @Test
    fun showsAfterUpdatingFromMusicSm() {
        assertTrue(shouldShowHymnIntro(seen = false, hadAppData = true))
    }

    @Test
    fun hiddenOnFreshInstall() {
        assertFalse(shouldShowHymnIntro(seen = false, hadAppData = false))
    }

    @Test
    fun hiddenOnceSeen() {
        assertFalse(shouldShowHymnIntro(seen = true, hadAppData = true))
    }

    @Test
    fun appDataMeansFilesInTheFolder() {
        val dir = Files.createTempDirectory("hymn-intro").toFile()
        try {
            assertFalse(hasAppData(File(dir, "missing")))
            assertFalse(hasAppData(dir))
            File(dir, "downloads").mkdirs()
            assertFalse(hasAppData(dir))
            File(dir, "library.json").writeText("{}")
            assertTrue(hasAppData(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun playsOnceForAnExistingUser() {
        val dir = Files.createTempDirectory("hymn-intro").toFile()
        try {
            val file = File(dir, "settings.json").apply { writeText("""{"volume":80}""") }
            val first = HymnIntroState(SettingsStore(file), hadAppData = true, forced = false)
            assertTrue(first.visible.value)
            first.dismiss()
            assertFalse(first.visible.value)

            val store = SettingsStore(file)
            assertTrue(store.current.hymnIntroSeen)
            assertFalse(HymnIntroState(store, hadAppData = true, forced = false).visible.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun freshInstallIsMarkedSeenWithoutPlaying() {
        val dir = Files.createTempDirectory("hymn-intro").toFile()
        try {
            val store = SettingsStore(File(dir, "settings.json"))
            assertFalse(HymnIntroState(store, hadAppData = false, forced = false).visible.value)
            assertTrue(store.current.hymnIntroSeen)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun forcedPlaysEvenWhenSeen() {
        val dir = Files.createTempDirectory("hymn-intro").toFile()
        try {
            val file = File(dir, "settings.json").apply { writeText("""{"hymnIntroSeen":true}""") }
            assertTrue(HymnIntroState(SettingsStore(file), hadAppData = false, forced = true).visible.value)
        } finally {
            dir.deleteRecursively()
        }
    }
}
