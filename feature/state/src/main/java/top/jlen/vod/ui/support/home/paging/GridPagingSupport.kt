package top.jlen.vod.ui

import top.jlen.vod.data.VodItem

internal fun List<VodItem>.initialGridVisibleCount(): Int =
    size.coerceAtMost(GRID_BATCH_ITEM_COUNT)

internal const val GRID_BATCH_ROWS = 12
internal const val GRID_BATCH_COLUMNS = 3
internal const val GRID_BATCH_ITEM_COUNT = GRID_BATCH_ROWS * GRID_BATCH_COLUMNS
