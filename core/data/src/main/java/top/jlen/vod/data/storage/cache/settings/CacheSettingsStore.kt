package top.jlen.vod.data

import android.content.Context
import androidx.core.content.edit

class CacheSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CacheSettings =
        CacheSettings(
            retention = CacheRetentionOption.fromKey(prefs.getString(KEY_RETENTION, null))
        )

    fun saveRetention(option: CacheRetentionOption): CacheSettings {
        prefs.edit { putString(KEY_RETENTION, option.key) }
        return CacheSettings(retention = option)
    }

    companion object {
        private const val PREFS_NAME = "app_cache_settings"
        private const val KEY_RETENTION = "retention"
    }
}
