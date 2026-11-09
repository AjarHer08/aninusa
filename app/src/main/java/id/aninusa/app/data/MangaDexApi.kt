package id.aninusa.app.data

import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ---------- Model mentah MangaDex ----------

@Serializable
data class MdRelationship(
    val id: String = "",
    val type: String = "",
    val attributes: MdRelAttributes? = null
)

@Serializable
data class MdRelAttributes(
    val fileName: String? = null,
    val name: String? = null,
    val volume: String? = null
)

@Serializable
data class MdTagRef(
    val id: String = "",
    val attributes: MdTagAttributes? = null
)

@Serializable
data class MdTagAttributes(
    val name: Map<String, String> = emptyMap(),
    val group: String? = null
)

@Serializable
data class MdMangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String> = emptyMap(),
    val originalLanguage: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val tags: List<MdTagRef> = emptyList(),
    val availableTranslatedLanguages: List<String> = emptyList(),
    val lastChapter: String? = null,
    val contentRating: String? = null,
    val publicationDemographic: String? = null
)

@Serializable
data class MdManga(
    val id: String = "",
    val attributes: MdMangaAttributes = MdMangaAttributes(),
    val relationships: List<MdRelationship> = emptyList()
)

@Serializable
data class MdChapterAttributes(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String? = null,
    val externalUrl: String? = null,
    val pages: Int = 0,
    val publishAt: String? = null,
    val readableAt: String? = null,
    val isUnavailable: Boolean = false
)

@Serializable
data class MdChapter(
    val id: String = "",
    val attributes: MdChapterAttributes = MdChapterAttributes(),
    val relationships: List<MdRelationship> = emptyList()
)

@Serializable
data class MdListResponse<T>(
    val result: String? = null,
    val data: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

@Serializable
data class MdEntityResponse<T>(
    val result: String? = null,
    val data: T? = null
)

@Serializable
data class MdAtHomeChapter(
    val hash: String = "",
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList()
)

@Serializable
data class MdAtHome(
    val baseUrl: String = "",
    val chapter: MdAtHomeChapter = MdAtHomeChapter()
)

// ---------- Model aplikasi ----------

enum class MangaKind(val label: String, val originalLanguage: String?) {
    ALL("Semua", null),
    MANGA("Manga", "ja"),
    MANHWA("Manhwa", "ko"),
    MANHUA("Manhua", "zh")
}

data class Manga(
    val id: String,
    val title: String,
    val altTitle: String? = null,
    val coverUrl: String? = null,
    val description: String = "",
    val kind: String = "Komik",
    val status: String = "Tidak diketahui",
    val year: Int? = null,
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val lastChapter: String? = null,
    val author: String? = null,
    val demographic: String? = null,
    val langKr: String? = null
)

data class MangaChapter(
    val id: String,
    val label: String,
    val title: String? = null,
    val lang: String = "id",
    val pages: Int = 0,
    val externalUrl: String? = null,
    val date: String? = null,
    val group: String? = null,
    val number: Double = -1.0
)

// ---------- Klien MangaDex ----------

object MangaDexApi {

    private const val BASE = "https://api.mangadex.org"
    private const val CONTENT = "contentRating[]=safe&contentRating[]=suggestive"
    private const val COVER_INCLUDE = "includes[]=cover_art"

    private val langNames = mapOf(
        "id" to "Bahasa Indonesia",
        "en" to "English",
        "es" to "Español",
        "es-la" to "Español (LatAm)",
        "pt-br" to "Português",
        "fr" to "Français",
        "ru" to "Русский",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
        "it" to "Italiano",
        "de" to "Deutsch",
        "th" to "ไทย",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "pl" to "Polski"
    )

    fun languageName(code: String): String = langNames[code] ?: code.uppercase(Locale.ROOT)

    private fun bestTitle(attributes: MdMangaAttributes): String {
        attributes.title["id"]?.let { return it }
        attributes.title["en"]?.let { return it }
        attributes.altTitles.firstNotNullOfOrNull { it["id"] }?.let { return it }
        attributes.altTitles.firstNotNullOfOrNull { it["en"] }?.let { return it }
        attributes.title.values.firstOrNull()?.let { return it }
        return attributes.altTitles.firstOrNull()?.values?.firstOrNull() ?: "Tanpa judul"
    }

    private fun altTitle(attributes: MdMangaAttributes, main: String): String? {
        val candidates = buildList {
            attributes.title["en"]?.let { add(it) }
            attributes.altTitles.forEach { map -> map["id"]?.let { add(it) }; map["en"]?.let { add(it) } }
        }
        return candidates.firstOrNull { it.isNotBlank() && !it.equals(main, ignoreCase = true) }
    }

    private fun kindOf(originalLanguage: String?): String = when (originalLanguage) {
        "ja" -> "Manga"
        "ko" -> "Manhwa"
        "zh" -> "Manhua"
        else -> "Komik"
    }

    private fun statusOf(status: String?): String = when (status) {
        "ongoing" -> "Sedang Berjalan"
        "completed" -> "Selesai"
        "hiatus" -> "Hiatus"
        "cancelled" -> "Dibatalkan"
        else -> "Tidak diketahui"
    }

    private fun formatDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(raw.take(10)) ?: return null
            val output = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("id-ID"))
            output.format(date)
        }.getOrNull()
    }

    fun MdManga.coverFileName(): String? =
        relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName

    fun MdManga.toManga(): Manga {
        val attributes = this.attributes
        val main = bestTitle(attributes)
        val author = relationships.firstOrNull { it.type == "author" }?.attributes?.name
            ?: relationships.firstOrNull { it.type == "artist" }?.attributes?.name
        val fileName = coverFileName()
        val description = attributes.description["id"]
            ?: attributes.description["en"]
            ?: attributes.description.values.firstOrNull()
            ?: ""
        return Manga(
            id = id,
            title = main,
            altTitle = altTitle(attributes, main),
            coverUrl = fileName?.let { "https://uploads.mangadex.org/covers/$id/$it.512.jpg" },
            description = description,
            kind = kindOf(attributes.originalLanguage),
            status = statusOf(attributes.status),
            year = attributes.year,
            tags = attributes.tags.mapNotNull { it.attributes?.name?.get("en") }.distinct().take(10),
            languages = attributes.availableTranslatedLanguages,
            lastChapter = attributes.lastChapter,
            author = author,
            demographic = attributes.publicationDemographic,
            langKr = attributes.title["ko-ro"]
        )
    }

    fun MdChapter.toChapter(): MangaChapter {
        val attributes = this.attributes
        val rawNumber = attributes.chapter?.trim()?.takeIf { it.isNotEmpty() }
        val label = when {
            rawNumber != null && attributes.volume != null -> "Vol. ${attributes.volume} · Ch. $rawNumber"
            rawNumber != null -> "Ch. $rawNumber"
            attributes.title?.isNotBlank() == true -> attributes.title
            else -> "Chapter"
        }
        return MangaChapter(
            id = id,
            label = label,
            title = attributes.title?.takeIf { it.isNotBlank() },
            lang = attributes.translatedLanguage ?: "id",
            pages = attributes.pages,
            externalUrl = attributes.externalUrl,
            date = formatDate(attributes.readableAt ?: attributes.publishAt),
            group = relationships.firstOrNull { it.type == "scanlation_group" }?.attributes?.name,
            number = rawNumber?.toDoubleOrNull() ?: -1.0
        )
    }

    private suspend fun mangaList(url: String): List<MdManga> {
        val raw = Http.get(url)
        val parsed = runCatching {
            Http.json.decodeFromString(MdListResponse.serializer(MdManga.serializer()), raw)
        }.getOrNull() ?: throw ApiException("Format respons MangaDex tidak dikenali")
        return parsed.data
    }

    suspend fun popular(
        lang: String,
        originalLanguage: String?,
        page: Int = 0,
        limit: Int = 24
    ): List<Manga> {
        val offset = page * limit
        val langFilter = if (lang.isBlank()) "" else "&availableTranslatedLanguage[]=$lang"
        val originFilter = originalLanguage?.let { "&originalLanguage[]=$it" }.orEmpty()
        val url = buildString {
            append("$BASE/manga?limit=$limit&offset=$offset")
            append("&$COVER_INCLUDE$langFilter$originFilter")
            append("&order[followedCount]=desc&hasAvailableChapters=true")
            append("&$CONTENT")
        }
        return mangaList(url).map { it.toManga() }
    }

    suspend fun search(keyword: String, lang: String, page: Int = 0, limit: Int = 24): List<Manga> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val offset = page * limit
        val langFilter = if (lang.isBlank()) "" else "&availableTranslatedLanguage[]=$lang"
        val url = buildString {
            append("$BASE/manga?limit=$limit&offset=$offset&title=$encoded")
            append("&$COVER_INCLUDE&order[relevance]=desc$langFilter")
            append("&$CONTENT")
        }
        return mangaList(url).map { it.toManga() }
    }

    /** Daftar judul dengan chapter Indonesia yang baru diunggah (feed "update terbaru"). */
    suspend fun latestIndonesian(limit: Int = 40): List<Manga> {
        val url = buildString {
            append("$BASE/chapter?limit=$limit&translatedLanguage[]=id")
            append("&order[readableAt]=desc&includes[]=manga")
            append("&$CONTENT")
        }
        val raw = Http.get(url)
        val parsed = runCatching {
            Http.json.decodeFromString(MdListResponse.serializer(MdChapter.serializer()), raw)
        }.getOrNull() ?: return emptyList()
        val ids = LinkedHashSet<String>()
        for (chapter in parsed.data) {
            val mangaId = chapter.relationships.firstOrNull { it.type == "manga" }?.id ?: continue
            if (mangaId.isNotBlank()) ids.add(mangaId)
            if (ids.size >= 24) break
        }
        if (ids.isEmpty()) return emptyList()
        return mangasByIds(ids.toList())
    }

    suspend fun mangasByIds(ids: List<String>): List<Manga> {
        if (ids.isEmpty()) return emptyList()
        val limited = ids.take(60)
        val idParams = limited.joinToString("&") { "ids[]=$it" }
        val url = "$BASE/manga?limit=${limited.size}&$idParams&$COVER_INCLUDE"
        val fetched = mangaList(url).associateBy { it.id }
        return limited.mapNotNull { fetched[it]?.toManga() }
    }

    suspend fun detail(mangaId: String): Manga? {
        val url = "$BASE/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist"
        val raw = Http.get(url)
        val parsed = runCatching {
            Http.json.decodeFromString(MdEntityResponse.serializer(MdManga.serializer()), raw)
        }.getOrNull() ?: return null
        return parsed.data?.toManga()
    }

    private val chapterCache = HashMap<String, List<MangaChapter>>()

    suspend fun chapters(mangaId: String, lang: String): List<MangaChapter> {
        val cacheKey = "$mangaId|$lang"
        synchronized(chapterCache) { chapterCache[cacheKey] }?.let { if (it.isNotEmpty()) return it }
        val url = buildString {
            append("$BASE/manga/$mangaId/feed?limit=500&translatedLanguage[]=$lang")
            append("&order[chapter]=desc&includes[]=scanlation_group")
            append("&$CONTENT")
        }
        val raw = Http.get(url)
        val parsed = runCatching {
            Http.json.decodeFromString(MdListResponse.serializer(MdChapter.serializer()), raw)
        }.getOrNull() ?: return emptyList()
        val chapters = parsed.data
            .map { it.toChapter() }
            .sortedWith(compareByDescending<MangaChapter> { it.number }.thenByDescending { it.date.orEmpty() })
        if (chapters.isNotEmpty()) {
            synchronized(chapterCache) { chapterCache[cacheKey] = chapters }
        }
        return chapters
    }

    suspend fun chapterPages(chapterId: String, dataSaver: Boolean): List<String> {
        val raw = Http.get("$BASE/at-home/server/$chapterId", useCache = false)
        val parsed = runCatching { Http.json.decodeFromString(MdAtHome.serializer(), raw) }.getOrNull()
            ?: throw ApiException("Gagal memuat gambar chapter")
        val files = if (dataSaver) parsed.chapter.dataSaver else parsed.chapter.data
        val mode = if (dataSaver) "data-saver" else "data"
        return files.map { "${parsed.baseUrl}/$mode/${parsed.chapter.hash}/$it" }
    }

    fun webUrl(mangaId: String): String = "https://mangadex.org/title/$mangaId"
}
