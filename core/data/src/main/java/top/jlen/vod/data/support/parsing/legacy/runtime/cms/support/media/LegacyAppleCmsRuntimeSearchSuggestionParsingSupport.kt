package top.jlen.vod.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun parseSearchSuggestionPage(root: JsonObject): SearchSuggestionPage {
    val payload = root.firstObject("data") ?: root
    val items = payload.firstArray("rows", "list", "items")
        .mapNotNull(::parseSearchSuggestionItem)
    return SearchSuggestionPage(
        engine = decodeSiteText(payload.firstString("engine", "source", "provider")),
        keyword = decodeSiteText(payload.firstString("keyword", "q", "wd")),
        limit = payload.firstInt("limit", "page_size") ?: items.size,
        items = items
    )
}

internal fun parseSearchSuggestionItem(element: JsonElement): SearchSuggestionItem? {
    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val title = decodeSiteText(obj.firstString("vod_name", "name", "title"))
    val highlight = decodeSiteText(
        obj.firstString("highlight", "vod_name_highlight", "name_highlight", "title_highlight")
    )
    return SearchSuggestionItem(
        vodId = obj.firstString("vod_id", "id"),
        title = title,
        subTitle = decodeSiteText(obj.firstString("vod_sub", "sub_title", "subtitle")),
        poster = obj.firstString("vod_pic", "pic", "poster"),
        remarks = decodeSiteText(obj.firstString("vod_remarks", "remarks", "remark")),
        typeId = obj.firstString("type_id", "cate_id", "category_id"),
        typeName = decodeSiteText(obj.firstString("type_name", "category_name")),
        typeParentName = decodeSiteText(obj.firstString("type_parent_name", "parent_type_name")),
        highlight = highlight.ifBlank { title }
    ).takeIf { item ->
        item.vodId.isNotBlank() || item.title.isNotBlank() || item.highlight.isNotBlank()
    }
}
