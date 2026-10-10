package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.model.Song
import java.text.Normalizer

/**
 * Tells apart *another song* from *another take on the same song*.
 *
 * YouTube's related graphs love the latter: after "Despacito" they offer the remix, the
 * Justin Bieber version, a flamenco guitar cover, the lyric video. Each is a different video, so
 * id checks let them through, and a radio queue ends up playing the same title over and over
 * instead of moving on to neighbouring artists. These rules reduce a title to the song it names —
 * dropping brackets, featured credits, version labels and the uploader's own name — so takes on one
 * song compare equal while genuinely different songs don't.
 */
object SongVariants {

    /** "(Official Video)", "[Remix]", "【MV】" and the like: packaging, never the song's name. */
    private val BRACKETED = Regex("""\([^)]*\)|\[[^\]]*\]|\{[^}]*\}|【[^】]*】|「[^」]*」""")

    /** A featured-artist tail outside brackets: "Mi Gente ft. Beyoncé". */
    private val FEATURING = Regex("""\s(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)

    /** "Artist - Title", "Title – Remix", "Title | Lyrics": the parts a video title is assembled from. */
    private val SEGMENT_SEPARATORS = Regex("""\s+[-–—|]\s+|\s*[|｜]\s*""")

    /** Words that name a version or a video format rather than the song. */
    private val VERSION_WORDS = Regex(
        """\b(?:official|music|lyrics?|lyrical|audio|video|visuali[sz]er|hd|hq|4k|mv|remix(?:ed)?|mix|edit|""" +
            """extended|radio|live|acoustic|unplugged|cover|karaoke|instrumental|version|ver|slowed|reverb|""" +
            """sped\s*up|nightcore|8d|lo-?fi|remaster(?:ed)?|\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

    /**
     * The comparison keys [song]'s title could be filed under — usually one, more when a video
     * title is "Artist - Title" and the artist part can't be told apart from the song.
     */
    fun titleKeys(song: Song): Set<String> {
        val artistKeys = ArtistMatching.creditKeys(song.artist)
        val unbracketed = BRACKETED.replace(song.title, " ").replace(FEATURING, "")
        val keys = SEGMENT_SEPARATORS.split(unbracketed)
            .map { normalize(VERSION_WORDS.replace(it, " ")) }
            .filter { it.isNotEmpty() && it !in artistKeys }
            .toSet()
        // A title that is nothing but packaging (or only the artist's name) still identifies itself.
        return keys.ifEmpty { setOfNotNull(normalize(unbracketed).ifEmpty { normalize(song.title) }.takeIf { it.isNotEmpty() }) }
    }

    /** True when [a] and [b] are recordings of the same song (or are the same track). */
    fun sameSong(a: Song, b: Song): Boolean =
        a.id == b.id || titleKeys(a).any { it in titleKeys(b) }

    /**
     * [candidates] without the songs already in [heard] — by id or as another take of the same
     * song — and keeping only the first take of any song that appears more than once, in order.
     */
    fun freshSongs(candidates: List<Song>, heard: Collection<Song>): List<Song> {
        val seenIds = heard.mapTo(HashSet()) { it.id }
        val seenKeys = heard.flatMapTo(HashSet(), ::titleKeys)
        return candidates.filter { song ->
            if (song.id in seenIds) return@filter false
            val keys = titleKeys(song)
            if (keys.any { it in seenKeys }) return@filter false
            seenIds += song.id
            seenKeys += keys
            true
        }
    }

    private fun normalize(raw: String): String {
        val folded = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return NON_ALPHANUMERIC.replace(folded, "").lowercase()
    }
}
