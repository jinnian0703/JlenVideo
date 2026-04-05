package top.jlen.vod

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import top.jlen.vod.data.AppleCmsRepository

class JlenVideoApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppRuntimeInfo.initialize(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            applicationId = BuildConfig.APPLICATION_ID
        )
        CrashLogger.install(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.04)
                    .build()
            }
            .build()

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
