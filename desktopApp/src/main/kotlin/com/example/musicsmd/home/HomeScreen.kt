package com.example.musicsmd.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.recommend.DailyMix
import com.example.musicsmd.player.AppUiState
import com.example.musicsmd.settings.HomeLayout
import com.example.musicsmd.ui.components.AlbumCard
import com.example.musicsmd.ui.components.ArtistCircle
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.LocalBottomBarPadding
import com.example.musicsmd.ui.components.SongCard
import com.example.musicsmd.ui.components.SongRow
import com.example.musicsmd.ui.components.rememberDominantColorState
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.GlassStrokeSoft
import com.example.musicsmd.ui.theme.OnAccent
import com.example.musicsmd.ui.theme.OnDarkVariant
import com.example.musicsmd.ui.theme.OverlayTint
import com.example.musicsmd.ui.theme.SurfaceHigh
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    state: AppUiState,
    layout: HomeLayout,
    moods: List<BrowseTile>,
    onLayoutChange: (HomeLayout) -> Unit,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onMood: (BrowseTile) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    onRefreshHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todayLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    color = Coral,
                )
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            RefreshButton(
                refreshing = state.isRefreshingHome,
                enabled = !state.isLoadingHome,
                onClick = onRefreshHome,
            )
            Spacer(Modifier.width(10.dp))
            LayoutToggle(layout = layout, onChange = onLayoutChange)
        }
        Spacer(Modifier.height(14.dp))

        HomeBody(
            state = state,
            layout = layout,
            moods = moods,
            isLiked = isLiked,
            onSongClick = onSongClick,
            onShuffleSongs = onShuffleSongs,
            onToggleLike = onToggleLike,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onMood = onMood,
            onLoadMore = onLoadMoreHome,
            onRetry = onRetryHome,
        )

        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun HomeBody(
    state: AppUiState,
    layout: HomeLayout,
    moods: List<BrowseTile>,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onMood: (BrowseTile) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val sections = state.homeFeed.sections
    val canLoadMore = state.homeFeed.continuation != null
    val generation = state.homeGeneration

    // The hero features the first shelf with something to play or open (artists keep their own
    // row of avatars); the rest of that shelf follows below rather than repeating the hero.
    val featuredIndex = remember(sections) {
        sections.indexOfFirst { section -> section.items.any { it !is HomeItem.ArtistItem } }
    }
    val featured = sections.getOrNull(featuredIndex)
    val featuredSongs = remember(featured) {
        featured?.items?.filterIsInstance<HomeItem.SongItem>()?.map { it.song }.orEmpty()
    }
    val heroItems = remember(featured) {
        featured?.items?.filter { it !is HomeItem.ArtistItem }?.take(HERO_COUNT).orEmpty()
    }
    val shelves = remember(sections, heroItems) {
        val heroKeys = heroItems.mapTo(HashSet()) { it.key() }
        sections.mapIndexedNotNull { index, section ->
            if (index != featuredIndex) return@mapIndexedNotNull section
            section.copy(items = section.items.filter { it.key() !in heroKeys }).takeIf { it.items.isNotEmpty() }
        }
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, canLoadMore, sections.size) {
        if (nearEnd && canLoadMore && !state.isLoadingHome) onLoadMore()
    }
    // A refreshed feed starts from the top, not wherever the old one was scrolled to.
    var seenGeneration by remember { mutableIntStateOf(generation) }
    LaunchedEffect(generation) {
        if (generation != seenGeneration) {
            seenGeneration = generation
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 28.dp + LocalBottomBarPadding.current),
    ) {
        if (moods.isNotEmpty()) {
            item(key = "moods") { MoodChips(moods = moods, loadingId = state.moodLoading, onClick = onMood) }
        }

        when {
            state.isLoadingHome -> item(key = "loading") { LoadingRow() }
            sections.isEmpty() -> item(key = "empty") { EmptyHome(onRetry = onRetry) }
            else -> {
                if (heroItems.isNotEmpty()) {
                    item(key = "hero") {
                        HeroCarousel(
                            entries = heroItems.mapNotNull { item ->
                                item.toHeroEntry(
                                    onPlay = { song -> onSongClick(song, featuredSongs) },
                                    onOpenAlbum = onAlbumClick,
                                    onOpenPlaylist = onPlaylistClick,
                                )
                            },
                            onShuffle = { onShuffleSongs(featuredSongs) },
                        )
                    }
                }
                if (state.homeMixes.isNotEmpty()) {
                    item(key = "mixes-title") { ShelfTitle("Your daily mixes") }
                    item(key = "mixes") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.homeMixes, key = { it.number }) { mix ->
                                MixCard(mix = mix, onClick = { mix.songs.firstOrNull()?.let { onSongClick(it, mix.songs) } })
                            }
                        }
                    }
                }
                shelves(
                    sections = shelves,
                    layout = layout,
                    isLiked = isLiked,
                    onSongClick = onSongClick,
                    onToggleLike = onToggleLike,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                )
                if (state.isLoadingMoreHome) item(key = "loading-more") { LoadingRow() }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelves(
    sections: List<HomeSection>,
    layout: HomeLayout,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    sections.forEachIndexed { index, section ->
        item(key = "title-$index-${section.title}") { ShelfTitle(section.title) }
        val songs = section.items.filterIsInstance<HomeItem.SongItem>().map { it.song }
        val openCard: (HomeItem) -> Unit = { item ->
            when (item) {
                is HomeItem.AlbumItem -> onAlbumClick(item.album)
                is HomeItem.ArtistItem -> onArtistClick(item.artist)
                is HomeItem.PlaylistItem -> onPlaylistClick(item.playlist)
                // Plays the shelf from this song, as a tapped card does on mobile.
                is HomeItem.SongItem -> onSongClick(item.song, songs)
            }
        }

        if (layout == HomeLayout.CARDS) {
            // One horizontal shelf per section, in the feed's own order — mobile's Home.
            item(key = "shelf-$index-${section.title}") {
                CardShelf(items = section.items, onClick = openCard) { item ->
                    SongCard(
                        song = item.song,
                        onClick = { openCard(item) },
                        isLiked = isLiked(item.song.id),
                        onToggleLike = { onToggleLike(item.song) },
                    )
                }
            }
            return@forEachIndexed
        }

        val cards = section.items.filterNot { it is HomeItem.SongItem }
        if (cards.isNotEmpty()) {
            item(key = "cards-$index-${section.title}") {
                CardShelf(items = cards, onClick = openCard, songCard = {})
            }
        }
        items(songs, key = { song -> "${section.title}-${song.id}" }) { song ->
            SongRow(
                song = song,
                onClick = { onSongClick(song, songs) },
                isLiked = isLiked(song.id),
                onToggleLike = { onToggleLike(song) },
            )
        }
    }
}

// ---- Moods and quick tiles ---------------------------------------------------------------------

/** One-click moods under the greeting; the clicked chip spins until its queue starts. */
@Composable
private fun MoodChips(moods: List<BrowseTile>, loadingId: String?, onClick: (BrowseTile) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        items(moods, key = { it.id }) { tile ->
            // Some tile colours are deep (navy, plum); lifted so the dot reads on a dark page.
            val color = lerp(Color(tile.accentColor), Color.White, 0.3f)
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OverlayTint.copy(alpha = 0.07f))
                    .border(1.dp, GlassStrokeSoft, CircleShape)
                    .clickable(enabled = loadingId == null, onClickLabel = "Play ${tile.title}") { onClick(tile) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                    if (loadingId == tile.id) {
                        CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                    } else {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(tile.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ---- Hero --------------------------------------------------------------------------------------

/** What a hero page shows, whatever kind of item it features. */
private class HeroEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    /** Plays a track; otherwise the page opens an album or playlist. */
    val playable: Boolean,
    val onOpen: () -> Unit,
)

private fun HomeItem.toHeroEntry(
    onPlay: (Song) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
): HeroEntry? = when (this) {
    is HomeItem.SongItem -> HeroEntry(key(), song.title, song.artist, song.artworkUrl, playable = true) { onPlay(song) }
    is HomeItem.AlbumItem -> HeroEntry(key(), album.title, album.artist, album.artworkUrl, playable = false) { onOpenAlbum(album) }
    is HomeItem.PlaylistItem -> HeroEntry(key(), playlist.name, "", playlist.artworkUrl, playable = false) { onOpenPlaylist(playlist) }
    is HomeItem.ArtistItem -> null
}

/**
 * Large featured cards, paged sideways. A wide window shows several side by side; the arrows and
 * a trackpad or Shift + wheel move through them.
 */
@Composable
private fun HeroCarousel(entries: List<HeroEntry>, onShuffle: () -> Unit) {
    if (entries.isEmpty()) return
    val pagerState = rememberPagerState { entries.size }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val pageWidth = minOf(maxWidth, HERO_MAX_WIDTH)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 16.dp,
            ) { page ->
                val entry = entries[page]
                HeroCard(entry = entry, onShuffle = if (entry.playable) onShuffle else null)
            }
            if (pagerState.canScrollBackward) {
                PagerArrow(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    label = "Previous",
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                )
            }
            if (pagerState.canScrollForward) {
                PagerArrow(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    label = "Next",
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(entries.lastIndex)) } },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                )
            }
        }
        if (entries.size > 1) {
            PagerDots(
                count = entries.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(entry: HeroEntry, onShuffle: (() -> Unit)?) {
    val tint = rememberDominantColorState(entry.artworkUrl, fallback = HeroFallbackTint)
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT)
            .clip(shape)
            .background(SurfaceHigh)
            .clickable(onClickLabel = if (entry.playable) "Play ${entry.title}" else "Open ${entry.title}", onClick = entry.onOpen)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        if (entry.artworkUrl != null) {
            AsyncImage(
                model = entry.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrim tinted from the cover, so the title sits on a wash of the artwork's own colour.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val deep = lerp(tint.value, Color.Black, 0.55f)
                    val scrim = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.28f to Color.Transparent,
                        0.5f to deep.copy(alpha = 0.25f),
                        1f to deep.copy(alpha = 0.95f),
                    )
                    onDrawBehind { drawRect(scrim) }
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("FEATURED", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = entry.onOpen)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.playable) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = HeroInk,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (entry.playable) "Play" else "Open",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = HeroInk,
                    )
                }
                if (onShuffle != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .clickable(onClickLabel = "Shuffle", onClick = onShuffle)
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerArrow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A solid dark disc rather than glass: the arrows sit over covers that can be nearly white.
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val active = Coral
    val idle = OverlayTint.copy(alpha = 0.22f)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) active else idle, label = "dotColor")
            Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

// ---- Daily mixes -------------------------------------------------------------------------------

/** A daily mix: a collage of its covers washed in the mix's colour, and who's in it. */
@Composable
private fun MixCard(mix: DailyMix, onClick: () -> Unit) {
    val tint = MixTints[(mix.number - 1).mod(MixTints.size)]
    val covers = mix.covers
    val artists = when (mix.artists.size) {
        0 -> ""
        1 -> "${mix.artists[0]} and more"
        else -> "${mix.artists[0]}, ${mix.artists[1]} and more"
    }
    Column(
        modifier = Modifier
            .width(MIX_CARD_WIDTH)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Play Daily Mix ${mix.number}", onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(tint),
        ) {
            if (covers.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    covers.chunked(2).forEach { pair ->
                        Row(Modifier.weight(1f)) {
                            pair.forEach { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            } else if (covers.isNotEmpty()) {
                AsyncImage(
                    model = covers.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to tint.copy(alpha = 0.95f))),
            )
            Text(
                text = "Daily Mix ${mix.number}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
        Text(
            text = artists,
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---- Shelves -----------------------------------------------------------------------------------

@Composable
private fun ShelfTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun CardShelf(
    items: List<HomeItem>,
    onClick: (HomeItem) -> Unit,
    songCard: @Composable (HomeItem.SongItem) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items) { item ->
            if (item is HomeItem.SongItem) {
                songCard(item)
            } else {
                val info = item.toCardInfo()
                if (info.circular) {
                    ArtistCircle(name = info.title, artworkUrl = info.artworkUrl, onClick = { onClick(item) })
                } else {
                    AlbumCard(title = info.title, subtitle = info.subtitle, artworkUrl = info.artworkUrl, onClick = { onClick(item) })
                }
            }
        }
    }
}

/** Refresh, with its keyboard shortcut in a tooltip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RefreshButton(refreshing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TooltipArea(tooltip = { Tooltip("Refresh (F5)") }) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(GlassFill)
                .clickable(enabled = enabled && !refreshing, onClickLabel = "Refresh Home", onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Coral)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (refreshing) "Refreshing" else "Refresh",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tooltip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GlassFillStrong).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** List / Cards switch, styled like Search's filter chips. */
@Composable
private fun LayoutToggle(layout: HomeLayout, onChange: (HomeLayout) -> Unit) {
    Row(
        modifier = Modifier.clip(CircleShape).background(GlassFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LayoutOption(Icons.AutoMirrored.Filled.ViewList, "List", selected = layout == HomeLayout.LIST) {
            onChange(HomeLayout.LIST)
        }
        LayoutOption(Icons.Filled.GridView, "Cards", selected = layout == HomeLayout.CARDS) {
            onChange(HomeLayout.CARDS)
        }
    }
}

@Composable
private fun LayoutOption(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Coral else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
    }
}

private data class CardInfo(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val circular: Boolean = false,
)

private fun HomeItem.toCardInfo(): CardInfo = when (this) {
    is HomeItem.AlbumItem -> CardInfo(album.title, album.artist, album.artworkUrl)
    is HomeItem.ArtistItem -> CardInfo(artist.name, artist.subscribers.orEmpty(), artist.artworkUrl, circular = true)
    is HomeItem.PlaylistItem -> CardInfo(
        playlist.name,
        if (playlist.songs.isEmpty()) "Playlist" else "Playlist · ${playlist.songs.size} songs",
        playlist.artworkUrl,
    )
    is HomeItem.SongItem -> CardInfo(song.title, song.artist, song.artworkUrl)
}

/** Stable, type-qualified key — a song and an album can share an id across providers. */
private fun HomeItem.key(): String = when (this) {
    is HomeItem.SongItem -> "song:${song.id}"
    is HomeItem.AlbumItem -> "album:${album.id}"
    is HomeItem.ArtistItem -> "artist:${artist.id}"
    is HomeItem.PlaylistItem -> "playlist:${playlist.id}"
}

@Composable
private fun LoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyHome(onRetry: () -> Unit) {
    GlassPanel(shape = RoundedCornerShape(24.dp), tint = GlassFillStrong) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Couldn't load your home feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry", color = Coral) }
        }
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

/** "WEDNESDAY, NOVEMBER 25", in the system's language. */
private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())).uppercase(Locale.getDefault())

private const val HERO_COUNT = 5
private const val HERO_ASPECT = 1.7f
private val HERO_MAX_WIDTH = 560.dp
private val MIX_CARD_WIDTH = 172.dp
private val HeroFallbackTint = Color(0xFF15161B)
private val HeroInk = Color(0xFF111114)

/** Fixed, vivid washes for the mix cards, so each mix keeps its colour whatever the theme. */
private val MixTints = listOf(
    Color(0xFFE8115B),
    Color(0xFF7358FF),
    Color(0xFF1E8C6E),
    Color(0xFFC2560A),
)
