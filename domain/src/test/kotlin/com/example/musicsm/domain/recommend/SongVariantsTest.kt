package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVariantsTest {

    private fun song(id: String, title: String, artist: String = "Luis Fonsi") = Song(id, title, artist)

    private val despacito = song("a", "Despacito (feat. Daddy Yankee)", "Luis Fonsi & Daddy Yankee")

    @Test
    fun `remixes, covers and videos of a song are the same song`() {
        listOf(
            song("b", "Despacito"),
            song("c", "Despacito (Remix) [feat. Justin Bieber]", "Justin Bieber"),
            song("d", "Despacito - Remix", "Luis Fonsi"),
            song("e", "Luis Fonsi - Despacito ft. Daddy Yankee (Official Video)", "Luis Fonsi"),
            song("f", "DESPACITO | Lyrics", "Some Channel"),
            song("g", "Despacíto (Live)", "Imad Fares"),
        ).forEach { assertTrue(it.title, SongVariants.sameSong(despacito, it)) }
    }

    @Test
    fun `different songs, even by the same artist, are not`() {
        listOf(
            song("h", "Échame La Culpa"),
            song("i", "Con Calma (feat. Snow)", "Daddy Yankee"),
            song("j", "Luis Fonsi - Sola", "Luis Fonsi"),
            song("k", "Despacito Para Siempre"),
        ).forEach { assertFalse(it.title, SongVariants.sameSong(despacito, it)) }
    }

    @Test
    fun `titles made only of version words still identify themselves`() {
        assertTrue(SongVariants.sameSong(song("a", "Music", "Madonna"), song("b", "Music (Live)", "Madonna")))
        assertFalse(SongVariants.sameSong(song("a", "1999", "Prince"), song("b", "2002", "Anne-Marie")))
    }

    @Test
    fun `fresh songs drop what was heard and repeats, keeping order`() {
        val candidates = listOf(
            song("b", "Despacito (Remix)"),
            song("h", "Échame La Culpa"),
            song("a", "Despacito"),
            song("i", "Con Calma", "Daddy Yankee"),
            song("x", "Echame la culpa (Official Video)", "Luis Fonsi & Demi Lovato"),
            song("h", "Échame La Culpa"),
        )
        val fresh = SongVariants.freshSongs(candidates, heard = listOf(despacito))
        assertEquals(listOf("h", "i"), fresh.map { it.id })
    }
}
