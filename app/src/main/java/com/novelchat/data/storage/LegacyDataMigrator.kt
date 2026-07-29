package com.novelchat.data.storage

import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.ConversationEntry

class LegacyDataMigrator(
    private val fileStorage: FileStorageManager,
    private val indexStore: ConversationIndexStore
) {
    suspend fun migrateConversationIndex() {
        indexStore.update { entries ->
            val recoveredEntries = fileStorage.recoverConversationEntries()
            val recoveredById = recoveredEntries.associateBy { it.id }
            val existing = entries.mapNotNull { entry ->
                recoveredById[entry.id]?.let { recovered ->
                    ConversationRecoveryPolicy.merge(entry, recovered)
                }
            }
            val existingIds = existing.mapTo(mutableSetOf()) { it.id }
            (existing + recoveredEntries.filterNot { it.id in existingIds })
                .map { migrateEntry(it) }
        }
    }

    private suspend fun migrateEntry(entry: ConversationEntry): ConversationEntry {
        val identityName = legacyNameMap[entry.presetKey]
            ?: legacyNameMap[entry.characterName]
            ?: entry.characterName
        val migratedName = CharacterNamingPolicy.presetPlayerNames[entry.presetKey]
            ?: identityToPlayerName[entry.characterName]
            ?: identityName
        val migratedPresetKey = legacyPresetKeyMap[entry.presetKey] ?: entry.presetKey

        if (migratedName != entry.characterName || migratedPresetKey != entry.presetKey) {
            migratePlayerName(entry.id, migratedName)
        }

        return entry.copy(
            characterName = migratedName,
            presetKey = migratedPresetKey,
            characterDesc = if (migratedName != entry.characterName || containsLegacyText(entry.characterDesc)) {
                when {
                    containsLegacyText(entry.characterDesc) -> {
                        val identity = identityName.takeIf { it != migratedName && it != "我" }.orEmpty()
                        identity.ifBlank { "互动故事" }
                    }
                    identityName != migratedName && identityName != "我" -> {
                        listOf(identityName, entry.characterDesc).filter { it.isNotBlank() }.joinToString(" · ")
                    }
                    else -> entry.characterDesc
                }
            } else {
                entry.characterDesc
            },
            lastMessage = if (containsLegacyText(entry.lastMessage)) {
                ""
            } else {
                entry.lastMessage
            }
        )
    }

    private suspend fun migratePlayerName(characterId: Long, newName: String) {
        val player = fileStorage.loadPlayerState(characterId) ?: return
        if (legacyNameMap.containsKey(player.name) || identityToPlayerName.containsKey(player.name)) {
            val migratedIdentity = legacyNameMap[player.name] ?: player.name
            fileStorage.savePlayerState(characterId, player.copy(
                name = newName,
                class_ = player.class_.ifBlank { migratedIdentity.takeIf { it != "我" }.orEmpty() }
            ))
        }
    }

    companion object {
        val legacyPresetKeyMap = mapOf(
            "loser" to "renter",
            "worker" to "clerk",
            "social" to "freelancer",
            "alpha" to "resident",
            "top_player" to "returnee",
            "wanderer" to "renter",
            "scribe" to "clerk",
            "envoy" to "freelancer",
            "captain" to "resident",
            "heir" to "returnee"
        )

        val legacyNameMap = mapOf(
            "loser" to "城市租客",
            "worker" to "职场新人",
            "social" to "自由职业者",
            "alpha" to "社区医生",
            "top_player" to "返乡创业者",
            "wanderer" to "城市租客",
            "scribe" to "职场新人",
            "envoy" to "自由职业者",
            "captain" to "社区医生",
            "heir" to "返乡创业者",
            "穷屌丝" to "城市租客",
            "普通上班族" to "职场新人",
            "社交达人" to "自由职业者",
            "精英Alpha" to "社区医生",
            "顶级玩家" to "返乡创业者",
            "流浪旅人" to "城市租客",
            "见习书记官" to "职场新人",
            "宫廷信使" to "自由职业者",
            "边境队长" to "社区医生",
            "失落继承人" to "返乡创业者"
        )

        val identityToPlayerName = mapOf(
            "城市租客" to CharacterNamingPolicy.playerNameForPreset("renter"),
            "职场新人" to CharacterNamingPolicy.playerNameForPreset("clerk"),
            "自由职业者" to CharacterNamingPolicy.playerNameForPreset("freelancer"),
            "社区医生" to CharacterNamingPolicy.playerNameForPreset("resident"),
            "返乡创业者" to CharacterNamingPolicy.playerNameForPreset("returnee"),
            "我" to CharacterNamingPolicy.DEFAULT_PLAYER_NAME
        )

        private val legacyPhrases = listOf(
            "自己用手指",
            "填补你的空虚",
            "指尖带来的",
            "持续、有节奏的刺激",
            "旧版测试故事",
            "旧版测试记录",
            "已迁移为通用叙事开局"
        )

        val legacyTerms = listOf(
            "约炮", "绿帽", "性欲", "性癖", "调教", "猎物", "穷屌丝",
            "艳遇", "下水道", "胸部", "内衣", "兴奋", "女大学生", "顶级玩家", "精英Alpha",
            "普通上班族", "社交达人", "流浪旅人", "见习书记官", "宫廷信使", "边境队长", "失落继承人"
        )

        fun containsLegacyText(text: String): Boolean {
            if (text.isBlank()) return false
            val hasLegacyTerm = legacyTerms.any { text.contains(it, ignoreCase = true) }
            val hasLegacyPhrase = legacyPhrases.any { text.contains(it, ignoreCase = true) }
            return hasLegacyTerm || hasLegacyPhrase
        }
    }
}
