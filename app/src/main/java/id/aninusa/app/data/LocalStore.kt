package id.aninusa.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class Favorite(
    val id: String,
    val title: String,
    val cover: String? = null,
    val kind: String = "MANGA",
    val subtitle: String? = null,
    val addedAt: Long = 0L
)

@Serializable
data class ReadingProgress(
    val mangaId: String,
    val mangaTitle: String,
    val cover: String? = null,
    val chapterId: String,
    val chapterLabel: String,
    val page: Int = 0,
    val total: Int = 0,
    val lang: String = "id",
    val updatedAt: Long = 0L
)

@Serializable
data class AnimeHistory(
    val id: Int,
    val title: String,
    val cover: String? = null,
    val meta: String? = null,
    val updatedAt: Long = 0L
)

@Serializable
data class Prefs(
    val themeMode: String = "dark",
    val dataSaver: Boolean = false,
    val verticalReader: Boolean = true,
    val mangaLanguage: String = "id"
)

@Serializable
data class UserData(
    val favorites: List<Favorite> = emptyList(),
    val progress: List<ReadingProgress> = emptyList(),
    val animeHistory: List<AnimeHistory> = emptyList(),
    val prefs: Prefs = Prefs()
)

/**
 * Penyimpanan lokal ringan (SharedPreferences + JSON) untuk favorit,
 * riwayat baca, dan preferensi. Semua perubahan langsung dipancarkan
 * lewat [data] sehingga UI ikut ter-update.
 */
class LocalStore private constructor(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("aninusa_store", Context.MODE_PRIVATE)

    private val _data = MutableStateFlow(read())
    val data: StateFlow<UserData> = _data.asStateFlow()

    private fun read(): UserData {
        val raw = preferences.getString(KEY, null) ?: return UserData()
        return runCatching { Http.json.decodeFromString(UserData.serializer(), raw) }
            .getOrElse { UserData() }
    }

    private fun write(value: UserData) {
        _data.value = value
        runCatching {
            preferences.edit()
                .putString(KEY, Http.json.encodeToString(UserData.serializer(), value))
                .apply()
        }
    }

    fun toggleFavorite(favorite: Favorite): Boolean {
        val current = _data.value
        val exists = current.favorites.any { it.kind == favorite.kind && it.id == favorite.id }
        val updated = if (exists) {
            current.favorites.filterNot { it.kind == favorite.kind && it.id == favorite.id }
        } else {
            listOf(favorite.copy(addedAt = System.currentTimeMillis())) + current.favorites
        }
        write(current.copy(favorites = updated))
        return !exists
    }

    fun saveProgress(progress: ReadingProgress) {
        val current = _data.value
        val stamped = progress.copy(updatedAt = System.currentTimeMillis())
        val updated = (listOf(stamped) + current.progress.filterNot { it.mangaId == progress.mangaId })
            .take(60)
        write(current.copy(progress = updated))
    }

    fun progressFor(mangaId: String): ReadingProgress? =
        _data.value.progress.firstOrNull { it.mangaId == mangaId }

    fun rememberAnime(history: AnimeHistory) {
        val current = _data.value
        val stamped = history.copy(updatedAt = System.currentTimeMillis())
        val updated = (listOf(stamped) + current.animeHistory.filterNot { it.id == history.id }).take(40)
        write(current.copy(animeHistory = updated))
    }

    fun updatePrefs(block: (Prefs) -> Prefs) {
        val current = _data.value
        write(current.copy(prefs = block(current.prefs)))
    }

    fun clearHistory() {
        val current = _data.value
        write(current.copy(progress = emptyList(), animeHistory = emptyList()))
    }

    companion object {
        private const val KEY = "user_data_v1"

        @Volatile
        private var instance: LocalStore? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = LocalStore(context)
                }
            }
        }

        fun get(): LocalStore = instance ?: throw IllegalStateException("LocalStore belum diinisialisasi")
    }
}
