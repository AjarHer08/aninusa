package id.aninusa.app.ui.manga

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import id.aninusa.app.data.ChapterDownloads
import id.aninusa.app.data.Favorite
import id.aninusa.app.data.LocalStore
import id.aninusa.app.data.Manga
import id.aninusa.app.data.MangaChapter
import id.aninusa.app.data.MangaDexApi
import id.aninusa.app.data.toUserMessage
import id.aninusa.app.ui.BackBar
import id.aninusa.app.ui.CoverImage
import id.aninusa.app.ui.DividerLine
import id.aninusa.app.ui.ErrorView
import id.aninusa.app.ui.GenreRow
import id.aninusa.app.ui.LoadingView
import id.aninusa.app.ui.Pill
import id.aninusa.app.ui.SelectablePill
import id.aninusa.app.ui.htmlToText
import id.aninusa.app.ui.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MangaDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val manga: Manga? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val chaptersLoading: Boolean = false,
    val chaptersError: String? = null,
    val selectedLang: String = "id",
    val notice: String? = null,
    val toast: String? = null
)

class MangaDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(MangaDetailUiState())
    val state: StateFlow<MangaDetailUiState> = _state.asStateFlow()

    private var mangaId: String = ""

    fun load(id: String) {
        mangaId = id
        _state.value = MangaDetailUiState(loading = true)
        viewModelScope.launch {
            val result = runCatching { MangaDexApi.detail(id) }
            val manga = result.getOrNull()
            if (manga == null) {
                _state.value = MangaDetailUiState(
                    loading = false,
                    error = result.exceptionOrNull()?.toUserMessage() ?: "Judul tidak ditemukan di MangaDex."
                )
                return@launch
            }
            val preferred = LocalStore.get().data.value.prefs.mangaLanguage
            val lang = chooseLanguage(manga, preferred)
            _state.value = MangaDetailUiState(loading = false, manga = manga, selectedLang = lang)
            loadChapters(id, lang)
        }
    }

    private fun chooseLanguage(manga: Manga, preferred: String): String {
        val languages = manga.languages
        return when {
            languages.contains(preferred) -> preferred
            languages.contains("id") -> "id"
            languages.contains("en") -> "en"
            languages.isNotEmpty() -> languages.first()
            else -> preferred
        }
    }

    fun selectLanguage(lang: String) {
        if (lang == _state.value.selectedLang) return
        _state.value = _state.value.copy(selectedLang = lang)
        loadChapters(mangaId, lang)
    }

    fun retry() {
        if (mangaId.isNotBlank()) load(mangaId)
    }

    fun retryChapters() {
        if (mangaId.isNotBlank()) loadChapters(mangaId, _state.value.selectedLang)
    }

    /** Mengunduh satu chapter untuk dibaca offline; tidak ada unduhan massal. */
    fun downloadChapter(manga: Manga, chapter: MangaChapter) {
        viewModelScope.launch {
            val result = ChapterDownloads.get().download(
                manga = manga,
                chapter = chapter,
                dataSaver = LocalStore.get().data.value.prefs.dataSaver
            )
            _state.value = _state.value.copy(
                toast = result.fold(
                    onSuccess = { "Tersimpan offline · ${it.chapterLabel}" },
                    onFailure = { error ->
                        "Gagal mengunduh ${chapter.label}: ${error.toUserMessage()}"
                    }
                )
            )
        }
    }

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun loadChapters(id: String, lang: String) {
        _state.value = _state.value.copy(
            chaptersLoading = true,
            chapters = emptyList(),
            chaptersError = null,
            notice = null
        )
        viewModelScope.launch {
            val result = runCatching { MangaDexApi.chapters(id, lang) }
            val chapters = result.getOrNull().orEmpty()
            val notice = when {
                result.isFailure -> null
                chapters.isEmpty() && lang == "id" ->
                    "Belum ada chapter bahasa Indonesia di MangaDex untuk judul ini."
                chapters.isEmpty() ->
                    "Belum ada chapter dalam bahasa ${MangaDexApi.languageName(lang)}."
                lang != "id" ->
                    "Menampilkan chapter ${MangaDexApi.languageName(lang)} karena chapter bahasa Indonesia belum tersedia."
                else -> null
            }
            _state.value = _state.value.copy(
                chaptersLoading = false,
                chapters = chapters,
                notice = notice,
                chaptersError = result.exceptionOrNull()?.toUserMessage()
            )
        }
    }
}

@Composable
fun MangaDetailScreen(
    mangaId: String,
    onBack: () -> Unit,
    onRead: (chapterId: String, lang: String, startPage: Int, dataSaver: Boolean) -> Unit
) {
    val viewModel: MangaDetailViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val userData by LocalStore.get().data.collectAsState()
    val downloads by ChapterDownloads.get().entries.collectAsState()
    val downloadProgress by ChapterDownloads.get().progress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(mangaId) { viewModel.load(mangaId) }

    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearToast()
    }

    val manga = state.manga
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingView("Memuat detail komik…")
        }

        manga == null -> Column(Modifier.fillMaxSize()) {
            BackBar("Detail Komik", onBack)
            ErrorView(state.error ?: "Judul tidak ditemukan.") { viewModel.retry() }
        }

        else -> {
            val progress = userData.progress.firstOrNull { it.mangaId == manga.id }
            val isFavorite = userData.favorites.any { it.kind == "MANGA" && it.id == manga.id }
            val dataSaver = userData.prefs.dataSaver

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    BackBar(
                        title = manga.kind,
                        onBack = onBack,
                        trailing = {
                            IconButton(onClick = {
                                val added = LocalStore.get().toggleFavorite(
                                    Favorite(
                                        id = manga.id,
                                        title = manga.title,
                                        cover = manga.coverUrl,
                                        kind = "MANGA",
                                        subtitle = "${manga.kind} · ${manga.status}"
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    if (added) "Ditambahkan ke koleksi" else "Dihapus dari koleksi",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Koleksi",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    )
                }

                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        CoverImage(
                            url = manga.coverUrl,
                            modifier = Modifier.width(104.dp).height(148.dp),
                            radius = 14.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 26.sp
                            )
                            manga.altTitle?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Pill(
                                    text = manga.kind,
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Pill(manga.status)
                                manga.year?.let { Pill(it.toString()) }
                            }
                            manga.author?.let { author ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (progress != null) {
                            Button(onClick = {
                                onRead(progress.chapterId, progress.lang, progress.page, dataSaver)
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lanjut baca")
                            }
                        }
                        OutlinedButton(onClick = {
                            // chapter diurutkan dari terbaru, jadi chapter paling awal ada di akhir
                            val first = state.chapters.lastOrNull { it.externalUrl.isNullOrEmpty() }
                                ?: state.chapters.lastOrNull()
                            if (first != null) {
                                if (first.externalUrl != null) {
                                    openUrl(context, first.externalUrl)
                                } else {
                                    onRead(first.id, first.lang, 0, dataSaver)
                                }
                            } else {
                                Toast.makeText(context, "Chapter belum tersedia", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text(if (progress != null) "Mulai dari awal" else "Mulai baca")
                        }
                    }
                }

                if (manga.tags.isNotEmpty()) {
                    item { GenreRow(manga.tags) }
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
                            text = htmlToText(manga.description),
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
                    }
                }

                if (manga.languages.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = "Bahasa chapter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                manga.languages.forEach { code ->
                                    SelectablePill(
                                        text = MangaDexApi.languageName(code),
                                        selected = code == state.selectedLang,
                                        onClick = { viewModel.selectLanguage(code) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    DividerLine()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Daftar Chapter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.chaptersLoading) "Memuat chapter…"
                                else "${state.chapters.size} chapter · ${MangaDexApi.languageName(state.selectedLang)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val offlineCount = downloads.count { it.mangaId == manga.id }
                            if (offlineCount > 0) {
                                Text(
                                    text = "$offlineCount chapter offline · " +
                                        ChapterDownloads.formatSize(
                                            downloads.filter { it.mangaId == manga.id }
                                                .sumOf { it.bytes }
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.retryChapters() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Muat ulang chapter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val notice = state.notice
                if (notice != null) {
                    item {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                if (state.chaptersLoading) {
                    item { LoadingView("Mengambil daftar chapter…") }
                } else if (state.chaptersError != null) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = state.chaptersError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { viewModel.retryChapters() }) { Text("Coba lagi") }
                        }
                    }
                } else if (state.chapters.isEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "Belum ada chapter yang bisa dibaca untuk bahasa ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { openUrl(context, MangaDexApi.webUrl(manga.id)) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Buka halaman MangaDex")
                            }
                        }
                    }
                } else {
                    items(state.chapters, key = { it.id }) { chapter ->
                        val startPage = if (progress?.chapterId == chapter.id) progress.page else 0
                        ChapterRow(
                            chapter = chapter,
                            isCurrent = progress?.chapterId == chapter.id,
                            downloaded = downloads.any { it.chapterId == chapter.id },
                            downloadProgress = downloadProgress[chapter.id],
                            onClick = {
                                if (!chapter.externalUrl.isNullOrBlank()) {
                                    openUrl(context, chapter.externalUrl)
                                } else {
                                    onRead(chapter.id, chapter.lang, startPage, dataSaver)
                                }
                            },
                            onDownload = { viewModel.downloadChapter(manga, chapter) },
                            onRemove = {
                                ChapterDownloads.get().remove(chapter.id)
                                Toast.makeText(
                                    context,
                                    "Unduhan ${chapter.label} dihapus",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = "Chapter dibaca langsung di aplikasi melalui MangaDex. " +
                            "Judul resmi yang hanya mengizinkan baca di platform penerbit akan membuka tautan resmi.",
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
private fun ChapterRow(
    chapter: MangaChapter,
    isCurrent: Boolean,
    downloaded: Boolean,
    downloadProgress: id.aninusa.app.data.DownloadProgress?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = chapter.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
            val secondary = listOfNotNull(chapter.title, chapter.group).joinToString(" · ")
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            chapter.date?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        if (!chapter.externalUrl.isNullOrBlank()) {
            Pill(text = "Sumber resmi")
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (chapter.pages > 0) "${chapter.pages} hlm" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                when {
                    downloadProgress != null -> Text(
                        text = "${downloadProgress.done}/${downloadProgress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Hapus unduhan ${chapter.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    else -> TextButton(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Unduh", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
