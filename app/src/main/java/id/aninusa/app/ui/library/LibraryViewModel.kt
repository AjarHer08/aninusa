package id.aninusa.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.aninusa.app.data.MangaDexApi
import id.aninusa.app.data.ReadingProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Chapter terbaru yang belum dibaca untuk satu judul di riwayat baca. */
data class ChapterUpdate(
    val mangaId: String,
    val latestLabel: String
)

data class UpdateState(
    val checking: Boolean = false,
    val updates: Map<String, ChapterUpdate> = emptyMap(),
    val message: String? = null
)

/**
 * Memeriksa satu per satu judul di riwayat baca: kalau chapter terbaru di MangaDex
 * berbeda dari chapter yang terakhir dibuka, judul itu ditandai punya update.
 */
class LibraryViewModel : ViewModel() {

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkUpdates(items: List<ReadingProgress>) {
        if (_state.value.checking) return
        if (items.isEmpty()) {
            _state.value = UpdateState(message = "Belum ada riwayat baca untuk diperiksa.")
            return
        }
        _state.value = _state.value.copy(checking = true, message = null)
        viewModelScope.launch {
            val found = LinkedHashMap<String, ChapterUpdate>()
            var failed = 0
            val checked = items.take(40)
            checked.forEachIndexed { index, item ->
                val result = runCatching {
                    MangaDexApi.chapters(item.mangaId, item.lang.ifBlank { "id" }, fresh = true)
                }
                val chapters = result.getOrNull()
                if (chapters.isNullOrEmpty()) {
                    if (result.isFailure) failed++
                } else {
                    val latest = chapters.firstOrNull()
                    if (latest != null && latest.id != item.chapterId) {
                        found[item.mangaId] = ChapterUpdate(item.mangaId, latest.label)
                    }
                }
                if (index < checked.lastIndex) delay(150)
            }
            _state.value = UpdateState(
                checking = false,
                updates = found,
                message = when {
                    found.isNotEmpty() -> "${found.size} judul punya chapter baru."
                    failed > 0 -> "Sebagian judul gagal diperiksa. Coba lagi nanti."
                    else -> "Semua judul sudah di chapter terbaru."
                }
            )
        }
    }
}
