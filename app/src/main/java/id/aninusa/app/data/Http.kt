package id.aninusa.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Error jaringan/API yang aman ditampilkan ke pengguna. Pesannya sudah berbahasa Indonesia. */
class ApiException(message: String) : IOException(message)

/**
 * Klien HTTP tunggal untuk seluruh aplikasi (OkHttp) + cache memori sederhana
 * agar hemat kuota dan tidak kena rate-limit API publik.
 */
object Http {

    private const val UA = "AniNusa/1.0 (Android; aplikasi baca anime & komik)"

    private const val MAX_ATTEMPTS = 3

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Dipakai juga oleh Coil agar pemuatan gambar memakai trust store yang sama. */
    val client: OkHttpClient by lazy { buildClient() }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .callTimeout(50, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        val trustManager = TlsRoots.trustManager()
        val socketFactory = TlsRoots.socketFactory()
        if (trustManager != null && socketFactory != null) {
            builder.sslSocketFactory(socketFactory, trustManager)
        }
        return builder.build()
    }

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

    /**
     * Mengunduh berkas biner (gambar chapter) dengan percobaan ulang yang sama
     * seperti permintaan JSON, lalu mengembalikan isinya beserta tipe konten.
     */
    suspend fun bytes(url: String): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "image/*")
            .get()
            .build()
        var attempt = 1
        while (true) {
            try {
                return@withContext singleBinaryCall(request)
            } catch (error: RetryableHttpException) {
                if (attempt >= MAX_ATTEMPTS) throw ApiException(messageFor(error.code))
                pause(attempt)
            } catch (error: SocketTimeoutException) {
                if (attempt >= MAX_ATTEMPTS) throw error
                pause(attempt)
            }
            attempt++
        }
        @Suppress("UNREACHABLE_CODE")
        throw ApiException("Gambar tidak bisa diunduh.")
    }

    /**
     * Menjalankan permintaan dengan percobaan ulang singkat untuk batas laju (HTTP 429)
     * dan gangguan server/timeout, lalu menerjemahkan kegagalan menjadi pesan yang jelas.
     */
    private fun execute(request: Request): String {
        var attempt = 1
        while (true) {
            try {
                return singleCall(request)
            } catch (error: RetryableHttpException) {
                if (attempt >= MAX_ATTEMPTS) throw ApiException(messageFor(error.code))
                pause(attempt)
            } catch (error: SocketTimeoutException) {
                if (attempt >= MAX_ATTEMPTS) throw error
                pause(attempt)
            }
            attempt++
        }
    }

    private fun singleCall(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 429 || response.code >= 500) {
                    throw RetryableHttpException(response.code)
                }
                throw ApiException(messageFor(response.code))
            }
            if (text.isBlank()) throw ApiException("Server mengirim respons kosong.")
            if (looksLikeWebPage(text)) {
                throw ApiException(
                    "Jaringan mengembalikan halaman web, bukan data API. Koneksi kemungkinan " +
                        "dibatasi operator seluler, penyedia internet, atau portal WiFi."
                )
            }
            return text
        }
    }

    private fun singleBinaryCall(request: Request): Pair<ByteArray, String?> {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 429 || response.code >= 500) {
                    throw RetryableHttpException(response.code)
                }
                throw ApiException(messageFor(response.code))
            }
            val body = response.body ?: throw ApiException("Server mengirim gambar kosong.")
            val data = body.bytes()
            if (data.isEmpty()) throw ApiException("Server mengirim gambar kosong.")
            return data to body.contentType()?.toString()
        }
    }

    private fun pause(attempt: Int) {
        val wait = 500L * attempt
        runCatching { Thread.sleep(wait) }
    }

    private fun looksLikeWebPage(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("<") && !trimmed.startsWith("{\"")
    }

    private fun messageFor(code: Int): String = when (code) {
        400 -> "Permintaan ditolak server (HTTP 400)."
        401, 403 -> "Akses ke server ditolak (HTTP $code). Jaringan atau wilayahmu mungkin dibatasi."
        404 -> "Data tidak ditemukan di server (HTTP 404)."
        429 -> "Terlalu banyak permintaan ke server (HTTP 429). Coba lagi sebentar lagi."
        in 500..599 -> "Server sedang bermasalah (HTTP $code). Coba lagi beberapa saat."
        else -> "Server membalas HTTP $code."
    }

    private class RetryableHttpException(val code: Int) : IOException("HTTP $code")

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }
}
