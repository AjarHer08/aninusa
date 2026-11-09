package id.aninusa.app.data

import android.content.Context
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 6.0 dan 7.0 belum memercayai akar sertifikat yang sekarang dipakai penyedia data:
 * MangaDex memakai Let's Encrypt (ISRG Root X1, baru ada di trust store sejak Android 7.1.1)
 * dan AniList memakai Google Trust Services (GTS Root R1–R4). Tanpa akar tersebut, semua
 * permintaan HTTPS gagal sebelum data diterima, sehingga aplikasi tampak "gagal memuat data".
 *
 * Kelas ini menambahkan akar sertifikat publik tersebut di atas trust store bawaan perangkat.
 * Verifikasi sertifikat tetap berjalan normal: tidak ada host yang dilewati atau dipercaya
 * tanpa rantai sertifikat yang valid.
 */
object TlsRoots {

    private const val ASSET_DIR = "tls"

    @Volatile
    private var installed = false

    @Volatile
    private var trustManager: X509TrustManager? = null

    @Volatile
    private var socketFactory: SSLSocketFactory? = null

    /** Jumlah akar sertifikat tambahan yang berhasil dimuat (dipakai layar diagnostik). */
    @Volatile
    var bundledCount: Int = 0
        private set

    /** True bila akar tambahan benar-benar dipakai oleh klien HTTP. */
    val active: Boolean
        get() = trustManager != null && socketFactory != null

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            runCatching {
                val anchors = loadBundled(context)
                bundledCount = anchors.size
                if (anchors.isNotEmpty()) {
                    val mine = anchorsManager(anchors)
                    val system = systemManager()
                    val combined = if (system == null) mine else CombinedTrustManager(mine, system)
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf<X509TrustManager>(combined), SecureRandom())
                    trustManager = combined
                    socketFactory = sslContext.socketFactory
                }
            }
            installed = true
        }
    }

    fun trustManager(): X509TrustManager? = trustManager

    fun socketFactory(): SSLSocketFactory? = socketFactory

    private fun loadBundled(context: Context): List<X509Certificate> {
        val names = runCatching { context.assets.list(ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".pem") || it.endsWith(".crt") }
            .sorted()
        if (names.isEmpty()) return emptyList()
        val factory = CertificateFactory.getInstance("X.509")
        return names.mapNotNull { name ->
            runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input: InputStream ->
                    factory.generateCertificate(input) as X509Certificate
                }
            }.getOrNull()
        }
    }

    /**
     * Mencoba beberapa tipe KeyStore (PKCS12, BKS, default) sampai satu di antaranya
     * berhasil dipakai sebagai trust anchor. Bila semuanya gagal, pengguna tetap bisa
     * memakai trust store sistem dan layar diagnostik melaporkan statusnya.
     */
    private fun anchorsManager(anchors: List<X509Certificate>): X509TrustManager {
        val types = listOf("PKCS12", "BKS", KeyStore.getDefaultType()).distinct()
        var lastError: Throwable? = null
        for (type in types) {
            try {
                val store = KeyStore.getInstance(type)
                store.load(null, null)
                anchors.forEachIndexed { index, certificate ->
                    store.setCertificateEntry("aninusa-root-$index", certificate)
                }
                val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                factory.init(store)
                factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                    ?.let { return it }
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("TrustManager akar tambahan tidak tersedia", lastError)
    }

    private fun systemManager(): X509TrustManager? =
        runCatching {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        }.getOrNull()

    /** Menerima rantai sertifikat jika salah satu trust manager (tambahan atau sistem) menerimanya. */
    private class CombinedTrustManager(
        private val primary: X509TrustManager,
        private val secondary: X509TrustManager
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                primary.checkClientTrusted(chain, authType)
            } catch (error: CertificateException) {
                secondary.checkClientTrusted(chain, authType)
            }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                primary.checkServerTrusted(chain, authType)
            } catch (error: CertificateException) {
                secondary.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            primary.acceptedIssuers + secondary.acceptedIssuers
    }
}
