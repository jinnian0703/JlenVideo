package top.jlen.vod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HotSearchFeedsTest {
    @Test fun tencentSelectsOverallRankEvenIfAnotherChannelIsFirst() {
        val json = """{"data":{"Results":[{"ChannelID":2,"Result":{"ListInfo":[{"Query":"剧集榜"}]}},
            {"ChannelID":"0","Result":{"ListInfo":[{"Query":"甲"},{"Query":""},{"Query":"甲"},{"Query":"乙"},{"Query":"丙"}]}}]}}"""
        val items = parseTencentHotSearch(json, 2).items
        assertEquals(listOf(1 to "甲", 2 to "乙"), items.map { it.rank to it.keyword })
    }

    @Test fun mgtvCombinesBothSectionsInRankOrderBeforeLimiting() {
        val json = """{"data":{"toplist":{"list":[{"label":"综艺","listWithPic":[{"index":1,"clipName":"分类榜"}]},
            {"label":"热门内容","listOnlyText":[{"index":4,"clipName":"丁"},{"index":3,"clipName":"丙"}],
            "listWithPic":[{"index":1,"clipName":"甲"},{"index":2,"clipName":"乙"}]}]}}}"""
        assertEquals(listOf("甲", "乙", "丙"), parseMgtvHotSearch(json, 3).items.map { it.keyword })
    }
}
