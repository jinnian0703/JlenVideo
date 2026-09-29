package top.jlen.vod.data

import org.junit.Assert.*
import org.junit.Test

class LibraryQueryTest {
    @Test fun allCategoriesOmitsTypeIdAndSupportsCursorAndFilters() {
        val query = buildLibraryQuery("", "next-page", mapOf("area" to "中国", "year" to "2026"), 36)
        assertFalse(query.containsKey("type_id"))
        assertEquals("next-page", query["cursor"])
        assertEquals("中国", query["area"])
        assertEquals("2026", query["year"])
    }
    @Test fun specificCategoryKeepsIdAndCleansBlankFilters() {
        val query = buildLibraryQuery(" 12 ", "", mapOf("class" to " 喜剧 ", "year" to ""), 36)
        assertEquals("12", query["type_id"])
        assertEquals("喜剧", query["class"])
        assertFalse(query.containsKey("year"))
    }
    @Test fun filtersCannotOverridePaginationOrCategory() {
        val query = buildLibraryQuery("12", "cursor", mapOf("type_id" to "99", "cursor" to "bad", "limit" to "0"), 36)
        assertEquals("12", query["type_id"])
        assertEquals("cursor", query["cursor"])
        assertEquals("36", query["limit"])
    }
    @Test fun preservesStateAndVersionFilters() {
        val query = buildLibraryQuery("", "", mapOf("state" to "完结", "version" to "高清"), 36)
        assertEquals("完结", query["state"])
        assertEquals("高清", query["version"])
    }
}
