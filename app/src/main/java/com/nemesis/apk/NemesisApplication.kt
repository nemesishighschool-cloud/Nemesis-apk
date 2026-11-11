package com.nemesis.apk

import android.app.Application
import com.nemap.android.EmbeddedRuntime

class NemesisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization is deferred to the Activity or the sticky service.
        // Heavy WebView/Python startup here can prevent startForeground within Android's deadline.
    }
}
