package top.jlen.vod.data

import android.content.Context
import okhttp3.OkHttpClient

class AppleCmsRepository(
    context: Context,
    cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    client: OkHttpClient = createClient(cookieJar)
) : LegacyAppleCmsRepository(context, cookieJar, client)
