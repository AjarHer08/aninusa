package id.aninusa.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import id.aninusa.app.data.LocalStore
import id.aninusa.app.data.MangaChapter
import id.aninusa.app.data.MangaDexApi
import id.aninusa.app.data.ReadingProgress
import id.aninusa.app.data.toUserMessage
import id.aninusa.app.ui.LoadingView
import id.aninusa.app.ui.openUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ReaderUiState(
    val loading: Boolean = true,
    val pagesLoading: Boolean = false,
    val error: String? = null,
    val externalUrl: String? = null,
    val pages: List<String> = emptyList(),
    val mangaId: String = "",
    val mangaTitle: String = "",
    val coverUrl: String? = null,
    val chapterId: String = "",
    val chapterLabel: String = "",
    val chapterTitle: String? = null,
    val lang: String = "id",
    val prevChapter: MangaChapter? = null,
    val nextChapter: MangaChapter? = null,
    val page: Int = 0,
    val total: Int = 0,
    val dataSaver: Boolean = false,
    val vertical: Boolean = true
)

class ReaderViewModel : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    fun load(
        mangaId: String,
        chapterId: String,
        lang: String,
        startPage: Int,
        dataSaver: Boolean,
        vertical: Boolean
    ) {
        _state.value = ReaderUiState(
            loading = true,
            mangaId = mangaId,
            chapterId = chapterId,
            lang = lang,
            page = startPage,
            dataSaver = dataSaver,
            vertical = vertical
        )
        viewModelScope.launch {
            val detail = runCatching { MangaDexApi.detail(mangaId) }.getOrNull()
            var chapters = runCatching { MangaDexApi.chapters(mangaId, lang) }.getOrElse { emptyList() }
            var index = chapters.indexOfFirst { it.id == chapterId }
            if (index < 0 && lang != "id") {
                chapters = runCatching { MangaDexApi.chapters(mangaId, "id") }.getOrElse { emptyList() }
                index = chapters.indexOfFirst { it.id == chapterId }
            }
            val chapter = chapters.getOrNull(index)
            val pagesResult = runCatching { MangaDexApi.chapterPages(chapterId, dataSaver) }
            val pages = pagesResult.getOrElse { emptyList() }

            val error = when {
                pages.isNotEmpty() -> null
                !chapter?.externalUrl.isNullOrBlank() -> "Chapter ini hanya tersedia di sumber resmi penerbit."
                else -> pagesResult.exceptionOrNull()?.toUserMessage()
                    ?: "Gambar chapter tidak bisa dimuat. Coba chapter lain atau periksa koneksi."
            }

            _state.value = _state.value.copy(
                loading = false,
                mangaTitle = detail?.title.orEmpty(),
                coverUrl = detail?.coverUrl,
                chapterLabel = chapter?.label ?: "Chapter",
                chapterTitle = chapter?.title,
                externalUrl = chapter?.externalUrl,
                pages = pages,
                total = pages.size,
                page = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                prevChapter = if (index >= 0) chapters.getOrNull(index + 1) else null,
                nextChapter = if (index > 0) chapters.getOrNull(index - 1) else null,
                error = error
            )
            if (pages.isNotEmpty()) persistProgress()
        }
    }

    fun retry() {
        val current = _state.value
        if (current.mangaId.isBlank() || current.chapterId.isBlank()) return
        load(current.mangaId, current.chapterId, current.lang, current.page, current.dataSaver, current.vertical)
    }

    fun openChapter(chapter: MangaChapter) {
        if (!chapter.externalUrl.isNullOrBlank()) return
        val current = _state.value
        load(current.mangaId, chapter.id, chapter.lang, 0, current.dataSaver, current.vertical)
    }

    fun setVertical(value: Boolean) {
        if (value == _state.value.vertical) return
        _state.value = _state.value.copy(vertical = value)
    }

    fun setDataSaver(value: Boolean) {
        if (value == _state.value.dataSaver) return
        val current = _state.value
        _state.value = current.copy(dataSaver = value, pagesLoading = true)
        viewModelScope.launch {
            val result = runCatching { MangaDexApi.chapterPages(current.chapterId, value) }
            val pages = result.getOrElse { current.pages }
            _state.value = _state.value.copy(
                pagesLoading = false,
                pages = pages,
                total = pages.size,
                page = _state.value.page.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                error = if (pages.isEmpty()) {
                    result.exceptionOrNull()?.toUserMessage() ?: "Gagal memuat ulang halaman."
                } else null
            )
        }
    }

    fun onPageChanged(index: Int) {
        if (index == _state.value.page || index < 0) return
        _state.value = _state.value.copy(page = index)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            persistProgress()
        }
    }

    private fun persistProgress() {
        val current = _state.value
        if (current.chapterId.isBlank() || current.total == 0) return
        LocalStore.get().saveProgress(
            ReadingProgress(
                mangaId = current.mangaId,
                mangaTitle = current.mangaTitle,
                cover = current.coverUrl,
                chapterId = current.chapterId,
                chapterLabel = current.chapterLabel,
                page = current.page,
                total = current.total,
                lang = current.lang
            )
        )
    }

    override fun onCleared() {
        saveJob?.cancel()
        persistProgress()
        super.onCleared()
    }
}

@Composable
fun ReaderScreen(
    mangaId: String,
    chapterId: String,
    lang: String,
    startPage: Int,
    dataSaver: Boolean,
    vertical: Boolean,
    onBack: () -> Unit
) {
    val viewModel: ReaderViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(mangaId, chapterId) {
        viewModel.load(mangaId, chapterId, lang, startPage, dataSaver, vertical)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ReaderTopBar(
            state = state,
            onBack = onBack,
            onToggleMode = { viewModel.setVertical(!state.vertical) },
            onToggleSaver = { viewModel.setDataSaver(!state.dataSaver) }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingView("Membuka chapter…")
                }

                state.error != null -> ReaderError(
                    message = state.error ?: "",
                    externalUrl = state.externalUrl,
                    onRetry = { viewModel.retry() }
                )

                state.vertical -> VerticalReader(state, viewModel)
                else -> HorizontalReader(state, viewModel)
            }
            if (state.pagesLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        ReaderBottomBar(
            state = state,
            onPrev = { state.prevChapter?.let(viewModel::openChapter) },
            onNext = { state.nextChapter?.let(viewModel::openChapter) }
        )
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleSaver: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.chapterLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.mangaTitle.ifBlank { "Membaca chapter" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onToggleMode) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (state.vertical) "Gulir" else "Halaman", fontSize = 12.sp)
            }
            TextButton(onClick = onToggleSaver) {
                Text(if (state.dataSaver) "Hemat" else "HD", fontSize = 12.sp)
            }
        }
        val fraction = if (state.total > 0) (state.page + 1).toFloat() / state.total else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun VerticalReader(state: ReaderUiState, viewModel: ReaderViewModel) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.page)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> viewModel.onPageChanged(index) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        items(state.pages, key = { it }) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
            )
        }
        item {
            Text(
                text = "Akhir chapter · ${state.chapterLabel}",
                color = Color(0xFF8C88A8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
    }
}

@Composable
private fun HorizontalReader(state: ReaderUiState, viewModel: ReaderViewModel) {
    val pagerState = rememberPagerState(initialPage = 0) { state.pages.size }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(state.page, state.pages.size) {
        if (state.pages.isNotEmpty() && pagerState.currentPage != state.page) {
            pagerState.scrollToPage(state.page.coerceIn(0, state.pages.size - 1))
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index -> viewModel.onPageChanged(index) }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !zoomed,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) { index ->
        ZoomableImage(
            url = state.pages.getOrNull(index).orEmpty(),
            onZoomChanged = { zoomed = it }
        )
    }
}

@Composable
private fun ZoomableImage(url: String, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    offset = if (nextScale > 1.01f) offset + pan else Offset.Zero
                    onZoomChanged(nextScale > 1.01f)
                }
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onPrev, enabled = state.prevChapter != null) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Sebelumnya", fontSize = 12.sp)
        }
        Text(
            text = if (state.total > 0) "${state.page + 1} / ${state.total}" else "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onNext, enabled = state.nextChapter != null) {
            Text("Berikutnya", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun ReaderError(message: String, externalUrl: String?, onRetry: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Chapter tidak bisa dibuka",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Text("Coba lagi")
            }
            if (!externalUrl.isNullOrBlank()) {
                Button(onClick = { openUrl(context, externalUrl) }) {
                    Text("Buka sumber resmi")
                }
            }
        }
    }
}
