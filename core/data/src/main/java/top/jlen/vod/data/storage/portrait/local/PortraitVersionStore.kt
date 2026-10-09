package top.jlen.vod.data

import android.content.Context
import android.content.SharedPreferences

/** 头像版本仅在上传成功后推进，跨页面和应用重启保持稳定。 */
internal class PortraitVersionStore(
    private val prefs: SharedPreferences,
    private val site: String
) {
    constructor(context: Context, site: String) : this(
        context.applicationContext.getSharedPreferences("portrait_version_store", Context.MODE_PRIVATE),
        site
    )

    fun version(userId: String): Long =
        if (userId.isBlank()) 0L else prefs.getLong(key(userId), 0L)

    fun markUpdated(userId: String) {
        if (userId.isBlank()) return
        synchronized(lock) {
            // 同一毫秒内再次更换，或系统时间回退时，也不能复用旧版本。
            val next = maxOf(System.currentTimeMillis(), version(userId) + 1L)
            prefs.edit().putLong(key(userId), next).apply()
        }
    }

    private fun key(userId: String): String = "${site.trimEnd('/')}::${userId.trim()}"

    private companion object {
        val lock = Any()
    }
}
