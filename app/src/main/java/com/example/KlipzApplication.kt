package com.example

import android.app.Application
import com.example.di.KlipzAppContainer

class KlipzApplication : Application() {

    lateinit var container: KlipzAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = KlipzAppContainer(this)
        // Warm up embedded Python + yt-dlp + QuickJS/EJS + FFmpeg runtime off the main thread
        container.ytDlpBridge.initializeAsync()
    }
}
