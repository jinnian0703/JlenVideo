package top.jlen.vod

import android.app.Application
import top.jlen.vod.data.AppleCmsRepository

class JlenVideoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppleCmsRepository.clearMemoryCaches(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            AppleCmsRepository.clearMemoryCaches(this)
        }
    }
}
