package com.novelchat.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonResponseParserTest {

    private val parser = JsonResponseParser()

    @Test
    fun parseNarrative_extractsJsonFromMarkdown() {
        val raw = """
            ```json
            {"narrative":"城门在雨里缓缓打开。","summary":"主角抵达城门。"}
            ```
        """.trimIndent()

        val result = parser.parseNarrative(raw)

        assertEquals("城门在雨里缓缓打开。", result.narrative)
        assertEquals("主角抵达城门。", result.summary)
    }

    @Test
    fun parseNarrative_readsActionCoverage() {
        val raw = """
            {
              "narrative": "林克付清房租，又拨通林越的电话。",
              "summary": "林克缴租后联系林越。",
              "action_coverage": [
                {"action": "支付房租", "evidence": "林克付清房租"},
                {"action": "联系林越", "evidence": "拨通林越的电话"}
              ],
              "forward_hook": {"cause":"电话接通后的异常背景声","evidence":"拨通林越的电话"},
              "ending_scene": {"location":"云桥里老小区","present_characters":["林越"]},
              "introduced_characters": [
                {"name":"苏晚","occupation":"插画师","relationship_to_player":"女朋友","first_appearance":"苏晚在家休息"}
              ]
            }
        """.trimIndent()

        val result = parser.parseNarrative(raw)

        assertEquals(2, result.actionCoverage.size)
        assertEquals("联系林越", result.actionCoverage.last().action)
        assertEquals("电话接通后的异常背景声", result.forwardHook.cause)
        assertEquals("云桥里老小区", result.endingScene.location)
        assertEquals(listOf("林越"), result.endingScene.presentCharacters)
        assertEquals("苏晚", result.introducedCharacters.single().name)
        assertEquals("女朋友", result.introducedCharacters.single().relationshipToPlayer)
    }

    @Test
    fun parseNarrative_acceptsCamelCaseFields() {
        val result = parser.parseNarrative(
            """{
              "narrative":"林克把彩票递给周晴核验。",
              "actionCoverage":[{"action":"兑奖","evidence":"把彩票递给周晴核验"}],
              "forwardHook":{"cause":"巨额奖金触发复核","evidence":"周晴核验"},
              "endingScene":{"location":"彩票店","presentCharacters":["周晴"]},
              "introducedCharacters":[{"name":"周晴","occupation":"彩票店店员","relationshipToPlayer":"初识","firstAppearance":"周晴核验彩票"}]
            }""".trimIndent()
        )

        assertEquals("兑奖", result.actionCoverage.single().action)
        assertEquals(listOf("周晴"), result.endingScene.presentCharacters)
        assertEquals("初识", result.introducedCharacters.single().relationshipToPlayer)
    }

    @Test
    fun parseNarrative_replacesExplicitNullsWithModelDefaults() {
        val result = parser.parseNarrative(
            """{"narrative":"林克走进咖啡厅。","summary":null,"action_coverage":null,"forward_hook":null}"""
        )

        assertEquals("", result.summary)
        assertEquals(0, result.actionCoverage.size)
        assertEquals("", result.forwardHook.evidence)
    }

    @Test
    fun parseNarrative_recoversParagraphsFromMalformedJsonResponse() {
        val raw = """
            {
              "narrative": "林克拨通林越的电话。",
              "他听见了地铁报站声。",
              "随后林克赶到会议室。",
              "summary": "林克联系林越后抵达会议室。",
              "action_coverage": [
                {"action":"联系林越","evidence":"林克拨通林越的电话"}
              ]
            }
        """.trimIndent()

        val result = parser.parseNarrative(raw)

        assertEquals(
            "林克拨通林越的电话。\n\n他听见了地铁报站声。\n\n随后林克赶到会议室。",
            result.narrative
        )
        assertEquals("林克联系林越后抵达会议室。", result.summary)
        assertEquals("联系林越", result.actionCoverage.first().action)
    }

    @Test
    fun parseTaskAssignment_rejectsInvalidJson() {
        val rawResponse = "任务：什么都不用做 " + "sk-" + "sensitive-placeholder"
        val error = assertThrows(TaskAssignmentParseException::class.java) {
            parser.parseTaskAssignment(rawResponse)
        }

        assertEquals(TaskAssignmentParseFailure.NO_JSON, error.failure)
        assertEquals(true, error.message?.contains("任务分配未返回有效 JSON") == true)
        assertFalse(error.message.orEmpty().contains(rawResponse))
    }

    @Test
    fun parseTaskAssignment_rejectsUnrelatedJsonObject() {
        val error = assertThrows(TaskAssignmentParseException::class.java) {
            parser.parseTaskAssignment("{\"message\":\"done\"}")
        }

        assertEquals(TaskAssignmentParseFailure.MISSING_FIELDS, error.failure)
        assertEquals(true, error.message?.contains("未包含任务分配字段") == true)
    }

    @Test
    fun parseTaskAssignment_reportsInvalidRootWithoutEchoingResponse() {
        val error = assertThrows(TaskAssignmentParseException::class.java) {
            parser.parseTaskAssignment("[]")
        }

        assertEquals(TaskAssignmentParseFailure.ROOT_SHAPE, error.failure)
        assertFalse(error.message.orEmpty().contains("[]"))
    }

    @Test
    fun parseTaskAssignment_usesFinalObjectWhenModelReturnsExampleFirst() {
        val raw = """
            示例：{"events":[]}
            最终答案：{"story_update":{"current_location":"彩票店"},"events":[]}
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals("彩票店", result.storyUpdate?.currentLocation)
    }

    @Test
    fun parseTaskAssignment_extractsJsonFromText() {
        val raw = """
            根据剧情分析，任务如下：
            ```json
            {
              "new_characters": [],
              "retired_characters": [],
              "character_updates": [],
              "worldbook_updates": [],
              "item_transfers": [],
              "events": [
                {"type": "工作压力", "target": "近岸商务区", "description": "临时会议打乱计划", "caused_by": "同事林越"}
              ]
            }
            ```
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals(1, result.events.size)
        assertEquals("工作压力", result.events.first().type)
        assertEquals("近岸商务区", result.events.first().target)
    }

    @Test
    fun parseTaskAssignment_cleansTrailingCommasOnAndroidRegexEngine() {
        val raw = """
            任务如下：
            {
              "new_characters": [],
              "retired_characters": [],
              "character_updates": [],
              "worldbook_updates": [],
              "item_transfers": [],
              "events": [
                {"type": "兑奖", "target": "彩票店", "description": "店员核验彩票后震惊",},
              ],
            }
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals(1, result.events.size)
        assertEquals("兑奖", result.events.first().type)
        assertEquals("彩票店", result.events.first().target)
    }

    @Test
    fun parseTaskAssignment_normalizesWrapperAndSingletonObjects() {
        val raw = """
            ```json
            {
              "task_assignment": {
                "story_update": {"current_location": "彩票店"},
                "character_updates": {"name": "店员阿远", "reason": "核验中奖彩票"},
                "events": null,
              }
            }
            ```
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals("彩票店", result.storyUpdate?.currentLocation)
        assertEquals(1, result.characterUpdates.size)
        assertEquals("店员阿远", result.characterUpdates.first().name)
        assertEquals(0, result.events.size)
    }

    @Test
    fun parseTaskAssignment_acceptsCamelCaseDeepSeekPayload() {
        val result = parser.parseTaskAssignment(
            """{
              "taskAssignment": {
                "storyUpdate": {"currentLocation":"彩票店","pendingEvents":["等待省中心复核"]},
                "playerUpdate": {"changes":{"mentalState":{"currentThoughts":"核对兑奖手续"},"storyProgress":{"currentGoal":"完成复核"}}},
                "characterUpdates": {"name":"周晴","reason":"确认巨额奖金"},
                "events": [{"type":"兑奖复核","target":"彩票店","causedBy":"林克中奖","description":"周晴启动二次复核"}],
                "syncStatus":"updated"
              }
            }""".trimIndent()
        )

        assertEquals("彩票店", result.storyUpdate?.currentLocation)
        assertEquals(listOf("等待省中心复核"), result.storyUpdate?.pendingEvents)
        assertEquals("核对兑奖手续", result.playerUpdate?.changes?.mentalState?.currentThoughts)
        assertEquals("完成复核", result.playerUpdate?.changes?.storyProgress?.currentGoal)
        assertEquals("周晴", result.characterUpdates.single().name)
        assertEquals("林克中奖", result.events.single().causedBy)
        assertEquals("updated", result.syncStatus)
    }

    @Test
    fun parseTaskAssignment_replacesNullRequiredStringsWithDefaults() {
        val result = parser.parseTaskAssignment(
            """{"character_updates":[{"name":null,"reason":null}],"events":[]}"""
        )

        assertEquals("", result.characterUpdates.single().name)
        assertEquals("", result.characterUpdates.single().reason)
    }

    @Test
    fun parseTaskAssignment_normalizesStringEventsReturnedByDeepSeek() {
        val raw = """
            {
              "story_update": null,
              "player_update": null,
              "new_characters": [],
              "retired_characters": [],
              "character_updates": [],
              "worldbook_updates": [],
              "item_transfers": [],
              "events": ["林克支付了房租", "林越解释了无法出席会议的原因"]
            }
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals(2, result.events.size)
        assertEquals("剧情事件", result.events.first().type)
        assertEquals("林克支付了房租", result.events.first().description)
        assertEquals("林越解释了无法出席会议的原因", result.events.last().description)
    }

    @Test
    fun parseTaskAssignment_normalizesStringCharacterUpdatesReturnedByDeepSeek() {
        val raw = """
            {
              "character_updates": ["房东陈姐：收到房租后态度缓和"],
              "events": []
            }
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals(1, result.characterUpdates.size)
        assertEquals("房东陈姐", result.characterUpdates.first().name)
        assertEquals("收到房租后态度缓和", result.characterUpdates.first().reason)
    }

    @Test
    fun parseTaskAssignment_dropsNonNumericRiskDescription() {
        val raw = """
            {
              "player_update": {
                "reason": "客户评价与交付偏差带来职业风险",
                "changes": {"risk": "客户评价与交付偏差带来职业风险"}
              },
              "events": []
            }
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals("客户评价与交付偏差带来职业风险", result.playerUpdate?.reason)
        assertEquals(null, result.playerUpdate?.changes?.risk)
    }

    @Test
    fun parseTaskAssignment_normalizesNumericStringsAndBounds() {
        val raw = """
            {
              "player_update": {
                "reason": "会议压力增加",
                "changes": {
                  "risk": "风险值 135",
                  "physical_state": {"stamina": "82点"}
                }
              },
              "item_transfers": [{"item":"客户纪要","quantity":"2份","from":"林越","to":"林克"}],
              "events": []
            }
        """.trimIndent()

        val result = parser.parseTaskAssignment(raw)

        assertEquals(135, result.playerUpdate?.changes?.risk)
        assertEquals(82, result.playerUpdate?.changes?.physicalState?.stamina)
        assertEquals(2, result.itemTransfers.first().quantity)
    }

    @Test
    fun parseTaskAssignment_keepsRelationshipFieldsIncremental() {
        val result = parser.parseTaskAssignment(
            """{"player_update":{"changes":{"relationships":{"苏晚":{"favor":48}}}}}"""
        )

        val patch = result.playerUpdate?.changes?.relationships?.get("苏晚")
        assertEquals(48, patch?.favor)
        assertEquals(null, patch?.trust)
        assertEquals(null, patch?.stage)
    }

    @Test
    fun parseTaskAssignment_acceptsCertifiedNoChangePayload() {
        val result = parser.parseTaskAssignment(
            """{"sync_status":"no_change","no_change_reason":"本轮仅补充环境观察，没有形成可持久化状态变化"}"""
        )

        assertEquals("no_change", result.syncStatus)
        assertEquals("本轮仅补充环境观察，没有形成可持久化状态变化", result.noChangeReason)
    }

    @Test
    fun parseTaskAssignment_normalizesSmallModelFieldShapes() {
        val result = parser.parseTaskAssignment(
            """{
              "story_update": {
                "current_location": 7,
                "active_characters": "周明、林克",
                "pending_events": {"description":"前往A07窗口登记"}
              },
              "player_update": {
                "reason": 123,
                "risk": "风险 120",
                "mental_state": {"focus": 9},
                "story_progress": {"current_goal": true}
              },
              "new_characters": [{
                "name":"周明",
                "age":"34岁",
                "occupation":9,
                "initial_status":{"physical_state":{"stamina":"坏数据"}}
              }],
              "worldbook_updates":[{"action":"create","name":"A07窗口","keywords":"彩票、登记"}],
              "events":{"type":7,"description":"周明递出登记册"},
              "sync_status":"updated",
              "no_change_reason":false
            }""".trimIndent()
        )

        assertEquals("7", result.storyUpdate?.currentLocation)
        assertEquals(listOf("周明", "林克"), result.storyUpdate?.activeCharacters)
        assertEquals(listOf("前往A07窗口登记"), result.storyUpdate?.pendingEvents)
        assertEquals("123", result.playerUpdate?.reason)
        assertEquals(120, result.playerUpdate?.changes?.risk)
        assertEquals("9", result.playerUpdate?.changes?.mentalState?.focus)
        assertEquals("true", result.playerUpdate?.changes?.storyProgress?.currentGoal)
        assertEquals(34, result.newCharacters.single().age)
        assertEquals("9", result.newCharacters.single().occupation)
        assertEquals(null, result.newCharacters.single().initialStatus)
        assertEquals(listOf("彩票", "登记"), result.worldbookUpdates.single().keywords)
        assertEquals("7", result.events.single().type)
        assertEquals("false", result.noChangeReason)
    }

    @Test
    fun parseTaskAssignment_acceptsQuotedAndArrayWrappedObjects() {
        val result = parser.parseTaskAssignment(
            """{
              "story_update":[{"current_location":"省福彩中心"}],
              "player_update":{"changes":"{\"mental_state\":{\"focus\":\"办理兑奖\"}}"},
              "events":[]
            }""".trimIndent()
        )

        assertEquals("省福彩中心", result.storyUpdate?.currentLocation)
        assertEquals("办理兑奖", result.playerUpdate?.changes?.mentalState?.focus)
    }

    @Test
    fun parseCharacterStatePatch_readsIncrementalFields() {
        val raw = """
            {
              "emotion": "震惊",
              "physical_state": {"stamina": 82},
              "mental_state": {"stress": "高", "focus": "核对中奖彩票"},
              "attributes": {"personality": "经历冲击后更谨慎", "arcane_affinity": 7},
              "relationship_to_player": {"favor_delta": 3, "trust_delta": 1, "stage": "初识"},
              "inventory_changes": []
            }
        """.trimIndent()

        val result = parser.parseCharacterStatePatch(raw)

        assertEquals("震惊", result.emotion)
        assertEquals(82, result.physicalState?.stamina)
        assertEquals("高", result.mentalState?.stress)
        assertEquals("经历冲击后更谨慎", result.attributes["personality"])
        assertEquals("7", result.attributes["arcane_affinity"])
        assertEquals(3, result.relationshipToPlayer?.favorDelta)
    }

    @Test
    fun parseCharacterStatePatch_acceptsCamelCaseFields() {
        val result = parser.parseCharacterStatePatch(
            """{"characterUpdate":{"mentalState":{"currentThoughts":"重新核对号码"},"relationshipToPlayer":{"favorDelta":"4","trustDelta":2},"inventoryChanges":[]}}"""
        )

        assertEquals("重新核对号码", result.mentalState?.currentThoughts)
        assertEquals(4, result.relationshipToPlayer?.favorDelta)
        assertEquals(2, result.relationshipToPlayer?.trustDelta)
    }

    @Test
    fun parseStoryParseResult_acceptsLocalModelLooseTypes() {
        val raw = """
            {
              "world_setting": {
                "name": "灰烬大陆",
                "genre": "黑暗奇幻",
                "tech_level": "中高",
                "magic_level": "中高",
                "tone": "压抑",
                "forbidden": "完全遗忘过去，或被永夜雾吞噬的记忆",
                "description": "大陆被永夜雾笼罩。"
              },
              "player": {
                "name": "艾岚",
                "age": "未知（外表约20-30岁）",
                "gender": "男性",
                "race": "人类",
                "class": "巡夜人",
                "personality": "警惕",
                "background": "失忆者",
                "appearance": "巡夜人制服",
                "skills": ["侦查", "生存"]
              },
              "opening": {
                "current_location": "黑钟塔",
                "time_of_day": "永夜第七码",
                "weather": "浓雾",
                "mood": "钟声将至",
                "pending_events": ["回应伙伴银灯的警告"]
              },
              "npcs": [
                {"name": "银灯", "description": "会说话的伙伴", "keywords": ["伙伴"]}
              ],
              "worldbook": [
                {"type": "地点", "name": "黑钟塔", "description": "醒来的地方", "keywords": ["起点", "钟塔"]}
              ]
            }
        """.trimIndent()

        val result = parser.parseStoryParseResult(raw)

        assertEquals("灰烬大陆", result.worldSetting.name)
        assertEquals(listOf("完全遗忘过去", "或被永夜雾吞噬的记忆"), result.worldSetting.forbidden)
        assertEquals(20, result.player.age)
        assertEquals("黑钟塔", result.opening.currentLocation)
        assertEquals(listOf("回应伙伴银灯的警告"), result.opening.pendingEvents)
        assertEquals("银灯", result.npcs.first().name)
        assertEquals("黑钟塔", result.worldbook.first().name)
    }

    @Test
    fun parseStoryParseResult_acceptsCamelCaseFields() {
        val result = parser.parseStoryParseResult(
            """{"worldSetting":{"name":"海临市","techLevel":"当代","magicLevel":"无","lore":{"powerSystem":"现实规则"}},"player":{"name":"林克","lore":{"speechStyle":"简短","relationshipNotes":{"顾宁":"女友"}}},"opening":{"currentLocation":"云桥里","timeOfDay":"周一早晨","sceneDescription":"纸箱靠墙","activeCharacters":["顾宁"],"pendingEvents":["回复林越"],"globalFlags":{"搬家完成":true}},"npcs":[{"name":"顾宁"}]}"""
        )

        assertEquals("海临市", result.worldSetting.name)
        assertEquals("当代", result.worldSetting.techLevel)
        assertEquals("现实规则", result.worldSetting.lore.powerSystem)
        assertEquals("简短", result.player.lore.speechStyle)
        assertEquals("女友", result.player.lore.relationshipNotes["顾宁"])
        assertEquals("云桥里", result.opening.currentLocation)
        assertEquals("纸箱靠墙", result.opening.sceneDescription)
        assertEquals(listOf("顾宁"), result.opening.activeCharacters)
        assertEquals("true", result.opening.globalFlags["搬家完成"])
        assertEquals(listOf("回复林越"), result.opening.pendingEvents)
    }

    @Test
    fun parseStoryParseResult_readsCharacterDefinitionsCapabilitiesAndCustomValues() {
        val result = parser.parseStoryParseResult(
            """{
              "world_setting": {
                "name": "星海学宫",
                "description": "施法者在浮空学宫学习。",
                "character_fields": [
                  {"key":"stamina","label":"魔法值","group":"status","value_type":"number","min_value":0,"max_value":200,"default_value":"160","unit":"MP","display":"primary","update_mode":"dynamic"},
                  {"key":"known_spells","label":"已知法术","group":"ability","value_type":"list","default_value":"照明术","update_mode":"event"}
                ]
              },
              "player": {
                "name":"林克",
                "charm":61,
                "action_skill":44,
                "willpower":72,
                "resilience":55,
                "physical_state":{"stamina":180},
                "custom_attributes":{"known_spells":["照明术","护盾术"]}
              }
            }""".trimIndent()
        )

        assertEquals("魔法值", result.worldSetting.characterFields.first { it.key == "stamina" }.label)
        assertEquals(200, result.worldSetting.characterFields.first { it.key == "stamina" }.maxValue)
        assertEquals(180, result.player.physicalState.stamina)
        assertEquals(61, result.player.charm)
        assertEquals(44, result.player.actionSkill)
        assertEquals(72, result.player.willpower)
        assertEquals(55, result.player.resilience)
        assertEquals("照明术、护盾术", result.player.customAttributes["known_spells"])
    }
}
