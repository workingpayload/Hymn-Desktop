package com.example.musicsmd.playback

import com.example.musicsm.domain.model.Song
import com.example.musicsmd.player.isLoopablePlaylistId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QueuePersistenceTest {

    private val songs = listOf(Song("a", "A", "Artist"), Song("b", "B", "Artist"))

    @Test
    fun remembersThatTheQueueIsAPlaylistSoItStillLoopsAfterARestart() {
        val file = File(Files.createTempDirectory("queue").toFile(), "queue.json")
        val store = QueuePersistence(file)

        store.save(songs, songs, index = 1, positionMs = 500L, isPlaylist = true)
        assertTrue(QueuePersistence(file).load()!!.isPlaylist)

        store.save(songs, songs, index = 1, positionMs = 500L)
        assertFalse(QueuePersistence(file).load()!!.isPlaylist)
    }

    @Test
    fun queueSavedBeforeLoopPlaylistsLoadsAsNotAPlaylist() {
        val file = File(Files.createTempDirectory("queue").toFile(), "queue.json")
        file.writeText("""{"queue":[{"id":"a","title":"A","artist":"Artist"}],"index":0,"positionMs":0}""")

        val restored = QueuePersistence(file).load()!!

        assertEquals("a", restored.queue.single().id)
        assertFalse(restored.isPlaylist)
    }

    @Test
    fun albumsOpenedAsPlaylistsDoNotLoop() {
        assertTrue(isLoopablePlaylistId("12"))
        assertTrue(isLoopablePlaylistId("PLx1y2z3"))
        assertTrue(isLoopablePlaylistId("VLPLx1y2z3"))
        assertFalse(isLoopablePlaylistId("MPREb_abc"))
        assertFalse(isLoopablePlaylistId("OLAK5uy_abc"))
    }
}
