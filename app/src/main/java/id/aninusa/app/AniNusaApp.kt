package id.aninusa.app

import android.app.Application
import id.aninusa.app.data.LocalStore

class AniNusaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalStore.init(this)
    }
}
