package id.aninusa.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.aninusa.app.data.Favorite
import id.aninusa.app.data.LocalStore
import id.aninusa.app.data.ReadingProgress
import id.aninusa.app.ui.CoverImage
import id.aninusa.app.ui.EmptyView
import id.aninusa.app.ui.Pill
import id.aninusa.app.ui.SegmentedTabs

@Composable
fun LibraryScreen(
    onOpenAnime: (Int) -> Unit,
    onOpenManga: (String) -> Unit,
    onContinueReading: (ReadingProgress) -> Unit
) {
    val userData by LocalStore.get().data.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Koleksi",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Favorit dan lanjutan bacaanmu tersimpan di perangkat ini",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SegmentedTabs(
            options = listOf("Favorit", "Riwayat Baca"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            if (userData.favorites.isEmpty()) {
                EmptyView(
                    title = "Belum ada favorit",
                    subtitle = "Tekan ikon hati di halaman detail anime atau komik untuk menyimpannya di sini."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(userData.favorites, key = { "${it.kind}-${it.id}" }) { favorite ->
                        FavoriteCard(favorite) {
                            if (favorite.kind == "ANIME") {
                                favorite.id.toIntOrNull()?.let(onOpenAnime)
                            } else {
                                onOpenManga(favorite.id)
                            }
                        }
                    }
                }
            }
        } else {
            if (userData.progress.isEmpty()) {
                EmptyView(
                    title = "Belum ada riwayat baca",
                    subtitle = "Mulai baca chapter apa pun, dan progresnya akan otomatis tercatat di sini."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(userData.progress, key = { it.mangaId }) { progress ->
                        ProgressRow(progress) { onContinueReading(progress) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(favorite: Favorite, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            CoverImage(url = favorite.cover, modifier = Modifier.fillMaxSize())
            Box(Modifier.padding(6.dp)) {
                Pill(
                    text = if (favorite.kind == "ANIME") "Anime" else "Komik",
                    container = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = favorite.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
        favorite.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressRow(progress: ReadingProgress, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = progress.cover,
            modifier = Modifier.width(52.dp).height(72.dp),
            radius = 10.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = progress.mangaTitle.ifBlank { "Judul komik" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = progress.chapterLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (progress.total > 0) {
                    "Halaman ${progress.page + 1} dari ${progress.total}"
                } else "Lanjutkan membaca",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
