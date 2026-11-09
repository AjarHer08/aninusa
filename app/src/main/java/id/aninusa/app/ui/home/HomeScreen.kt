package id.aninusa.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import id.aninusa.app.data.Anime
import id.aninusa.app.data.Manga
import id.aninusa.app.data.MangaKind
import id.aninusa.app.data.displayTitle
import id.aninusa.app.data.formatLabel
import id.aninusa.app.data.posterUrl
import id.aninusa.app.data.yearLabel
import id.aninusa.app.ui.AnimeCard
import id.aninusa.app.ui.ErrorView
import id.aninusa.app.ui.LoadingView
import id.aninusa.app.ui.MangaCard
import id.aninusa.app.ui.MangaRow
import id.aninusa.app.ui.Pill
import id.aninusa.app.ui.SectionHeader
import id.aninusa.app.ui.SegmentedTabs
import id.aninusa.app.ui.SelectablePill

@Composable
fun HomeScreen(
    onOpenAnime: (Int) -> Unit,
    onOpenManga: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item { HomeHeader(onRefresh = { viewModel.load() }, onOpenSearch = onOpenSearch) }
        item {
            SegmentedTabs(
                options = listOf("Anime", "Manga / Manhwa / Manhua"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        val error = state.error
        when {
            state.loading && state.trending.isEmpty() -> item { LoadingView("Menyiapkan katalog…") }
            error != null -> item { ErrorView(error) { viewModel.load() } }
            tab == 0 -> animeSections(state, onOpenAnime)
            else -> mangaSections(
                state = state,
                onOpenManga = onOpenManga,
                onKindSelected = viewModel::setKind
            )
        }
    }
}

@Composable
private fun HomeHeader(onRefresh: () -> Unit, onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "AniNusa",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Anime & Komik · Sub Indo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = "Cari", tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Muat ulang", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private fun LazyListScope.animeSections(state: HomeUiState, onOpenAnime: (Int) -> Unit) {
    if (state.trending.isNotEmpty()) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.trending.take(8), key = { "hero-${it.id}" }) { anime ->
                    HeroCard(anime) { onOpenAnime(anime.id) }
                }
            }
        }
    }

    animeRow(
        title = "Sedang Tren",
        subtitle = "Paling banyak ditonton minggu ini",
        anime = state.trending,
        onOpenAnime = onOpenAnime
    )
    animeRow(
        title = "Terpopuler Sepanjang Masa",
        subtitle = "Katalog AniList",
        anime = state.popularAnime,
        onOpenAnime = onOpenAnime
    )
    animeRow(
        title = "Musim Ini",
        subtitle = "Rilis terbaru yang sedang tayang",
        anime = state.season,
        onOpenAnime = onOpenAnime
    )

    item {
        Text(
            text = "Tonton anime dengan subtitle Indonesia di platform resmi seperti Bstation, iQIYI, dan Netflix. " +
                "Buka detail anime untuk melihat tautannya.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

private fun LazyListScope.animeRow(
    title: String,
    subtitle: String,
    anime: List<Anime>,
    onOpenAnime: (Int) -> Unit
) {
    if (anime.isEmpty()) return
    item { SectionHeader(title = title, subtitle = subtitle) }
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(anime, key = { it.id }) { item ->
                AnimeCard(anime = item, modifier = Modifier.width(126.dp)) { onOpenAnime(item.id) }
            }
        }
    }
}

private fun LazyListScope.mangaSections(
    state: HomeUiState,
    onOpenManga: (String) -> Unit,
    onKindSelected: (MangaKind) -> Unit
) {
    item {
        SectionHeader(
            title = "Update Chapter Indonesia",
            subtitle = "Chapter berbahasa Indonesia terbaru dari MangaDex"
        )
    }
    if (state.mangaLatest.isEmpty()) {
        item { LoadingView("Memuat chapter terbaru…") }
    } else {
        items(state.mangaLatest.take(12), key = { it.id }) { manga ->
            MangaRow(manga) { onOpenManga(manga.id) }
        }
    }

    item { SectionHeader(title = "Populer di Indonesia", subtitle = "Berdasarkan jumlah pembaca") }
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MangaKind.values().forEach { kind ->
                SelectablePill(
                    text = kind.label,
                    selected = kind == state.mangaKind,
                    onClick = { onKindSelected(kind) }
                )
            }
        }
    }
    item {
        when {
            state.kindLoading -> LoadingView("Memuat daftar ${state.mangaKind.label}…")
            state.mangaPopular.isEmpty() -> Text(
                text = "Belum ada data untuk kategori ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(state.mangaPopular, key = { it.id }) { manga ->
                    MangaCard(manga = manga, modifier = Modifier.width(126.dp)) {
                        onOpenManga(manga.id)
                    }
                }
            }
        }
    }

    item {
        Text(
            text = "Semua chapter dibaca langsung di dalam aplikasi dan tersedia dalam bahasa Indonesia. " +
                "Judul resmi yang tidak mengizinkan hosting chapter hanya menampilkan tautan ke sumber resmi.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun HeroCard(anime: Anime, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(300.dp)
            .height(172.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val image = anime.bannerImage ?: anime.posterUrl
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x22000000), Color(0x99000000), Color(0xF20A0A12))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Pill(
                text = "Sedang Tren",
                container = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = anime.displayTitle,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "★ ${anime.averageScore ?: "—"}  ·  ${formatLabel(anime.format)}  ·  ${anime.yearLabel}",
                color = Color(0xFFD9D5EE),
                fontSize = 11.sp
            )
        }
    }
}
