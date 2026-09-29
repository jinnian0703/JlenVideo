package top.jlen.vod.data

// 空分类 ID 代表不限制分类；请求时省略 type_id，而不是发给服务端一个虚构 ID。
val ALL_LIBRARY_CATEGORY = AppleCmsCategory(typeName = "全部")

fun libraryCategoryOptions(categories: List<AppleCmsCategory>): List<AppleCmsCategory> =
    listOf(ALL_LIBRARY_CATEGORY) + categories.filter { it.typeId.isNotBlank() }.distinctBy { it.typeId }

fun libraryFilterGroups(
    category: AppleCmsCategory?,
    categories: List<AppleCmsCategory>
): List<CategoryFilterGroup> = when {
    category == null -> emptyList()
    category.typeId.isNotBlank() -> category.filterGroups
    else -> categories.flatMap { it.filterGroups }.groupBy { it.key }.map { (key, groups) ->
        CategoryFilterGroup(key, groups.first().label, groups.flatMap { it.options }.distinct())
    }
}

fun libraryFilterSummary(category: AppleCmsCategory?, groups: List<CategoryFilterGroup>, filters: Map<String, String>): String =
    (listOf(category?.typeName?.takeIf(String::isNotBlank) ?: "全部") +
        groups.mapNotNull { filters[it.key]?.takeIf(String::isNotBlank) }).joinToString(" · ")
