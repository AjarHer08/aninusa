package id.aninusa.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import id.aninusa.app.data.Http
import id.aninusa.app.data.LocalStore
import id.aninusa.app.data.TlsRoots

class AniNusaApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        TlsRoots.install(this)
        LocalStore.init(this)
    }

    /** Gambar komik diunduh dengan klien yang sama, termasuk trust store tambahan. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .build()
}
