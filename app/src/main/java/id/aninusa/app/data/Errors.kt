package id.aninusa.app.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Mengubah exception teknis menjadi pesan yang ramah pengguna. */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "Tidak ada koneksi internet. Periksa jaringan lalu coba lagi."
    is SocketTimeoutException -> "Koneksi terlalu lambat atau server tidak merespons. Coba lagi."
    is ApiException -> message ?: "Permintaan ke server gagal."
    is SSLException ->
        "Koneksi aman (HTTPS) gagal. Periksa tanggal & waktu di HP, lalu coba lagi."
    is IOException -> "Gagal menghubungi server. Periksa koneksi internet kamu."
    else -> message ?: "Terjadi kesalahan tak terduga."
}
