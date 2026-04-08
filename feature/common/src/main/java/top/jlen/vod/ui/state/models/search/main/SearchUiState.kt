package top.jlen.vod.ui

import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.SearchSuggestionItem
import top.jlen.vod.data.VodItem

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isHotSearchLoading: Boolean = false,
    val error: String? = null,
    val appendError: String? = null,
    val hotSearchError: String? = null,
    val suggestError: String? = null,
    val history: List<String> = emptyList(),
    val hotSearchGroups: List<HotSearchGroup> = emptyList(),
    val suggestions: List<SearchSuggestionItem> = emptyList(),
    val results: List<VodItem> = emptyList(),
    val cursor: String = "",
    val hasMore: Boolean = false,
    val firstLoaded: Boolean = false,
    val isSuggestLoading: Boolean = false,
    val suggestSubmittedQuery: String = ""
)
