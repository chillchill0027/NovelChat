package com.novelchat.domain.engine

import com.novelchat.data.model.InventoryChange
import com.novelchat.data.model.ItemTransfer
import com.novelchat.data.model.EventRecord
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterUpdateRequest
import com.novelchat.data.model.NewCharacter
import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.PlayerUpdate
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.Quest
import com.novelchat.data.model.RetiredCharacter
import com.novelchat.data.model.TaskAssignment
import com.novelchat.data.model.WorldbookUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAssignmentExecutionPolicyTest {

    @Test
    fun sanitize_normalizesPlayerAliasesAndRemovesDuplicateInventoryChange() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                playerUpdate = PlayerUpdate(
                    changes = PlayerChanges(
                        inventory = listOf(InventoryChange("add", "中奖彩票", 1))
                    )
                ),
                itemTransfers = listOf(
                    ItemTransfer(item = "中奖彩票", quantity = 1, from = "老周", to = "主角")
                )
            ),
            playerName = "林克"
        )

        assertTrue(sanitized.playerUpdate?.changes?.inventory.orEmpty().isEmpty())
        assertEquals("林克", sanitized.itemTransfers.single().to)
    }

    @Test
    fun sanitize_dropsTransfersFromPlayerToPlayer() {
        val raw = TaskAssignment(
            itemTransfers = listOf(
                ItemTransfer(item = "手机", from = "我", to = "林克")
            )
        )
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = raw,
            playerName = "林克"
        )

        assertTrue(TaskAssignmentQualityPolicy.isMeaningful(raw))
        assertTrue(sanitized.itemTransfers.isEmpty())
        assertFalse(TaskAssignmentQualityPolicy.isMeaningful(sanitized))
    }

    @Test
    fun sanitize_keepsOnlyOneTransferForSameItemAndParticipants() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                itemTransfers = listOf(
                    ItemTransfer(item = "中奖彩票", quantity = 1, from = "林克", to = "周明"),
                    ItemTransfer(item = "中奖彩票", quantity = 2, from = "林克", to = "周明")
                )
            ),
            playerName = "林克"
        )

        assertEquals(1, sanitized.itemTransfers.size)
        assertEquals(1, sanitized.itemTransfers.single().quantity)
    }

    @Test
    fun sanitize_keepsOnlyPersistableEventsAndNormalizesTheirFields() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                events = listOf(
                    EventRecord(
                        type = " 兑奖完成 ",
                        target = " 彩票店 ",
                        causedBy = " 林克 ",
                        description = " 店员确认中奖彩票 "
                    ),
                    EventRecord(type = "剧情事件", target = "", description = "没有目标")
                )
            ),
            playerName = "林克"
        )

        assertEquals(1, sanitized.events.size)
        assertEquals("兑奖完成", sanitized.events.single().type)
        assertEquals("彩票店", sanitized.events.single().target)
        assertEquals("店员确认中奖彩票", sanitized.events.single().description)
    }

    @Test
    fun sanitize_deduplicatesSameRoundEntityOperationsBeforeExecution() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                newCharacters = listOf(
                    NewCharacter(name = " 王常 ", background = "第一次描述"),
                    NewCharacter(name = "王常", background = "重复描述")
                ),
                retiredCharacters = listOf(
                    RetiredCharacter(name = " 老周 ", reason = "先离开"),
                    RetiredCharacter(name = "老周", reason = "重复退场")
                ),
                characterUpdates = listOf(
                    CharacterUpdateRequest(name = " 王常 ", reason = "目睹兑奖"),
                    CharacterUpdateRequest(name = "王常", reason = "补充震惊")
                ),
                worldbookUpdates = listOf(
                    WorldbookUpdate(
                        action = " UPDATE ",
                        id = "lottery_shop",
                        name = "彩票店",
                        description = "完成兑奖"
                    ),
                    WorldbookUpdate(
                        action = "update",
                        id = "lottery_shop",
                        name = "彩票店",
                        description = "重复改写"
                    )
                ),
                events = listOf(
                    EventRecord(
                        type = "兑奖完成",
                        target = "彩票店",
                        description = "店员确认中奖彩票"
                    ),
                    EventRecord(
                        type = "兑奖完成",
                        target = "彩票店",
                        description = "店员震惊于一亿元奖金"
                    )
                )
            ),
            playerName = "林克"
        )

        assertEquals(1, sanitized.newCharacters.size)
        assertEquals("第一次描述", sanitized.newCharacters.single().background)
        assertEquals(1, sanitized.retiredCharacters.size)
        assertEquals("先离开", sanitized.retiredCharacters.single().reason)
        assertEquals(1, sanitized.characterUpdates.size)
        assertEquals("目睹兑奖", sanitized.characterUpdates.single().reason)
        assertEquals(1, sanitized.worldbookUpdates.size)
        assertEquals("完成兑奖", sanitized.worldbookUpdates.single().description)
        assertEquals(1, sanitized.events.size)
        assertEquals("店员确认中奖彩票", sanitized.events.single().description)
    }

    @Test
    fun sanitize_boundsBulkOperationsAndRejectsModelSuppliedInitialState() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                newCharacters = List(40) { index ->
                    NewCharacter(
                        name = "角色$index",
                        background = "背".repeat(7_000),
                        initialStatus = CharacterCard(name = "绕过校验")
                    )
                },
                events = List(60) { index ->
                    EventRecord(
                        type = "事件",
                        target = "地点$index",
                        description = "描".repeat(2_000) + index
                    )
                }
            ),
            playerName = "林克"
        )

        assertEquals(24, sanitized.newCharacters.size)
        assertTrue(sanitized.newCharacters.all { it.initialStatus == null })
        assertEquals(6_000, sanitized.newCharacters.first().background.length)
        assertEquals(32, sanitized.events.size)
        assertEquals(1_000, sanitized.events.first().description.length)
    }

    @Test
    fun sanitize_boundsPlayerPatchCollectionsAndValues() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                playerUpdate = PlayerUpdate(
                    changes = PlayerChanges(
                        inventory = List(70) { index ->
                            InventoryChange(action = "add", item = "物品$index", quantity = 99_999)
                        },
                        questLog = List(70) { index ->
                            Quest(name = "任务$index", description = "描".repeat(2_000))
                        },
                        physicalState = PhysicalStatePatch(
                            injuries = List(30) { "伤".repeat(1_500) }
                        ),
                        risk = 999
                    )
                )
            ),
            playerName = "林克"
        )

        val changes = sanitized.playerUpdate!!.changes
        assertEquals(50, changes.inventory.size)
        assertEquals(9_999, changes.inventory.first().quantity)
        assertEquals(50, changes.questLog?.size)
        assertEquals(1_000, changes.questLog?.first()?.description?.length)
        assertEquals(20, changes.physicalState?.injuries?.size)
        assertEquals(1_000, changes.physicalState?.injuries?.first()?.length)
        assertEquals(999, changes.risk)
    }

    @Test
    fun sanitize_keepsValidCharacterAttributePatches() {
        val sanitized = TaskAssignmentExecutionPolicy.sanitize(
            assignment = TaskAssignment(
                playerUpdate = PlayerUpdate(
                    changes = PlayerChanges(
                        attributes = mapOf(" Personality " to "更愿意直面冲突", "bad-key!" to "丢弃")
                    )
                ),
                newCharacters = listOf(
                    NewCharacter(name = "王常", attributes = mapOf("life_stability" to "42"))
                )
            ),
            playerName = "林克"
        )

        assertEquals(mapOf("personality" to "更愿意直面冲突"), sanitized.playerUpdate!!.changes.attributes)
        assertEquals(mapOf("life_stability" to "42"), sanitized.newCharacters.single().attributes)
    }
}
