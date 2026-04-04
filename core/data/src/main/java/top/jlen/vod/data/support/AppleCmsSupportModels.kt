package top.jlen.vod.data

data class HomePayload(
    val slides: List<VodItem>,
    val hot: List<VodItem>,
    val featured: List<VodItem>,
    val latest: List<VodItem>,
    val sections: List<HomeSection>,
    val categories: List<AppleCmsCategory>,
    val selectedCategory: AppleCmsCategory?,
    val categoryVideos: List<VodItem>,
    val latestCursor: String,
    val latestHasMore: Boolean,
    val categoryCursor: String,
    val categoryHasMore: Boolean
)

data class HomeSection(
    val title: String,
    val typeId: String,
    val items: List<VodItem>
)

data class PagedVodItems(
    val items: List<VodItem>,
    val page: Int,
    val pageCount: Int,
    val totalItems: Int,
    val limit: Int,
    val hasNextPage: Boolean
)

data class ResolvedPlayUrl(
    val url: String,
    val useWebPlayer: Boolean
)

internal data class PlayRoute(
    val sid: String,
    val nid: String
)

internal fun compareVersionNames(left: String, right: String): Int {
    val leftParts = left.removePrefix("v").split('.', '-', '_')
    val rightParts = right.removePrefix("v").split('.', '-', '_')
    val maxSize = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
        val rightPart = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }
    return 0
}
