# AniNusa — Anime, Manga, Manhwa & Manhua (Sub Indo)

[![Android CI](https://github.com/AjarHer08/aninusa/actions/workflows/android.yml/badge.svg)](https://github.com/AjarHer08/aninusa/actions/workflows/android.yml)

Aplikasi Android native (Kotlin + Jetpack Compose) untuk menjelajah anime dan membaca
komik (manga, manhwa, manhua) dengan chapter berbahasa Indonesia.

- **Paket:** `id.aninusa.app`
- **Min SDK:** 23 (Android 6.0 Marshmallow)
- **Compile/Target SDK:** 36 (Android 16), sehingga tetap bisa dipasang dan berjalan di
  versi Android yang lebih baru, termasuk Android 17.
- **APK siap pasang:** [`dist/AniNusa-1.0.7-debug.apk`](dist/AniNusa-1.0.7-debug.apk)

## Fitur

**Anime**
- Beranda dengan anime sedang tren, terpopuler, dan rilisan musim ini (AniList GraphQL).
- Pencarian anime.
- Detail anime: skor, status, studio, genre, sinopsis (bisa diperluas), hitung mundur
  episode berikutnya, rekomendasi serupa, dan trailer.
- Tautan menonton ke platform resmi ber-subtitle Indonesia (Bstation, iQIYI, Netflix)
  serta tautan platform streaming resmi yang tercatat di AniList.

**Komik (manga / manhwa / manhua)**
- Daftar update chapter Indonesia terbaru, populer, dan filter per tipe
  Manga / Manhwa / Manhua.
- Pencarian judul dengan prioritas judul yang punya chapter Indonesia.
- Detail komik: cover, judul alternatif, penulis, status, tahun, genre, sinopsis,
  pemilih bahasa chapter, dan daftar chapter lengkap beserta grup scanlation.
- **Reader bawaan**: mode gulir vertikal (ideal untuk manhwa/manhua) dan mode halaman,
  pinch-to-zoom pada mode halaman, indikator halaman, pindah chapter sebelumnya/berikutnya,
  serta mode hemat data.
- Chapter dari penerbit resmi (mis. MangaPlus) otomatis membuka tautan sumber resmi.
- Progres baca tersimpan otomatis per judul.

**Lainnya**
- Koleksi favorit anime + komik, riwayat baca dengan tombol lanjut baca.
- **Cek update chapter**: satu tombol di riwayat baca memeriksa chapter terbaru setiap
  judul di MangaDex dan menandai judul yang punya chapter baru.
- **Diagnostik koneksi** di Pengaturan untuk mengecek AniList, MangaDex, dan pemuatan gambar
  bila data gagal dimuat.
- **Baca offline**: di daftar chapter ada tombol **Unduh** per chapter (satu per satu, tanpa unduhan
  massal). Gambar disimpan di penyimpanan internal aplikasi, lalu bisa dibaca tanpa jaringan
  dari tab **Koleksi → Unduhan** atau langsung dari daftar chapter. Setiap unduhan bisa dihapus
  sendiri lewat ikon tempat sampah.
- **Tautan eksternal** di Pengaturan: daftar situs lain yang dibuka di browser HP, bukan di
  dalam aplikasi. Isi katalog tetap berasal dari API resmi, jadi tidak ada konten situs
  pihak ketiga yang diambil atau ditayangkan aplikasi.
- Tema gelap/terang/mengikuti sistem, pengaturan bahasa chapter, dan hapus riwayat.
- Semua teks antarmuka berbahasa Indonesia.

## Sumber data

| Konten | Sumber | Catatan |
| --- | --- | --- |
| Anime | [AniList GraphQL API](https://anilist.co) | Metadata, skor, trailer, rekomendasi |
| Komik | [MangaDex API](https://api.mangadex.org) | Chapter `translatedLanguage=id`, gambar dari server at-home MangaDex |

Aplikasi **tidak** menyimpan dan **tidak** menayangkan video anime. Untuk menonton dengan
subtitle Indonesia, aplikasi mengarahkan pengguna ke platform streaming resmi. Chapter
komik dibaca melalui API publik MangaDex beserta tautan resmi penerbit.

## Build dari source

Prasyarat: JDK 17, Android SDK dengan `platforms;android-36` dan `build-tools;36.0.0`.

```bash
# arahkan SDK bila belum ada local.properties
echo "sdk.dir=/path/ke/Android/Sdk" > local.properties

./gradlew assembleDebug      # APK debug
./gradlew assembleRelease    # APK release (belum ditandatangani)
```

APK hasil build ada di `app/build/outputs/apk/debug/app-debug.apk`.

Alternatif: buka folder proyek ini di Android Studio, tunggu Gradle sync, lalu **Run**.

## CI (GitHub Actions)

Workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) berjalan pada setiap
push ke `main`, pull request, dan bisa dipicu manual:

1. checkout repo dan memasang JDK 17 (Temurin),
2. `./gradlew assembleDebug`,
3. mengunggah APK sebagai artifact `aninusa-debug-apk` di tab **Actions** (tersimpan 30 hari).

Push tag versi juga membuat GitHub Release berisi APK:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Jadi APK bisa diunduh tanpa build lokal, langsung dari artifact Actions atau halaman Release.

## Pasang APK

```bash
adb install -r dist/AniNusa-1.0.7-debug.apk
```

Atau salin APK ke HP, aktifkan "Instal dari sumber tidak dikenal", lalu buka file APK.
APK ini ditandatangani dengan debug keystore, cocok untuk pemakaian pribadi dan pengujian.

## Pemecahan masalah

Bila muncul pesan data gagal dimuat padahal internet lancar, buka
**Pengaturan → Bantuan → Diagnostik koneksi** lalu tekan **Periksa**. Hasilnya menunjukkan
sumber mana yang gagal beserta pesannya:

- *Terlalu banyak permintaan (HTTP 429)* atau *Server sedang bermasalah (HTTP 5xx)* —
  server publik sedang membatasi; tunggu 1–2 menit lalu coba lagi.
- *Jaringan mengembalikan halaman web, bukan data API* — koneksi dibatasi operator
  seluler, penyedia internet, atau portal WiFi.
- *Koneksi aman (HTTPS) gagal* — periksa tanggal & waktu di HP.

Sejak 2026 MangaDex memakai Let's Encrypt dan AniList memakai Google Trust Services,
sedangkan akar sertifikat keduanya baru ada di trust store Android 7.1.1 ke atas. Karena itu
mulai versi 1.0.1 aplikasi membundel akar sertifikat publik tersebut (ISRG Root X1/X2,
GTS Root R1–R4, Amazon Root CA 1, DigiCert Global Root G2) dan menggabungkannya dengan
trust store bawaan perangkat. Verifikasi sertifikat tetap berjalan; tidak ada host yang
dilewati tanpa rantai sertifikat valid.

## Struktur kode

```
app/src/main/java/id/aninusa/app/
├── AniNusaApp.kt              # Application, inisialisasi penyimpanan lokal
├── MainActivity.kt
├── data/
│   ├── Http.kt                # OkHttp + cache respons + retry/backoff
│   ├── TlsRoots.kt            # akar sertifikat tambahan untuk Android 6/7.0
│   ├── Diagnostics.kt         # pemeriksaan koneksi tiap sumber data
│   ├── AniListApi.kt          # model + query anime
│   ├── MangaDexApi.kt         # model, mapping, endpoint komik
│   ├── LocalStore.kt          # favorit, progres baca, preferensi (SharedPreferences + JSON)
│   └── Errors.kt              # pesan error ramah pengguna
└── ui/
    ├── Navigation.kt          # NavHost + bottom navigation
    ├── Components.kt          # kartu, chip, status kosong/loading/error
    ├── theme/Theme.kt         # Material 3 gelap/terang
    ├── home/                  # beranda anime + komik
    ├── search/                # pencarian
    ├── anime/                 # detail anime
    ├── manga/                 # detail komik + daftar chapter
    ├── reader/                # pembaca chapter
    ├── library/               # favorit, riwayat, dan cek update chapter
    └── settings/              # pengaturan
```

Teknologi: Jetpack Compose (Material 3), Navigation Compose, OkHttp, kotlinx.serialization,
Coil (pemuatan gambar), ViewModel + StateFlow. Tanpa library dependency injection agar
proyek ringan.

## Batasan yang diketahui

- Butuh koneksi internet; tidak ada mode offline/cache chapter.
- Favorit dan progres baca disimpan lokal di perangkat (belum ada akun/sinkronisasi).
- Beberapa judul resmi tidak mengizinkan hosting chapter di MangaDex; aplikasi menampilkan
  tautan ke sumber resmi sebagai gantinya.
- MangaDex membatasi laju permintaan; aplikasi memakai cache singkat dan memuat data
  secara paralel untuk mengurangi permintaan.
