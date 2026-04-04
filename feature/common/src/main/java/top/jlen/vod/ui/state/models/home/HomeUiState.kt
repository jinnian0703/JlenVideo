package top.jlen.vod.ui

import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.HomeSection
import top.jlen.vod.data.VodItem

data class HomeUiState(
    val isLoading: Boolean = true,
    val isHomeAppending: Boolean = false,
    val isCategoryLoading: Boolean = false,
    val isCategoryAppending: Boolean = false,
    val error: String? = null,
    val homeAppendError: String? = null,
    val categoryAppendError: String? = null,
    val slides: List<VodItem> = emptyList(),
    val hot: List<VodItem> = emptyList(),
    val featured: List<VodItem> = emptyList(),
    val latest: List<VodItem> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val homeVisibleCount: Int = 0,
    val homeCursor: String = "",
    val hasMoreHomeItems: Boolean = false,
    val homeFirstLoaded: Boolean = false,
    val categories: List<AppleCmsCategory> = emptyList(),
    val selectedCategory: AppleCmsCategory? = null,
    val selectedCategoryFilters: Map<String, String> = emptyMap(),
    val categoryVideos: List<VodItem> = emptyList(),
    val categoryVisibleCount: Int = 0,
    val categoryCursor: String = "",
    val hasMoreCategoryItems: Boolean = false,
    val categoryFirstLoaded: Boolean = false
) {
    val visibleLatest: List<VodItem>
        get() = latest.take(homeVisibleCount.coerceIn(0, latest.size))

    val hasMoreLatest: Boolean
        get() = homeVisibleCount < latest.size || hasMoreHomeItems

    val visibleCategoryVideos: List<VodItem>
        get() = categoryVideos.take(categoryVisibleCount.coerceIn(0, categoryVideos.size))

    val hasMoreCategoryVideos: Boolean
        get() = categoryVisibleCount < categoryVideos.size || hasMoreCategoryItems

    val categoryFilterGroups: List<CategoryFilterGroup>
        get() = selectedCategory?.filterGroups.orEmpty()
}
