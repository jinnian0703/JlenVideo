package top.jlen.vod.data

import android.content.Context
import okhttp3.OkHttpClient

class AppleCmsRepository(
    context: Context,
    cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    client: OkHttpClient = createClient(cookieJar)
) : LegacyAppleCmsRuntimeRepository(context, cookieJar, client) {
    companion object {
        fun clearAllCaches(context: Context) {
            LegacyAppleCmsRuntimeRepository.clearAllCaches(context)
        }

        fun clearMemoryCaches(context: Context) {
            LegacyAppleCmsRuntimeRepository.clearMemoryCaches(context)
        }
    }
}
