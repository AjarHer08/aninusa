package id.aninusa.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error jaringan/API yang aman ditampilkan ke pengguna. */
class ApiException(message: String) : IOException(message)

/**
 * Klien HTTP tunggal untuk seluruh aplikasi (OkHttp) + cache memori sederhana
 * agar hemat kuota dan tidak kena rate-limit API publik.
 */
object Http {

    private const val UA = "AniNusa/1.0 (Android; aplikasi baca anime & komik)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private data class CacheEntry(val body: String, val time: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > 120
    }

    private const val CACHE_TTL_MS = 3 * 60 * 1000L

    suspend fun get(url: String, useCache: Boolean = true): String = withContext(Dispatchers.IO) {
        if (useCache) {
            synchronized(cache) {
                val hit = cache[url]
                if (hit != null && System.currentTimeMillis() - hit.time < CACHE_TTL_MS) {
                    return@withContext hit.body
                }
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = execute(request)
        if (useCache) {
            synchronized(cache) { cache[url] = CacheEntry(body, System.currentTimeMillis()) }
        }
        body
    }

    suspend fun postJson(url: String, payload: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        execute(request)
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException("HTTP ${response.code}")
            }
            if (text.isBlank()) throw ApiException("Respons kosong dari server")
            return text
        }
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }
}
