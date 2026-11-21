package top.jlen.vod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryUndoTest {
    @Test fun undoKeepsNewQueriesFirstAndRestoresOlderOnesWithinTheHistoryLimit() {
        val prefs = InMemoryPreferences()
        val store = SearchHistoryStore(prefs)
        (1..14).forEach { store.save("影片$it") }
        store.save("Film")
        val previous = store.load()
        store.clear()
        store.save("FILM")
        store.save("新影片")
        store.restore(previous)

        assertEquals(listOf("新影片", "FILM") + (14 downTo 2).map { "影片$it" }, SearchHistoryStore(prefs).load())
    }
}
