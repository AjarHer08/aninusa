package id.aninusa.app.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Satu baris hasil pemeriksaan koneksi pada layar Pengaturan. */
data class DiagnosticCheck(
    val label: String,
    val success: Boolean,
    val detail: String
)

/**
 * Memeriksa setiap sumber data secara berurutan supaya pengguna (dan pengembang) tahu
 * bagian mana yang gagal: AniList, MangaDex, gambar sampul, atau lingkungan perangkat.
 */
object Diagnostics {

    suspend fun run(): List<DiagnosticCheck> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheck>()
        var coverUrl: String? = null

        results += check("AniList · anime") {
            val anime = AniListApi.trending()
            "${anime.size} judul diterima"
        }

        results += check("MangaDex · komik") {
            val manga = MangaDexApi.popular(lang = "id", originalLanguage = null, limit = 5)
            coverUrl = manga.firstOrNull()?.coverUrl
            "${manga.size} judul diterima"
        }

        results += check("MangaDex · update Indonesia") {
            val latest = MangaDexApi.latestIndonesian(limit = 5)
            "${latest.size} judul diperbarui"
        }

        results += check("Gambar sampul (HTTPS)") {
            val url = coverUrl ?: throw ApiException("Tidak ada URL sampul untuk diuji")
            val request = Request.Builder().url(url).head().build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("HTTP ${response.code}")
                "HTTP ${response.code} · gambar dapat diunduh"
            }
        }

        results += DiagnosticCheck(
            label = "Lingkungan",
            success = true,
            detail = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                "${TlsRoots.bundledCount} akar sertifikat tambahan"
        )

        results
    }

    private inline fun check(label: String, block: () -> String): DiagnosticCheck =
        try {
            DiagnosticCheck(label = label, success = true, detail = block())
        } catch (error: Throwable) {
            DiagnosticCheck(label = label, success = false, detail = error.toUserMessage())
        }
}
