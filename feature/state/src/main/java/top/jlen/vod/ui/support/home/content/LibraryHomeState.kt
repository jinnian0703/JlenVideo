package top.jlen.vod.ui

// 首页刷新不能把片库的“全部”或其他筛选结果重置回首个分类。
internal fun homeStateKeepingLibrary(fresh: HomeUiState, current: HomeUiState): HomeUiState {
    val category = current.selectedCategory ?: return fresh
    val updatedCategory = fresh.categories.firstOrNull { it.typeId == category.typeId } ?: category
    return fresh.copy(
        selectedCategory = updatedCategory,
        selectedCategoryFilters = current.selectedCategoryFilters,
        categoryVideos = current.categoryVideos,
        categoryVisibleCount = current.categoryVisibleCount,
        categoryCursor = current.categoryCursor,
        hasMoreCategoryItems = current.hasMoreCategoryItems,
        categoryFirstLoaded = current.categoryFirstLoaded,
        isCategoryLoading = current.isCategoryLoading,
        isCategoryAppending = current.isCategoryAppending,
        categoryAppendError = current.categoryAppendError
    )
}
