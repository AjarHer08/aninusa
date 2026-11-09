package id.aninusa.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

/** Satu chapter yang gambarnya sudah tersimpan di perangkat. */
@Serializable
data class DownloadedChapter(
    val mangaId: String,
    val mangaTitle: String,
    val chapterId: String,
    val chapterLabel: String,
    val lang: String = "id",
    val pageCount: Int = 0,
    val bytes: Long = 0L,
    val savedAt: Long = 0L
)

data class DownloadProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

/**
 * Penyimpanan chapter offline. Gambar disimpan sebagai berkas biasa di
 * penyimpanan internal aplikasi (tidak terlihat galeri) dan daftarnya dicatat
 * di SharedPreferences. Unduhan berjalan per chapter, tidak ada unduhan massal.
 */
class ChapterDownloads private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("aninusa_downloads", Context.MODE_PRIVATE)
    private val root = File(appContext.filesDir, "offline")

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<DownloadedChapter>> = _entries.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val running = HashSet<String>()

    fun entry(chapterId: String): DownloadedChapter? =
        _entries.value.firstOrNull { it.chapterId == chapterId }

    fun isDownloaded(chapterId: String): Boolean = entry(chapterId) != null

    fun countForManga(mangaId: String): Int = _entries.value.count { it.mangaId == mangaId }

    fun bytesForManga(mangaId: String): Long =
        _entries.value.filter { it.mangaId == mangaId }.sumOf { it.bytes }

    fun totalBytes(): Long = _entries.value.sumOf { it.bytes }

    /**
     * Halaman lokal siap baca. Kosong berarti chapter belum tersimpan (atau
     * berkasnya sudah dihapus), sehingga pembaca kembali memakai jaringan.
     */
    fun pages(chapterId: String): List<String> {
        val saved = entry(chapterId) ?: return emptyList()
        val files = chapterDir(saved.mangaId, chapterId)
            .listFiles { file -> file.isFile }
            ?.sortedBy { it.name }
            ?: return emptyList()
        if (files.isEmpty()) return emptyList()
        return files.map { Uri.fromFile(it).toString() }
    }

    suspend fun download(
        manga: Manga,
        chapter: MangaChapter,
        dataSaver: Boolean
    ): Result<DownloadedChapter> {
        entry(chapter.id)?.let { return Result.success(it) }
        if (!markRunning(chapter.id)) {
            return Result.failure(ApiException("Chapter ini sedang diunduh."))
        }
        val dir = chapterDir(manga.id, chapter.id)
        return try {
            val urls = MangaDexApi.chapterPages(chapter.id, dataSaver)
            if (urls.isEmpty()) throw ApiException("Chapter ini tidak punya halaman yang bisa diunduh.")
            withContext(Dispatchers.IO) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
            _progress.value = _progress.value + (chapter.id to DownloadProgress(0, urls.size))
            var bytes = 0L
            urls.forEachIndexed { index, url ->
                val (data, contentType) = Http.bytes(url)
                val file = File(dir, "%03d.%s".format(Locale.US, index + 1, extensionFor(contentType)))
                withContext(Dispatchers.IO) { file.writeBytes(data) }
                bytes += data.size
                _progress.value = _progress.value +
                    (chapter.id to DownloadProgress(index + 1, urls.size))
            }
            val saved = DownloadedChapter(
                mangaId = manga.id,
                mangaTitle = manga.title,
                chapterId = chapter.id,
                chapterLabel = chapter.label,
                lang = chapter.lang,
                pageCount = urls.size,
                bytes = bytes,
                savedAt = System.currentTimeMillis()
            )
            write(listOf(saved) + _entries.value.filterNot { it.chapterId == chapter.id })
            Result.success(saved)
        } catch (error: Throwable) {
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
            Result.failure(error)
        } finally {
            _progress.value = _progress.value - chapter.id
            clearRunning(chapter.id)
        }
    }

    fun remove(chapterId: String) {
        val saved = entry(chapterId) ?: return
        chapterDir(saved.mangaId, chapterId).deleteRecursively()
        write(_entries.value.filterNot { it.chapterId == chapterId })
    }

    fun removeForManga(mangaId: String) {
        _entries.value.filter { it.mangaId == mangaId }.forEach { remove(it.chapterId) }
    }

    fun removeAll() {
        runCatching { root.deleteRecursively() }
        write(emptyList())
    }

    private fun chapterDir(mangaId: String, chapterId: String): File =
        File(File(root, safeName(mangaId)), safeName(chapterId))

    private fun safeName(raw: String): String =
        raw.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

    private fun markRunning(chapterId: String): Boolean =
        synchronized(running) { running.add(chapterId) }

    private fun clearRunning(chapterId: String) {
        synchronized(running) { running.remove(chapterId) }
    }

    private fun read(): List<DownloadedChapter> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            Http.json.decodeFromString(ListSerializer, raw)
        }.getOrElse { emptyList() }
    }

    private fun write(value: List<DownloadedChapter>) {
        _entries.value = value
        runCatching {
            preferences.edit()
                .putString(KEY, Http.json.encodeToString(ListSerializer, value))
                .apply()
        }
    }

    companion object {
        private const val KEY = "downloaded_chapters_v1"

        private val ListSerializer =
            kotlinx.serialization.builtins.ListSerializer(DownloadedChapter.serializer())

        @Volatile
        private var instance: ChapterDownloads? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = ChapterDownloads(context)
                }
            }
        }

        fun get(): ChapterDownloads =
            instance ?: throw IllegalStateException("ChapterDownloads belum diinisialisasi")

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024L * 1024L ->
                String.format(Locale.forLanguageTag("id-ID"), "%.1f MB", bytes / 1048576.0)
            bytes >= 1024L -> "${bytes / 1024} KB"
            bytes > 0L -> "$bytes B"
            else -> "0 KB"
        }
    }
}

private fun extensionFor(contentType: String?): String = when {
    contentType == null -> "jpg"
    contentType.contains("png", ignoreCase = true) -> "png"
    contentType.contains("webp", ignoreCase = true) -> "webp"
    contentType.contains("gif", ignoreCase = true) -> "gif"
    contentType.contains("avif", ignoreCase = true) -> "avif"
    else -> "jpg"
}
