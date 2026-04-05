package top.jlen.vod

object AppConfig {
    val appleCmsBaseUrl: String
        get() = top.jlen.vod.core.common.BuildConfig.APPLE_CMS_BASE_URL
}

object AppRuntimeInfo {
    @Volatile
    private var versionNameOverride: String? = null

    @Volatile
    private var versionCodeOverride: Int? = null

    @Volatile
    private var applicationIdOverride: String? = null

    fun initialize(
        versionName: String,
        versionCode: Int,
        applicationId: String
    ) {
        versionNameOverride = versionName
        versionCodeOverride = versionCode
        applicationIdOverride = applicationId
    }

    val versionName: String
        get() = versionNameOverride ?: top.jlen.vod.core.common.BuildConfig.APP_VERSION_NAME

    val versionCode: Int
        get() = versionCodeOverride ?: top.jlen.vod.core.common.BuildConfig.APP_VERSION_CODE

    val applicationId: String
        get() = applicationIdOverride ?: top.jlen.vod.core.common.BuildConfig.APP_APPLICATION_ID
}

const val PLAYER_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36"
