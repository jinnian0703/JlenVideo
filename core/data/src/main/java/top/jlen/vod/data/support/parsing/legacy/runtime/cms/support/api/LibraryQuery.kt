package top.jlen.vod.data

internal fun buildLibraryQuery(
    typeId: String,
    cursor: String,
    filters: Map<String, String>,
    limit: Int
): Map<String, String> = linkedMapOf(
    "limit" to limit.coerceAtLeast(1).toString(),
    "sort" to "time",
    "cursor" to cursor
).apply {
    typeId.trim().takeIf(String::isNotBlank)?.let { put("type_id", it) }
    // 不允许筛选值覆盖分类、游标或分页参数。
    filters.forEach { (key, value) ->
        val normalizedKey = key.trim()
        val normalizedValue = value.trim()
        if (normalizedKey in setOf("class", "area", "lang", "year", "state", "version") && normalizedValue.isNotBlank()) {
            put(normalizedKey, normalizedValue)
        }
    }
}
