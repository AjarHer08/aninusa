package id.aninusa.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.aninusa.app.data.AniListApi
import id.aninusa.app.data.Anime
import id.aninusa.app.data.LocalStore
import id.aninusa.app.data.Manga
import id.aninusa.app.data.MangaDexApi
import id.aninusa.app.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val tab: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val anime: List<Anime> = emptyList(),
    val manga: List<Manga> = emptyList(),
    val searched: Boolean = false,
    val fallbackLanguage: Boolean = false
)

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        job?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(
                anime = emptyList(),
                manga = emptyList(),
                searched = false,
                loading = false,
                error = null,
                fallbackLanguage = false
            )
            return
        }
        job = viewModelScope.launch {
            delay(450)
            execute()
        }
    }

    fun setTab(index: Int) {
        if (index == _state.value.tab) return
        _state.value = _state.value.copy(tab = index, anime = emptyList(), manga = emptyList(), searched = false)
        if (_state.value.query.isNotBlank()) {
            job?.cancel()
            job = viewModelScope.launch { execute() }
        }
    }

    fun submit() {
        job?.cancel()
        job = viewModelScope.launch { execute() }
    }

    private suspend fun execute() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        val tab = _state.value.tab
        _state.value = _state.value.copy(loading = true, error = null)

        if (tab == 0) {
            val result = runCatching { AniListApi.search(query) }
            _state.value = _state.value.copy(
                loading = false,
                anime = result.getOrElse { emptyList() },
                manga = emptyList(),
                searched = true,
                fallbackLanguage = false,
                error = result.exceptionOrNull()?.toUserMessage()
            )
        } else {
            val language = LocalStore.get().data.value.prefs.mangaLanguage
            val result = runCatching { MangaDexApi.search(query, language) }
            var list = result.getOrElse { emptyList() }
            var fallback = false
            if (list.isEmpty() && result.isSuccess) {
                val anyLanguage = runCatching { MangaDexApi.search(query, "") }.getOrElse { emptyList() }
                if (anyLanguage.isNotEmpty()) {
                    list = anyLanguage
                    fallback = true
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                manga = list,
                anime = emptyList(),
                searched = true,
                fallbackLanguage = fallback,
                error = result.exceptionOrNull()?.toUserMessage()
            )
        }
    }
}
