package top.jlen.vod

import android.app.Application

class JlenVideoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
