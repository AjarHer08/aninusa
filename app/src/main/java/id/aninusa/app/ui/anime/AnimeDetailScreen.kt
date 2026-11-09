package id.aninusa.app.ui.anime

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import id.aninusa.app.data.AniListApi
import id.aninusa.app.data.Anime
import id.aninusa.app.data.AnimeHistory
import id.aninusa.app.data.Favorite
import id.aninusa.app.data.LocalStore
import id.aninusa.app.data.displayTitle
import id.aninusa.app.data.formatLabel
import id.aninusa.app.data.posterUrl
import id.aninusa.app.data.statusLabel
import id.aninusa.app.data.studioName
import id.aninusa.app.data.toUserMessage
import id.aninusa.app.data.yearLabel
import id.aninusa.app.ui.AnimeCard
import id.aninusa.app.ui.BackBar
import id.aninusa.app.ui.CoverImage
import id.aninusa.app.ui.ErrorView
import id.aninusa.app.ui.GenreRow
import id.aninusa.app.ui.LoadingView
import id.aninusa.app.ui.Pill
import id.aninusa.app.ui.SectionHeader
import id.aninusa.app.ui.htmlToText
import id.aninusa.app.ui.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnimeDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val anime: Anime? = null
)

class AnimeDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(AnimeDetailUiState())
    val state: StateFlow<AnimeDetailUiState> = _state.asStateFlow()

    fun load(id: Int) {
        if (id <= 0) {
            _state.value = AnimeDetailUiState(loading = false, error = "ID anime tidak valid.")
            return
        }
        _state.value = AnimeDetailUiState(loading = true)
        viewModelScope.launch {
            val result = runCatching { AniListApi.detail(id) }
            val anime = result.getOrNull()
            _state.value = AnimeDetailUiState(
                loading = false,
                anime = anime,
                error = result.exceptionOrNull()?.toUserMessage()
            )
            if (anime != null) {
                LocalStore.get().rememberAnime(
                    AnimeHistory(
                        id = anime.id,
                        title = anime.displayTitle,
                        cover = anime.posterUrl,
                        meta = "${formatLabel(anime.format)} · ${anime.yearLabel}"
                    )
                )
            }
        }
    }
}

private fun countdownLabel(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "$days hari $hours jam lagi"
        hours > 0 -> "$hours jam $minutes menit lagi"
        else -> "${minutes.coerceAtLeast(1)} menit lagi"
    }
}

@Composable
fun AnimeDetailScreen(id: Int, onBack: () -> Unit, onOpenAnime: (Int) -> Unit) {
    val viewModel: AnimeDetailViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val userData by LocalStore.get().data.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.load(id) }

    val anime = state.anime
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingView("Memuat detail anime…")
        }

        anime == null -> Column(Modifier.fillMaxSize()) {
            BackBar("Detail Anime", onBack)
            ErrorView(state.error ?: "Anime tidak ditemukan.") { viewModel.load(id) }
        }

        else -> {
            val isFavorite = userData.favorites.any { it.kind == "ANIME" && it.id == anime.id.toString() }
            val title = anime.displayTitle
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    DetailHeader(
                        anime = anime,
                        isFavorite = isFavorite,
                        onBack = onBack,
                        onToggleFavorite = {
                            val added = LocalStore.get().toggleFavorite(
                                Favorite(
                                    id = anime.id.toString(),
                                    title = title,
                                    cover = anime.posterUrl,
                                    kind = "ANIME",
                                    subtitle = "${formatLabel(anime.format)} · ${anime.yearLabel}"
                                )
                            )
                            Toast.makeText(
                                context,
                                if (added) "Ditambahkan ke koleksi" else "Dihapus dari koleksi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Pill(
                            text = "★ ${anime.averageScore ?: "—"} / 100",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Pill("${formatLabel(anime.format)} · ${anime.episodes ?: "?"} ep")
                        Pill(statusLabel(anime.status))
                        Pill(anime.yearLabel)
                        anime.duration?.let { Pill("$it menit/ep") }
                    }
                }

                if (anime.genres.isNotEmpty()) {
                    item { GenreRow(anime.genres.take(8)) }
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Sinopsis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            text = htmlToText(anime.description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = if (expanded) "Tampilkan lebih sedikit" else "Baca selengkapnya",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expanded = !expanded }
                        )
                        anime.studioName?.let { studio ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Studio: $studio",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        anime.nextAiringEpisode?.let { airing ->
                            val seconds = airing.timeUntilAiring ?: 0L
                            if (seconds > 0 && airing.episode != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Episode ${airing.episode} tayang ${countdownLabel(seconds)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                item { WatchSection(anime) }

                val recommendations = anime.recommendations?.nodes
                    ?.mapNotNull { it.mediaRecommendation }
                    ?.filter { it.type == "ANIME" && it.id != anime.id }
                    .orEmpty()
                if (recommendations.isNotEmpty()) {
                    item { SectionHeader("Rekomendasi Serupa") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recommendations, key = { it.id }) { item ->
                                AnimeCard(anime = item, modifier = Modifier.width(126.dp)) {
                                    onOpenAnime(item.id)
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Data anime dari AniList. AniNusa tidak menyimpan atau menayangkan video. " +
                            "Subtitle Indonesia tersedia di platform streaming resmi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    anime: Anime,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .height(310.dp)
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
                        0f to Color(0x33000000),
                        0.55f to background.copy(alpha = 0.75f),
                        1f to background
                    )
                )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleAction(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") },
                onClick = onBack
            )
            CircleAction(
                icon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Koleksi",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                    )
                },
                onClick = onToggleFavorite
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            CoverImage(
                url = anime.posterUrl,
                modifier = Modifier.width(96.dp).height(136.dp),
                radius = 14.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = anime.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 25.sp
                )
                Spacer(Modifier.height(4.dp))
                val alt = anime.title?.romaji?.takeIf { !it.equals(anime.displayTitle, ignoreCase = true) }
                if (alt != null) {
                    Text(
                        text = alt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    text = "${formatLabel(anime.format)} · ${statusLabel(anime.status)} · ${anime.yearLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CircleAction(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xB3000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun WatchSection(anime: Anime) {
    val context = LocalContext.current
    val title = anime.title?.english ?: anime.title?.romaji ?: anime.displayTitle
    val encoded = Uri.encode(title)
    val trailer = anime.trailer
    val trailerUrl = if (trailer?.site == "youtube" && trailer.id != null) {
        "https://www.youtube.com/watch?v=${trailer.id}"
    } else {
        anime.siteUrl
    }
    val providers = remember(title) {
        listOf(
            "Bstation" to "https://www.bstation.tv/search?keyword=$encoded",
            "iQIYI" to "https://www.iq.com/search?query=$encoded",
            "Netflix" to "https://www.netflix.com/search?q=$encoded"
        )
    }
    val streamingSites = remember(anime.streamingEpisodes) {
        anime.streamingEpisodes
            .filter { !it.url.isNullOrBlank() }
            .distinctBy { it.site }
            .take(4)
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "Tonton Anime",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tautan menuju platform resmi dengan subtitle Indonesia. Pilih salah satu:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (trailerUrl != null) {
                Button(onClick = { openUrl(context, trailerUrl) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (trailer != null) "Trailer" else "Situs Resmi")
                }
            }
            providers.forEach { (label, url) ->
                OutlinedButton(onClick = { openUrl(context, url) }) { Text(label) }
            }
        }
        if (streamingSites.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tersedia di platform streaming resmi:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                streamingSites.forEach { episode ->
                    OutlinedButton(onClick = { openUrl(context, episode.url) }) {
                        Text(episode.site ?: "Streaming")
                    }
                }
            }
        }
    }
}
