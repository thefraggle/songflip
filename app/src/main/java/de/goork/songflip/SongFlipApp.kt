package de.goork.songflip

import android.app.Application
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.ProManager

class SongFlipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinkCacheManager.init(this)
        ProManager.init(this)
    }
}
