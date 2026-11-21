package top.jlen.vod.data

import com.google.gson.JsonObject
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ViewingFeatures(val danmaku: Boolean = false, val chat: Boolean = false, val live: Boolean = false)
data class EpisodeThread(val vod: String, val sourceIndex: Int, val episodeIndex: Int) {
    internal fun params() = mapOf("vod_id" to vod, "sid" to (sourceIndex + 1).toString(), "nid" to (episodeIndex + 1).toString())
}
data class TimedComment(val id: Int, val timeMs: Long, val text: String, val color: String, val type: Int)
data class ChatLine(val id: Int, val author: String, val text: String)
data class LiveCategory(val id: Int, val name: String)
data class LiveSource(val name: String, val url: String)
data class LiveChannel(val id: Int, val name: String, val category: String, val sources: List<LiveSource>)
data class ChannelPage(val channels: List<LiveChannel>, val nextOffset: Int?)

private fun JsonObject.rows(key: String) = get(key).safeArray()?.mapNotNull { it.safeObject() }.orEmpty()

suspend fun CmsAppApi.danmaku(thread: EpisodeThread): List<TimedComment> =
    request("danmaku/get_list", thread.params()).firstObject("info")?.rows("rows").orEmpty().mapNotNull { row ->
        val seconds = row.firstString("danmaku_time").toDoubleOrNull() ?: return@mapNotNull null
        val text = row.firstString("danmaku_text")
        if (!seconds.isFinite() || seconds < 0 || text.isBlank()) return@mapNotNull null
        TimedComment(row.firstInt("danmaku_id") ?: return@mapNotNull null, (seconds * 1000).toLong(), text,
            row.firstString("danmaku_color"), row.firstInt("danmaku_type") ?: 0)
    }.sortedBy { it.timeMs }

suspend fun CmsAppApi.sendDanmaku(user: String, thread: EpisodeThread, positionMs: Long, text: String) {
    request("danmaku/send", form = thread.params() + mapOf("time" to (positionMs.coerceAtLeast(0) / 1000.0).toString(),
        "text" to text.trim()), csrf = token(user))
}

suspend fun CmsAppApi.chat(vod: String, after: Int): List<ChatLine> =
    request("chatroom/get_list", mapOf("vod_id" to vod, "after_id" to after.toString())).firstObject("info")?.rows("rows").orEmpty().mapNotNull {
        ChatLine(it.firstInt("chat_id") ?: return@mapNotNull null, it.firstString("user_name"), it.firstString("chat_content"))
    }

suspend fun CmsAppApi.sendChat(user: String, vod: String, text: String) {
    request("chatroom/send", form = mapOf("vod_id" to vod, "content" to text.trim()), csrf = token(user))
}

suspend fun CmsAppApi.reportChat(user: String, id: Int) {
    request("chatroom/report", form = mapOf("chat_id" to id.toString()), csrf = token(user))
}

suspend fun CmsAppApi.liveCategories() = request("live/get_category").rows("list").mapNotNull {
    LiveCategory(it.firstInt("cate_id") ?: return@mapNotNull null, it.firstString("cate_name"))
}

private fun JsonObject.channel(): LiveChannel? {
    val id = firstInt("live_id") ?: return null
    val sources = rows("live_url_list").mapNotNull {
        val url = it.firstString("url").toHttpUrlOrNull() ?: return@mapNotNull null
        LiveSource(it.firstString("name").ifBlank { "默认线路" }, url.toString())
    }
    return LiveChannel(id, firstString("live_name"), firstString("cate_name"), sources)
}

suspend fun CmsAppApi.liveChannels(category: Int, offset: Int): ChannelPage {
    val json = request("live/get_list", mapOf("cate_id" to "$category", "offset" to "$offset", "limit" to "30"))
    val rows = json.rows("list")
    val next = offset + rows.size
    return ChannelPage(rows.mapNotNull { it.channel() }, next.takeIf { rows.isNotEmpty() && it < (json.firstInt("total") ?: next) })
}

suspend fun CmsAppApi.liveChannel(id: Int) = request("live/get_detail", mapOf("live_id" to "$id"))
    .firstObject("info")?.channel() ?: throw IOException("频道不可用")
