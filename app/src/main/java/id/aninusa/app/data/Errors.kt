package id.aninusa.app.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Mengubah exception teknis menjadi pesan yang ramah pengguna. */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "Tidak ada koneksi internet. Periksa jaringan lalu coba lagi."
    is SocketTimeoutException -> "Koneksi terlalu lambat atau server tidak merespons. Coba lagi."
    is ApiException -> "Server menolak permintaan (${message ?: "error"}). Coba beberapa saat lagi."
    is IOException -> "Gagal menghubungi server. Periksa koneksi internet kamu."
    else -> message ?: "Terjadi kesalahan tak terduga."
}
