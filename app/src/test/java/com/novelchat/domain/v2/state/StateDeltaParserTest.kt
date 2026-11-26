package com.novelchat.domain.v2.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDeltaParserTest {

    private val parser = StateDeltaParser()

    @Test
    fun parse_handlesEmptyMinimalJsonAndMarkdownTrailingComma() {
        val minimal = parser.parse("""{"summary":"无变化"}""")
        assertEquals("无变化", minimal.summary)

        val messy = parser.parse(
            """
            ```json
            {
              "summary": "林克买了咖啡",
              "player": {
                "fields": {"stamina": 78, "stress": "低"},
              },
              "npcs": [
                {"name": "许棠", "favor_delta": "3", "memory": "林克买了冰美式",}
              ],
              "departed": [{"name": "陈姐", "permanent": "false"}],
              "suggestions": "回出租屋整理简历",
            }
            ```
            """.trimIndent()
        )

        assertEquals("林克买了咖啡", messy.summary)
        assertEquals("78", messy.player?.fields?.get("stamina"))
        assertEquals(3, messy.npcs?.single()?.favorDelta)
        assertEquals(false, messy.departed?.single()?.permanent)
        assertEquals(listOf("回出租屋整理简历"), messy.suggestions)
    }

    @Test(expected = StateSyncParseException::class)
    fun parse_throwsWhenNoValidJsonObjectExists() {
        parser.parse("这只是一段纯文本说明，没有任何对象")
    }
}
