package top.jlen.vod.data

import android.content.Context
import okhttp3.OkHttpClient

class AppleCmsRepository(
    context: Context,
    cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    client: OkHttpClient = createClient(cookieJar)
) : LegacyAppleCmsRuntimeRepository(context, cookieJar, client) {
    // Interactive playback/download decisions must use current server state, not a catalog preview.
    suspend fun mediaVodId(routeId: String): String {
        val id = routeId.trim()
        if (id.toLongOrNull()?.let { it > 0 } == true) return id
        val siteId = loadDetail(id, forceRefresh = true)?.siteVodId.orEmpty()
        return siteId.takeIf { it.toLongOrNull()?.let { number -> number > 0 } == true }
            ?: throw java.io.IOException("无法确认影片编号，请从片库重新打开")
    }

    suspend fun loadMediaDetailForApp(routeId: String): VodItem? {
        val id = mediaVodId(routeId)
        val item = runtimeRequestDetailApi(id)?.takeIf { it.vodId == id } ?: return null
        return appApi.checkMediaAccess(item)
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
