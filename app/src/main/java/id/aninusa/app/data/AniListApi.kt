package id.aninusa.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar

// ---------- Model AniList (anime) ----------

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
)

@Serializable
data class CoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null
)

@Serializable
data class Trailer(val id: String? = null, val site: String? = null)

@Serializable
data class FuzzyDate(val year: Int? = null, val month: Int? = null, val day: Int? = null)

@Serializable
data class NextAiring(
    val episode: Int? = null,
    val timeUntilAiring: Long? = null,
    val airingAt: Long? = null
)

@Serializable
data class StudioNode(val name: String? = null)

@Serializable
data class Studios(val nodes: List<StudioNode> = emptyList())

@Serializable
data class StreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null
)

@Serializable
data class RecommendationNode(val mediaRecommendation: Anime? = null)

@Serializable
data class Recommendations(val nodes: List<RecommendationNode> = emptyList())

@Serializable
data class RelationEdge(val relationType: String? = null, val node: Anime? = null)

@Serializable
data class Relations(val edges: List<RelationEdge> = emptyList())

@Serializable
data class Anime(
    val id: Int = 0,
    val type: String? = null,
    val title: AnimeTitle? = null,
    val description: String? = null,
    val coverImage: CoverImage? = null,
    val bannerImage: String? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val genres: List<String> = emptyList(),
    val siteUrl: String? = null,
    val trailer: Trailer? = null,
    val nextAiringEpisode: NextAiring? = null,
    val startDate: FuzzyDate? = null,
    val studios: Studios? = null,
    val recommendations: Recommendations? = null,
    val relations: Relations? = null,
    val streamingEpisodes: List<StreamingEpisode> = emptyList()
)

val Anime.displayTitle: String
    get() = title?.english?.takeIf { it.isNotBlank() }
        ?: title?.romaji?.takeIf { it.isNotBlank() }
        ?: title?.native?.takeIf { it.isNotBlank() }
        ?: "Tanpa judul"

val Anime.posterUrl: String?
    get() = coverImage?.extraLarge ?: coverImage?.large

val Anime.yearLabel: String
    get() = seasonYear?.toString() ?: startDate?.year?.toString() ?: "—"

val Anime.studioName: String?
    get() = studios?.nodes?.firstOrNull()?.name

/** Terjemahan label format AniList ke Bahasa Indonesia. */
fun formatLabel(format: String?): String = when (format) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Pendek"
    "MOVIE" -> "Film"
    "SPECIAL" -> "Spesial"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> "Musik"
    else -> "Anime"
}

fun statusLabel(status: String?): String = when (status) {
    "RELEASING" -> "Sedang Tayang"
    "FINISHED" -> "Tamat"
    "NOT_YET_RELEASED" -> "Belum Tayang"
    "CANCELLED" -> "Dibatalkan"
    "HIATUS" -> "Hiatus"
    else -> "Tidak diketahui"
}

// ---------- Klien AniList GraphQL ----------

object AniListApi {

    private const val ENDPOINT = "https://graphql.anilist.co"

    private val mediaFields = """
        id
        type
        title { romaji english native }
        description(asHtml: false)
        coverImage { extraLarge large color }
        bannerImage
        averageScore
        popularity
        episodes
        duration
        format
        status
        season
        seasonYear
        genres
        siteUrl
        trailer { id site }
        nextAiringEpisode { episode timeUntilAiring airingAt }
        startDate { year month day }
        studios(isMain: true) { nodes { name } }
    """.trimIndent()

    @Serializable
    private data class PageWrap(val data: PageData? = null)

    @Serializable
    private data class PageData(val Page: MediaPage? = null)

    @Serializable
    private data class MediaPage(val media: List<Anime> = emptyList())

    @Serializable
    private data class MediaWrap(val data: MediaHolder? = null)

    @Serializable
    private data class MediaHolder(val Media: Anime? = null)

    private fun payload(query: String, variables: Map<String, Any> = emptyMap()): String =
        buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject {
                variables.forEach { (key, value) ->
                    when (value) {
                        is Int -> put(key, value)
                        is Long -> put(key, value)
                        is Boolean -> put(key, value)
                        is String -> put(key, value)
                        null -> put(key, JsonNull)
                        else -> put(key, value.toString())
                    }
                }
            })
        }.toString()

    private suspend fun pageQuery(query: String, variables: Map<String, Any> = emptyMap()): List<Anime> {
        val raw = Http.postJson(ENDPOINT, payload(query, variables))
        val parsed = runCatching { Http.json.decodeFromString(PageWrap.serializer(), raw) }.getOrNull()
            ?: throw ApiException("Format respons AniList tidak dikenali")
        return parsed.data?.Page?.media.orEmpty()
    }

    private fun pageBody(sort: String, extra: String = ""): String = """
        query {
          Page(page: 1, perPage: 24) {
            media(type: ANIME, sort: $sort, isAdult: false $extra) { $mediaFields }
          }
        }
    """.trimIndent()

    suspend fun trending(): List<Anime> = pageQuery(pageBody("TRENDING_DESC"))

    suspend fun popular(): List<Anime> = pageQuery(pageBody("POPULARITY_DESC"))

    suspend fun thisSeason(): List<Anime> {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val season = when (month) {
            in 0..2 -> "WINTER"
            in 3..5 -> "SPRING"
            in 6..8 -> "SUMMER"
            else -> "FALL"
        }
        val year = calendar.get(Calendar.YEAR)
        return pageQuery(pageBody("POPULARITY_DESC", ", season: $season, seasonYear: $year"))
    }

    suspend fun search(keyword: String): List<Anime> {
        val query = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 30) {
                media(type: ANIME, search: ${'$'}search, sort: SEARCH_MATCH, isAdult: false) { $mediaFields }
              }
            }
        """.trimIndent()
        return pageQuery(query, mapOf("search" to keyword))
    }

    suspend fun detail(id: Int): Anime {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                $mediaFields
                streamingEpisodes { title thumbnail url site }
                relations { edges { relationType node { id type title { romaji english } coverImage { large extraLarge } } } }
                recommendations(perPage: 12, sort: RATING_DESC) {
                  nodes { mediaRecommendation { id type title { romaji english } coverImage { large extraLarge } averageScore format } }
                }
              }
            }
        """.trimIndent()
        val raw = Http.postJson(ENDPOINT, payload(query, mapOf("id" to id)))
        val parsed = runCatching { Http.json.decodeFromString(MediaWrap.serializer(), raw) }.getOrNull()
            ?: throw ApiException("Detail anime tidak ditemukan")
        return parsed.data?.Media ?: throw ApiException("Anime tidak ditemukan di AniList")
    }
}
