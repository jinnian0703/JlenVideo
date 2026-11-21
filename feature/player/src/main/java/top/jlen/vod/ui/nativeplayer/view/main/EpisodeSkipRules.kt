package top.jlen.vod.ui

import android.content.SharedPreferences
import top.jlen.vod.data.normalizePlaybackSourceKey

internal fun episodeSkipKey(vodId: String, sourceName: String, sourceIndex: Int): String? {
    val id = vodId.trim().ifEmpty { return null }
    return "${id.length}:$id:${normalizePlaybackSourceKey(sourceName, sourceIndex)}"
}

internal data class EpisodeSkipRules(val introEndMs: Long = 0, val outroLengthMs: Long = 0) {
    fun fits(duration: Long): Boolean = duration > 0 && introEndMs in 0 until duration &&
        outroLengthMs >= 0 && outroLengthMs < duration - introEndMs

    fun save(prefs: SharedPreferences, key: String) {
        val edit = prefs.edit()
        if (introEndMs > 0) edit.putLong("intro:$key", introEndMs) else edit.remove("intro:$key")
        if (outroLengthMs > 0) edit.putLong("outro:$key", outroLengthMs) else edit.remove("outro:$key")
        edit.apply()
    }

    companion object {
        fun load(prefs: SharedPreferences, key: String) = EpisodeSkipRules(
            prefs.getLong("intro:$key", 0), prefs.getLong("outro:$key", 0)
        )
    }
}

/** A manual rewind after the opening was skipped should still allow watching it. */
internal class EpisodeSkipSession(private val rules: EpisodeSkipRules) {
    private var passedIntro = false
    private var skippedOutro = false

    fun seekTarget(position: Long, duration: Long, hasNext: Boolean): Long? {
        if (position !in 0 until duration || !rules.fits(duration)) return null
        if (!passedIntro) {
            passedIntro = true
            if (position < rules.introEndMs) return rules.introEndMs
        }
        if (!skippedOutro && hasNext && rules.outroLengthMs > 0 && position >= duration - rules.outroLengthMs) {
            skippedOutro = true
            // Let the existing end-of-media listener persist completion and advance exactly once.
            return duration
        }
        return null
    }
}
