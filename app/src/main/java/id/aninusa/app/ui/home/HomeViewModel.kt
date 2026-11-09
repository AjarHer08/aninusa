package id.aninusa.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.aninusa.app.data.AniListApi
import id.aninusa.app.data.Anime
import id.aninusa.app.data.Manga
import id.aninusa.app.data.MangaDexApi
import id.aninusa.app.data.MangaKind
import id.aninusa.app.data.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val trending: List<Anime> = emptyList(),
    val season: List<Anime> = emptyList(),
    val popularAnime: List<Anime> = emptyList(),
    val mangaLatest: List<Manga> = emptyList(),
    val mangaPopular: List<Manga> = emptyList(),
    val mangaKind: MangaKind = MangaKind.ALL,
    val kindLoading: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loaded = false

    fun loadIfNeeded() {
        if (!loaded) {
            loaded = true
            load()
        }
    }

    fun load() {
        _state.value = _state.value.copy(loading = _state.value.trending.isEmpty(), error = null)
        viewModelScope.launch {
            supervisorScope {
                val trending = async { runCatching { AniListApi.trending() } }
                val season = async { runCatching { AniListApi.thisSeason() } }
                val popularAnime = async { runCatching { AniListApi.popular() } }
                val latest = async { runCatching { MangaDexApi.latestIndonesian() } }
                val popularManga = async {
                    runCatching { MangaDexApi.popular(lang = "id", originalLanguage = null) }
                }

                val trendingResult = trending.await()
                val seasonResult = season.await()
                val popularAnimeResult = popularAnime.await()
                val latestResult = latest.await()
                val popularMangaResult = popularManga.await()

                val failures = listOf(
                    trendingResult, seasonResult, popularAnimeResult, latestResult, popularMangaResult
                ).mapNotNull { it.exceptionOrNull() }

                val nothingLoaded =
                    trendingResult.getOrNull().isNullOrEmpty() && latestResult.getOrNull().isNullOrEmpty()

                _state.value = _state.value.copy(
                    loading = false,
                    error = if (nothingLoaded) {
                        failures.firstOrNull()?.toUserMessage() ?: "Data tidak tersedia saat ini."
                    } else null,
                    trending = trendingResult.getOrElse { _state.value.trending },
                    season = seasonResult.getOrElse { _state.value.season },
                    popularAnime = popularAnimeResult.getOrElse { _state.value.popularAnime },
                    mangaLatest = latestResult.getOrElse { _state.value.mangaLatest },
                    mangaPopular = popularMangaResult.getOrElse { _state.value.mangaPopular }
                )
            }
        }
    }

    fun setKind(kind: MangaKind) {
        if (kind == _state.value.mangaKind) return
        _state.value = _state.value.copy(mangaKind = kind, kindLoading = true)
        viewModelScope.launch {
            val result = runCatching {
                MangaDexApi.popular(lang = "id", originalLanguage = kind.originalLanguage)
            }
            _state.value = _state.value.copy(
                kindLoading = false,
                mangaPopular = result.getOrElse { _state.value.mangaPopular }
            )
        }
    }
}
