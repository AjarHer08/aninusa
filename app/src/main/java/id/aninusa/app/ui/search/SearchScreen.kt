package id.aninusa.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.aninusa.app.ui.AnimeCard
import id.aninusa.app.ui.EmptyView
import id.aninusa.app.ui.ErrorView
import id.aninusa.app.ui.LoadingView
import id.aninusa.app.ui.MangaCard
import id.aninusa.app.ui.SegmentedTabs

@Composable
fun SearchScreen(onOpenAnime: (Int) -> Unit, onOpenManga: (String) -> Unit) {
    val viewModel: SearchViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Cari",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Temukan anime, manga, manhwa, dan manhua",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Ketik judul…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submit() })
        )

        SegmentedTabs(
            options = listOf("Anime", "Manga / Manhwa / Manhua"),
            selectedIndex = state.tab,
            onSelect = viewModel::setTab,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        val error = state.error
        when {
            state.loading -> LoadingView("Mencari…")
            error != null -> ErrorView(error) { viewModel.submit() }
            !state.searched -> EmptyView(
                title = "Mulai mencari",
                subtitle = "Ketik minimal beberapa huruf judul. Hasil manga diutamakan yang punya chapter bahasa Indonesia."
            )
            state.tab == 0 && state.anime.isEmpty() -> EmptyView(
                title = "Anime tidak ditemukan",
                subtitle = "Coba kata kunci lain, misalnya judul versi romaji atau Inggris."
            )
            state.tab == 1 && state.manga.isEmpty() -> EmptyView(
                title = "Tidak ada hasil",
                subtitle = "Coba kata kunci lain atau periksa ejaan judul."
            )
            else -> {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = if (state.tab == 0) {
                            "${state.anime.size} anime ditemukan"
                        } else {
                            "${state.manga.size} judul ditemukan" +
                                if (state.fallbackLanguage) " · chapter Indonesia belum tersedia untuk judul ini" else ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.tab == 0) {
                            items(state.anime, key = { it.id }) { anime ->
                                AnimeCard(anime = anime, modifier = Modifier.fillMaxWidth()) {
                                    onOpenAnime(anime.id)
                                }
                            }
                        } else {
                            items(state.manga, key = { it.id }) { manga ->
                                MangaCard(manga = manga, modifier = Modifier.fillMaxWidth()) {
                                    onOpenManga(manga.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
