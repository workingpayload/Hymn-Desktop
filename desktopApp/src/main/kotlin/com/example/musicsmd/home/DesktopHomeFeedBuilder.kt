package com.example.musicsmd.home

import com.example.musicsm.data.source.youtube.signin.YouTubeAccountLibrary
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.recommend.DailyMix
import com.example.musicsm.domain.recommend.DailyMixes
import com.example.musicsm.domain.recommend.DailyRotation
import com.example.musicsm.domain.recommend.NewReleases
import com.example.musicsm.domain.recommend.ShelfRanker
import com.example.musicsm.domain.recommend.TasteProfile
import com.example.musicsm.domain.recommend.TasteProfiles
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsmd.stats.SkipSignals
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** What Home shows: the shelves, and today's daily mixes as a row of their own. */
data class DesktopHome(
    val feed: HomeFeed,
    val mixes: List<DailyMix> = emptyList(),
)

/**
 * The page's releases, credited to the artist. Cards on an artist page don't name the artist (the
 * page already does), so the source falls back to repeating the title as the byline.
 */
private fun Artist.creditedAlbums(): List<Album> = albums.map { album ->
    if (album.artist.isBlank() || album.artist == album.title) album.copy(artist = name) else album
}

/**
 * Builds the desktop Home feed: the signed-in YouTube account's own shelves and history first
 * (when there is one and it's allowed), then shelves from the listener's own history, then
 * personalized catalog shelves (ported from mobile's HomeViewModel), then the provider's generic
 * feed (left out when the account's home stands in for it).
 *
 * Everything is deterministic for a given day and [refresh] count: the feed stays put while you
 * look at it, and each manual refresh rotates which seeds, artists and slices are used, so it
 * brings something new without being random. Recent skips ([SkipSignals]) keep songs and artists
 * the listener keeps moving on from out of the discovery shelves.
 */
class DesktopHomeFeedBuilder(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val stats: StatsRepository,
    /** The signed-in YouTube account, whose personal shelves lead Home when it is available. */
    private val account: YouTubeAccountLibrary? = null,
    private val skips: () -> SkipSignals = { SkipSignals() },
) {
    /** Followed artists not yet given a shelf, offered as the page scrolls past the provider's feed. */
    @Volatile
    private var pendingArtists: List<Artist> = emptyList()

    @Volatile
    private var avoid: Avoid = Avoid(SkipSignals(), TasteProfile())

    /** The latest taste profile, so a mood can lead with the listener's favourite acts. */
    @Volatile
    private var profile: TasteProfile = TasteProfile()

    /** Followed artists' pages fetched for this feed, by id, reused while paging. */
    private val artistPages = ConcurrentHashMap<String, Artist>()

    /** The mixes built for a set of anchor tracks, so rebuilding the same day's Home reuses them. */
    @Volatile
    private var mixCache: Pair<List<String>, List<DailyMix>>? = null

    suspend fun initialFeed(refresh: Int = 0): HomeFeed = initialHome(refresh).feed

    /** The feed plus today's daily mixes, which Home shows as their own row of cards. */
    suspend fun initialHome(refresh: Int = 0): DesktopHome = coroutineScope {
        artistPages.clear()
        val key = DailyRotation.today() + refresh
        val likedAsync = async { runCatching { library.likedSongs().first() }.getOrDefault(emptyList()) }
        val recentAsync = async { runCatching { library.recentlyPlayed().first() }.getOrDefault(emptyList()) }
        val followedAsync = async { runCatching { library.likedArtists().first() }.getOrDefault(emptyList()) }
        val recentStatsAsync = async { statsOrEmpty(StatsRange.LAST_4_WEEKS) }
        val lifetimeStatsAsync = async { statsOrEmpty(StatsRange.ALL_TIME) }
        val forgottenAsync = async { runCatching { stats.forgottenFavorites(SHELF_SIZE) }.getOrDefault(emptyList()).map { it.song } }
        val providerAsync = async { runCatching { music.homeFeed() }.getOrDefault(HomeFeed()) }
        val trendingAsync = async { runCatching { music.trending() }.getOrDefault(emptyList()) }
        val acct = account?.takeIf { it.isAvailable }
        val accountHomeAsync = async { acct?.let { runCatching { it.accountHome() }.getOrNull() } ?: HomeFeed() }
        val accountHistoryAsync = async {
            acct?.let { runCatching { it.accountHistory(ACCOUNT_HISTORY_POOL) }.getOrNull() }.orEmpty()
        }

        val liked = likedAsync.await()
        val recent = recentAsync.await()
        val youTubeHistory = accountHistoryAsync.await()
        val profile = TasteProfiles.build(
            recent = recentStatsAsync.await(),
            lifetime = lifetimeStatsAsync.await(),
            liked = liked,
            followedArtists = followedAsync.await(),
            // YouTube history seeds recommendations too, behind what was played here.
            recentlyPlayed = (recent + youTubeHistory).distinctBy { it.id },
            seedCount = SEED_POOL,
            rotationSize = SHELF_SIZE * 2,
        )
        val avoid = Avoid(skips(), profile).also { this@DesktopHomeFeedBuilder.avoid = it }
        this@DesktopHomeFeedBuilder.profile = profile
        val followed = followedAsync.await().sortedByDescending { profile.affinity(it.name) }

        // Each refresh moves these windows along; within one refresh they're fixed.
        val seeds = DailyRotation.pick(profile.seeds.filterNot(avoid::song), SEED_COUNT, key)
        val discoverSeeds = DailyRotation.pick(profile.discoveryPool().filterNot(avoid::song), DISCOVER_SEEDS, key + 1)
        val topSeed = seeds.firstOrNull()
        val artistPicks = DailyRotation.pick(followed.take(ARTIST_POOL), SEED_COUNT, key)
        // New releases look across the listener's top follows, not only this refresh's picks.
        val pageArtists = (artistPicks + followed.take(RELEASE_ARTIST_COUNT)).distinctBy { it.id }

        val mixesAsync = async { mixes(profile, avoid, key) }
        val discoverAsync = async { recommendations(discoverSeeds) }
        val recommendedAsync = async { recommendations(seeds) }
        val relatedAsync = async {
            topSeed?.let { seed -> runCatching { music.relatedTo(seed.id) }.getOrDefault(emptyList()) }.orEmpty()
        }
        val artistPagesAsync = async {
            pageArtists.map { artist -> async { artistPage(artist.id) } }
                .awaitAll()
                .filterNotNull()
        }

        val sections = mutableListOf<HomeSection>()
        val shown = linkedSetOf<String>()
        fun addSongs(title: String, songs: List<Song>, min: Int = 1) {
            // A shelf the account's home already has by this name keeps YouTube's version.
            if (sections.any { it.title == title }) return
            val fresh = songs.distinctBy { it.id }.filter { it.id.isNotBlank() && it.id !in shown }.take(SHELF_SIZE)
            if (fresh.size < min) return
            shown += fresh.map { it.id }
            sections += HomeSection(title, fresh.map { HomeItem.SongItem(it) })
        }
        fun discovery(candidates: List<Song>, exclude: Set<String> = emptySet(), maxPerArtist: Int = ShelfRanker.DEFAULT_MAX_PER_ARTIST) =
            ShelfRanker.rank(
                candidates.filterNot { avoid.song(it, discovery = true) },
                profile,
                SHELF_SIZE,
                exclude = shown + exclude,
                excludeKnown = profile.hasHistory,
                maxPerArtist = maxPerArtist,
            )

        // The account's own shelves lead; the local ones below skip songs those already show.
        val accountHome = accountHomeAsync.await()
        accountHome.sections.forEach { section ->
            if (section.items.isEmpty()) return@forEach
            sections += section
            section.items.filterIsInstance<HomeItem.SongItem>().forEach { shown += it.song.id }
        }
        addSongs("Recently played on YouTube", youTubeHistory.filterNot { avoid.song(it) })

        addSongs("Recently played", recent)
        addSongs("Listen again", DailyRotation.pick(profile.heavyRotation.filterNot { avoid.song(it) }, SHELF_SIZE, key))

        // Timely, and only ever from people the listener chose to follow, so it sits high up.
        val fetchedPages = artistPagesAsync.await()
        val pageAlbums = fetchedPages.map { it.creditedAlbums() }
        val releases = NewReleases.pick(pageAlbums, LocalDate.now().year, SHELF_SIZE)
        if (releases.size >= MIN_RELEASE_SHELF) {
            sections += HomeSection(NEW_RELEASES_TITLE, releases.map { HomeItem.AlbumItem(it) })
        }
        val releaseIds = releases.mapTo(HashSet()) { it.id }

        addSongs("Daily discover", discovery(discoverAsync.await()), min = MIN_SONG_SHELF)
        addSongs("Recommended for you", discovery(recommendedAsync.await()), min = MIN_SONG_SHELF)
        topSeed?.let { seed ->
            val title = if (profile.hasHistory) "More like ${seed.title}" else "Because you liked ${seed.title}"
            addSongs(title, discovery(relatedAsync.await(), exclude = setOf(seed.id)), min = MIN_SONG_SHELF)
        }

        if (followed.size >= MIN_ARTIST_SHELF) {
            sections += HomeSection("Your artists", followed.take(ARTIST_SHELF_SIZE).map { HomeItem.ArtistItem(it) })
        }
        val pickedIds = artistPicks.mapTo(HashSet()) { it.id }
        val artistPages = fetchedPages.filter { it.id in pickedIds }
        addSongs(
            "From artists you follow",
            ShelfRanker.rank(
                artistPages.flatMap { it.topSongs.take(ARTIST_PICKS) }.filterNot { avoid.song(it) },
                profile,
                SHELF_SIZE,
                exclude = shown,
                maxPerArtist = ARTIST_PICKS,
            ),
            min = MIN_SONG_SHELF,
        )

        addSongs("Forgotten favorites", forgottenAsync.await().filterNot { avoid.song(it) })

        // Anything already offered as a new release isn't offered twice.
        val albums = pageAlbums.flatten()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.id !in releaseIds }
            .distinctBy { it.id }
            .take(SHELF_SIZE)
        if (albums.size >= MIN_ALBUM_SHELF) {
            sections += HomeSection("Albums from your artists", albums.map { HomeItem.AlbumItem(it) })
        }

        addSongs("Trending now", ShelfRanker.dedupe(trendingAsync.await(), SHELF_SIZE, shown))

        // Artists already given a shelf here aren't offered again while paging.
        pendingArtists = followed.filter { artist -> artistPicks.none { it.id == artist.id } }

        val provider = providerAsync.await()
        val mixes = mixesAsync.await()
        // Signed in, the account's home stands in for the anonymous editorial feed; its pages
        // continue through the account.
        if (accountHome.sections.isNotEmpty()) {
            return@coroutineScope DesktopHome(
                feed = HomeFeed(
                    sections = sections,
                    continuation = accountHome.continuation?.let { ACCOUNT_CURSOR_PREFIX + it }
                        ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() },
                ),
                mixes = mixes,
            )
        }
        val generic = if (profile.hasHistory) provider.sections.take(MAX_GENERIC_SECTIONS) else provider.sections
        DesktopHome(
            feed = HomeFeed(
                sections = merge(sections, generic),
                continuation = provider.continuation ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() },
            ),
            mixes = mixes,
        )
    }

    /**
     * A queue for a mood chip: the catalog's tracks for it, the listener's favourite acts first,
     * without songs or artists they keep skipping.
     */
    suspend fun moodQueue(tile: BrowseTile): List<Song> {
        val candidates = runCatching { music.searchSongs(tile.query) }.getOrDefault(emptyList())
        val avoid = avoid
        // Searches for a mood turn up hour-long compilations; a mood queue is for songs.
        val songs = candidates.filterNot { avoid.song(it) || it.durationMs > MOOD_MAX_DURATION_MS }
        return ShelfRanker.rank(songs, profile, MOOD_SIZE, maxPerArtist = MOOD_MAX_PER_ARTIST)
    }

    /**
     * Daily mixes for [key]'s day and refresh, each grown from a different favourite. One request
     * per mix; the same anchors reuse the mixes already built.
     */
    private suspend fun mixes(profile: TasteProfile, avoid: Avoid, key: Long): List<DailyMix> = coroutineScope {
        val anchors = DailyMixes.anchors(profile, MIX_COUNT, key).filterNot { avoid.song(it) }
        if (anchors.size < MIN_MIXES) return@coroutineScope emptyList()
        val ids = anchors.map { it.id }
        mixCache?.takeIf { it.first == ids }?.let { return@coroutineScope it.second }
        val mixes = anchors
            .map { seed -> async { runCatching { music.relatedTo(seed.id) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .mapIndexedNotNull { index, related ->
                DailyMixes.assemble(
                    number = index + 1,
                    seed = anchors[index],
                    related = related.filterNot { avoid.song(it) },
                    limit = MIX_SIZE,
                )
            }
            .takeIf { it.size >= MIN_MIXES }
            .orEmpty()
        if (mixes.isNotEmpty()) mixCache = ids to mixes
        mixes
    }

    /** A followed artist's page, fetched once per feed. */
    private suspend fun artistPage(id: String): Artist? =
        artistPages[id] ?: runCatching { music.artist(id) }.getOrNull()?.also { artistPages[id] = it }

    /**
     * The next shelves as the page scrolls: the provider's own feed while it has more, then a
     * "More from" shelf per remaining followed artist, a few at a time.
     */
    suspend fun moreShelves(continuation: String, shownSongIds: Set<String>): HomeFeed {
        if (continuation.startsWith(ACCOUNT_CURSOR_PREFIX)) {
            val more = account?.let { runCatching { it.moreAccountHome(continuation.removePrefix(ACCOUNT_CURSOR_PREFIX)) }.getOrNull() }
            if (more != null && more.sections.isNotEmpty()) {
                val next = more.continuation?.let { ACCOUNT_CURSOR_PREFIX + it } ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() }
                return HomeFeed(more.sections, next)
            }
            return artistShelves(shownSongIds.toMutableSet())
        }
        if (continuation != ARTIST_CURSOR) {
            val more = runCatching { music.moreHomeShelves(continuation) }.getOrNull()
            if (more != null && more.sections.isNotEmpty()) {
                return HomeFeed(more.sections, more.continuation ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() })
            }
        }
        return artistShelves(shownSongIds.toMutableSet())
    }

    private suspend fun artistShelves(shown: MutableSet<String>): HomeFeed = coroutineScope {
        val batch = pendingArtists.take(ARTIST_PAGE_SIZE)
        pendingArtists = pendingArtists.drop(batch.size)
        val avoid = avoid
        val sections = batch
            .map { artist -> async { artistPage(artist.id) } }
            .awaitAll()
            .filterNotNull()
            .mapNotNull { page ->
                val songs = page.topSongs.filter { it.id.isNotBlank() && it.id !in shown && !avoid.song(it) }.take(SHELF_SIZE)
                if (songs.size < MIN_SONG_SHELF) return@mapNotNull null
                shown += songs.map { it.id }
                HomeSection("More from ${page.name}", songs.map { HomeItem.SongItem(it) })
            }
        HomeFeed(sections, ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() })
    }

    private suspend fun recommendations(seeds: List<Song>): List<Song> =
        if (seeds.isEmpty()) emptyList() else runCatching { music.recommendations(seeds, SHELF_SIZE * 2) }.getOrDefault(emptyList())

    private suspend fun statsOrEmpty(range: StatsRange): ListeningStats =
        runCatching { stats.stats(range).first() }.getOrDefault(ListeningStats(range = range))

    private fun TasteProfile.discoveryPool(): List<Song> = heavyRotation.ifEmpty { seeds }

    private fun merge(first: List<HomeSection>, second: List<HomeSection>): List<HomeSection> {
        val seen = first.mapTo(mutableSetOf()) { it.title }
        return first + second.filter { it.items.isNotEmpty() && seen.add(it.title) }
    }

    /** What recent skips rule out. */
    internal class Avoid(private val signals: SkipSignals, private val profile: TasteProfile) {
        /**
         * A song skipped repeatedly, or once when it would be offered as something new, or by an
         * artist the listener keeps skipping and doesn't otherwise play much.
         */
        fun song(song: Song, discovery: Boolean): Boolean {
            val skipped = if (discovery) signals.skippedOnce else signals.skippedRepeatedly
            return song.id in skipped || artist(song.artist)
        }

        fun song(song: Song): Boolean = song(song, discovery = false)

        private fun artist(credit: String): Boolean =
            signals.artistSkipCount(credit) >= AVOID_ARTIST_SKIPS && profile.affinity(credit) < AVOID_ARTIST_MAX_AFFINITY
    }

    companion object {
        /** Continuation meaning "the provider is done; next come followed-artist shelves". */
        const val ARTIST_CURSOR = "musicsm:followed-artists"

        /** Marks a continuation of the signed-in account's home feed. */
        const val ACCOUNT_CURSOR_PREFIX = "musicsm:account:"

        const val NEW_RELEASES_TITLE = "New from artists you follow"

        /** YouTube history songs read for the shelf and for recommendation seeds. */
        private const val ACCOUNT_HISTORY_POOL = 50

        /** Followed artists whose pages are read for new releases, and the fewest worth a row. */
        private const val RELEASE_ARTIST_COUNT = 8
        private const val MIN_RELEASE_SHELF = 2

        /** Daily mixes per day, the fewest worth a row, and each one's length. */
        private const val MIX_COUNT = 4
        private const val MIN_MIXES = 2
        private const val MIX_SIZE = 30

        /** A mood queue's length, and how much of it one act may take. */
        private const val MOOD_SIZE = 30
        private const val MOOD_MAX_PER_ARTIST = 2
        private const val MOOD_MAX_DURATION_MS = 15 * 60_000L

        private const val SEED_POOL = 8
        private const val SEED_COUNT = 4
        private const val SHELF_SIZE = 12
        private const val DISCOVER_SEEDS = 3
        private const val ARTIST_POOL = 12
        private const val ARTIST_PICKS = 4
        private const val ARTIST_SHELF_SIZE = 12
        private const val ARTIST_PAGE_SIZE = 3
        private const val MIN_ARTIST_SHELF = 3
        private const val MIN_ALBUM_SHELF = 3
        private const val MIN_SONG_SHELF = 4
        private const val MAX_GENERIC_SECTIONS = 5

        internal const val AVOID_ARTIST_SKIPS = 4
        internal const val AVOID_ARTIST_MAX_AFFINITY = 0.25
    }
}
