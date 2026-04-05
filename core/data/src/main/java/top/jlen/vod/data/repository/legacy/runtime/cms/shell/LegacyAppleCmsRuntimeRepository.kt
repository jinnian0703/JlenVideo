package top.jlen.vod.data
import android.content.Context
import okhttp3.OkHttpClient
open class LegacyAppleCmsRuntimeRepository(
    context: Context,
    cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    client: OkHttpClient = createClient(cookieJar)
) : LegacyAppleCmsRuntimeRepositoryCore(context, cookieJar, client) {
    companion object {
        fun clearAllCaches(context: Context) {
            LegacyAppleCmsRuntimeRepositoryCore.clearAllCaches(context)
        }
        fun clearMemoryCaches(context: Context) {
            LegacyAppleCmsRuntimeRepositoryCore.clearMemoryCaches(context)
        }
        internal fun createClient(cookieJar: PersistentCookieJar): OkHttpClient =
            LegacyAppleCmsRuntimeRepositoryCore.createClient(cookieJar)
    }
}