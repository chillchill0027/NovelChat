package com.novelchat.domain.v2.state

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.MemoryNote
import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.engine.StateSyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDeltaApplierTest {

    private fun baseSnapshot(): StorySnapshot {
        val fields = listOf(
            CharacterFieldDefinition(
                key = "stamina",
                label = "体力",
                valueType = CharacterFieldDefinition.TYPE_NUMBER,
                minValue = 0,
                maxValue = 100,
                updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC,
                appliesTo = CharacterFieldDefinition.TARGET_ALL
            ),
            CharacterFieldDefinition(
                key = "manual_tag",
                label = "手动标签",
                valueType = CharacterFieldDefinition.TYPE_TEXT,
                updateMode = CharacterFieldDefinition.UPDATE_MANUAL,
                appliesTo = CharacterFieldDefinition.TARGET_PLAYER
            )
        )
        return StorySnapshot(
            storyId = 1L,
            world = WorldSetting(name = "海临市", characterFields = fields),
            story = StoryState(
                currentRound = 1,
                currentLocation = "云桥里",
                activeCharacters = listOf("房东陈姐"),
                pendingEvents = listOf("本周交房租", "回复林越")
            ),
            player = PlayerState(
                name = "林克",
                inventory = listOf(InventoryItem(name = "备用钥匙", quantity = 1)),
                customAttributes = mapOf("manual_tag" to "保持不变")
            ),
            characters = listOf(
                CharacterCard(
                    id = "chen_jie",
                    name = "房东陈姐",
                    status = "active",
                    favor = 0,
                    memories = (1..30).map { MemoryNote(turn = it, text = "旧记忆$it", weight = if (it == 1) 2 else 1) }
                )
            ),
            worldbook = emptyList(),
            narratives = listOf(
                NarrativeEntry(
                    turn = 2,
                    userInput = "去便利店买水然后把备用钥匙给陈姐",
                    narrative = "林克在楼下遇见房东陈姐，把备用钥匙还给她，又去便利店找店员许棠买了矿泉水。"
                )
            ),
            summaries = emptyList(),
            chapters = emptyList(),
            narration = NarrationSettings()
        )
    }

    @Test
    fun apply_toleratesPartialFailuresClampsValuesAndHandlesMemoriesAndTransfers() {
        val base = baseSnapshot()
        val delta = StateDelta(
            summary = "林克归还钥匙并在便利店买水。",
            scene = SceneDelta(location = "望潮路便利店", present = listOf("许棠", "林克", "幽灵路人")),
            goals = GoalsDelta(
                complete = listOf("交房租"),
                update = listOf(GoalUpdate("回复林越", "下午三点替林越参会")),
                add = listOf("整理面试材料")
            ),
            player = PlayerDelta(
                fields = mapOf("stamina" to "150", "manual_tag" to "企图篡改"),
                inventory = listOf(
                    InventoryOp("add", "矿泉水", 2),
                    InventoryOp("remove", "不存在的伞", 1)
                )
            ),
            npcs = listOf(
                NpcDelta(
                    name = "房东陈姐",
                    emotion = "欣慰",
                    favorDelta = 99,
                    memory = "林克主动归还备用钥匙并答应周五交租"
                ),
                NpcDelta(
                    name = "许棠",
                    emotion = "热情",
                    favorDelta = 4
                ),
                NpcDelta(
                    name = "不存在未出场角色",
                    emotion = "生气"
                )
            ),
            newNpcs = listOf(
                NewNpcDelta(name = "店员A", identity = "店员"),
                NewNpcDelta(name = "许棠", identity = "便利店店员", speechStyle = "语速快")
            ),
            departed = listOf(DepartedNpc(name = "房东陈姐", permanent = true)),
            transfers = listOf(
                ItemTransferDelta(item = "备用钥匙", qty = 1, from = "林克", to = "房东陈姐"),
                ItemTransferDelta(item = "金条", qty = 1, from = "林克", to = "许棠")
            ),
            suggestions = listOf("喝口水", "去地铁站", "给林越回消息")
        )

        val result = StateDeltaApplier.apply(base, delta, listOf(2), "2026-11-26T10:00:00")

        // 主角字段范围与 manual 保护
        assertEquals(100, result.snapshot.player.physicalState.stamina)
        assertEquals("保持不变", result.snapshot.player.customAttributes["manual_tag"])

        // 背包与物品转移（备用钥匙转给陈姐，矿泉水 +2，缺物品移除/转移不崩溃）
        assertTrue(result.snapshot.player.inventory.none { it.name == "备用钥匙" })
        assertEquals(2, result.snapshot.player.inventory.single { it.name == "矿泉水" }.quantity)

        val chen = result.snapshot.characters.single { it.name == "房东陈姐" }
        assertEquals(15, chen.favor) // delta 被限制在 ±15
        assertEquals("pool", chen.status) // permanent departed
        assertEquals(1, chen.inventory.single { it.name == "备用钥匙" }.quantity)
        // 记忆上限 30，淘汰最旧的 weight=1（turn=2），保留 turn=1 的 weight=2
        assertEquals(30, chen.memories.size)
        assertTrue(chen.memories.any { it.turn == 1 && it.weight == 2 })
        assertFalse(chen.memories.any { it.text == "旧记忆2" })
        assertTrue(chen.memories.any { it.text.contains("归还备用钥匙") && it.weight == 2 })

        // 占位名店员A被跳过；许棠正常创建并包含 speechStyle
        assertFalse(result.snapshot.characters.any { it.name == "店员A" })
        val xuTang = result.snapshot.characters.single { it.name == "许棠" }
        assertEquals("语速快", xuTang.lore.speechStyle)
        assertEquals(4, xuTang.favor)

        // 场景与待办
        assertEquals("望潮路便利店", result.snapshot.story.currentLocation)
        assertEquals(listOf("许棠"), result.snapshot.story.activeCharacters)
        assertEquals(listOf("下午三点替林越参会", "整理面试材料"), result.snapshot.story.pendingEvents)
        assertEquals("下午三点替林越参会", result.snapshot.player.storyProgress.currentGoal)

        // issues 记录了跳过项但整体成功产出写入
        assertTrue(result.issues.isNotEmpty())
        assertTrue(result.writes.isNotEmpty())
        assertEquals("林克归还钥匙并在便利店买水。", result.summary?.summary)
    }

    @Test
    fun apply_clearsLegacyPendingFlagWhenRequested() {
        val base = baseSnapshot().copy(
            story = StateSyncPolicy.markPending(StoryState(currentRound = 2), 2)
        )
        val result = StateDeltaApplier.apply(
            base = base,
            delta = StateDelta(summary = "补同步完成"),
            rounds = listOf(2),
            now = "2026-11-26T10:00:00",
            clearLegacyPendingFlag = true
        )
        assertEquals(null, StateSyncPolicy.pendingFromRound(result.snapshot.story))
        assertTrue(result.writes.any { it.type == "story" })
    }

    @Test
    fun apply_mergesWorldbookDescriptionInsteadOfOverwriting() {
        val existingEntry = com.novelchat.data.model.WorldbookEntry(
            id = "wb_teahouse",
            type = "location",
            name = "听雨茶馆",
            description = "位于渡口南侧的老茶馆，二楼临窗座位可以俯瞰码头动态。",
            keywords = listOf("听雨茶馆", "茶馆")
        )
        val base = baseSnapshot().copy(worldbook = listOf(existingEntry))
        val delta = StateDelta(
            world = listOf(
                WorldDelta(
                    type = "location",
                    name = "听雨茶馆",
                    description = "下午四点半后二楼通常没有其他客人，适合密谈。",
                    keywords = listOf("二楼")
                )
            )
        )

        val result = StateDeltaApplier.apply(base, delta, listOf(2), "2026-11-26T10:00:00")
        val updated = result.snapshot.worldbook.single { it.name == "听雨茶馆" }

        assertTrue(updated.description.contains("俯瞰码头动态"))
        assertTrue(updated.description.contains("下午四点半后二楼通常没有其他客人"))
        assertTrue(updated.keywords.containsAll(listOf("听雨茶馆", "茶馆", "二楼")))
    }
}
