package top.jlen.vod.data

import android.content.Context
import okhttp3.OkHttpClient

class AppleCmsRepository(
    context: Context,
    cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    client: OkHttpClient = createClient(cookieJar)
) : LegacyAppleCmsRuntimeRepository(context, cookieJar, client) {
    fun peekDetailForApp(vodId: String): VodItem? {
        val normalizedId = vodId.trim()
        if (normalizedId.isBlank()) return null
        return runtimePeekDetailCacheEntry(normalizedId)
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeDetailCacheTtlMs()) }
            ?.value
            ?: runtimeFindPreviewItem(normalizedId)
    }

    companion object {
        fun clearAllCaches(context: Context) {
            LegacyAppleCmsRuntimeRepository.clearAllCaches(context)
        }

        fun clearMemoryCaches(context: Context) {
            LegacyAppleCmsRuntimeRepository.clearMemoryCaches(context)
        }
    }
}
