package top.jlen.vod.data

import android.content.Context
import androidx.core.content.edit

class CacheSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CacheSettings =
        CacheSettings(
            retention = CacheRetentionOption.fromKey(prefs.getString(KEY_RETENTION, null)),
            sizeLimit = CacheSizeLimitOption.fromKey(prefs.getString(KEY_SIZE_LIMIT, null))
        )

    fun saveRetention(option: CacheRetentionOption): CacheSettings {
        prefs.edit { putString(KEY_RETENTION, option.key) }
        return load()
    }

    fun saveSizeLimit(option: CacheSizeLimitOption): CacheSettings {
        prefs.edit { putString(KEY_SIZE_LIMIT, option.key) }
        return load()
    }

    companion object {
        private const val PREFS_NAME = "app_cache_settings"
        private const val KEY_RETENTION = "retention"
        private const val KEY_SIZE_LIMIT = "size_limit"
    }
}
