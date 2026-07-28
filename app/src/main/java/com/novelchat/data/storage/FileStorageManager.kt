package com.novelchat.data.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.novelchat.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FileStorageManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val storedJsonCodec = StoredJsonCodec(gson)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private fun getRpgRootDir(): File = File(context.filesDir, "rpg")

    private fun getBaseDir(characterId: Long): File {
        require(characterId > 0L) { "无效故事标识" }
        return File(context.filesDir, "rpg/$characterId")
    }

    private fun getWorldSettingFile(characterId: Long): File =
        File(getBaseDir(characterId), "world_setting.json")

    private fun getStatesFile(characterId: Long): File =
        File(getBaseDir(characterId), "states.json")

    private fun getCreationMarkerFile(characterId: Long): File =
        File(getBaseDir(characterId), ".creating")

    private fun getCharactersDir(characterId: Long): File =
        File(getBaseDir(characterId), "characters")

    private fun getWorldbookDir(characterId: Long): File =
        File(getBaseDir(characterId), "worldbook")

    private fun getWorldbookSubDir(characterId: Long, subDir: String): File =
        File(getWorldbookDir(characterId), subDir)

    private fun getSummaryDir(characterId: Long): File =
        File(getBaseDir(characterId), "summary")

    private fun getNarrativeDir(characterId: Long): File =
        File(getBaseDir(characterId), "narrative")

    private fun getInventoryTransferDir(characterId: Long): File =
        File(getBaseDir(characterId), "transactions/inventory")

    private fun getStateMutationDir(characterId: Long): File =
        File(getBaseDir(characterId), "transactions/state")

    private fun safeFileName(raw: String): String {
        return CharacterCardFilePolicy.safeFileName(raw)
    }

    private fun jsonFiles(directory: File): List<File> {
        AtomicTextFile.recoverDirectory(directory)
        return directory.listFiles { file -> file.extension == "json" }?.toList().orEmpty()
    }

    // ========== 初始化 ==========

    suspend fun isInitialized(characterId: Long): Boolean = withContext(Dispatchers.IO) {
        !getCreationMarkerFile(characterId).exists() &&
            AtomicTextFile.readOrNull(getWorldSettingFile(characterId)) != null
    }

    suspend fun initialize(characterId: Long) = withContext(Dispatchers.IO) {
        getBaseDir(characterId).mkdirs()
        getCharactersDir(characterId).mkdirs()
        getWorldbookDir(characterId).mkdirs()
        listOf("locations", "items", "organizations", "events", "characters").forEach {
            getWorldbookSubDir(characterId, it).mkdirs()
        }
        getSummaryDir(characterId).mkdirs()
        getNarrativeDir(characterId).mkdirs()
        getInventoryTransferDir(characterId).mkdirs()
        getStateMutationDir(characterId).mkdirs()
        if (AtomicTextFile.readOrNull(getStatesFile(characterId)) == null) {
            saveStoryState(characterId, StoryState())
        }
    }

    suspend fun createConversationStorage(): Long = withContext(Dispatchers.IO) {
        val characterId = ConversationIdAllocator.reserve(
            rootDirectory = getRpgRootDir(),
            seed = System.currentTimeMillis()
        )
        try {
            initialize(characterId)
            AtomicTextFile.write(getCreationMarkerFile(characterId), "in_progress")
            characterId
        } catch (e: Exception) {
            getBaseDir(characterId).deleteRecursively()
            throw e
        }
    }

    suspend fun completeConversationStorage(characterId: Long): Unit = withContext(Dispatchers.IO) {
        val playerFile = File(getCharactersDir(characterId), "player.json")
        val hasCoreFiles = AtomicTextFile.readOrNull(getWorldSettingFile(characterId)) != null &&
            AtomicTextFile.readOrNull(getStatesFile(characterId)) != null &&
            AtomicTextFile.readOrNull(playerFile) != null
        if (!hasCoreFiles) throw IOException("故事初始化数据不完整")

        val marker = getCreationMarkerFile(characterId)
        if (marker.exists() && !marker.delete()) {
            throw IOException("无法完成故事初始化")
        }
    }

    suspend fun deleteConversation(characterId: Long): Unit = withContext(Dispatchers.IO) {
        val directory = getBaseDir(characterId)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("无法删除故事数据：${directory.absolutePath}")
        }
    }

    suspend fun recoverConversationEntries(): List<ConversationEntry> = withContext(Dispatchers.IO) {
        val root = getRpgRootDir()
        if (!root.exists()) return@withContext emptyList()
        val directories = root.listFiles()
            ?: throw IOException("无法读取故事存档目录：${root.absolutePath}")

        val recovered = mutableListOf<ConversationEntry>()
        for (directory in directories) {
            if (!directory.isDirectory) continue
            val characterId = directory.name.toLongOrNull()?.takeIf { it > 0 } ?: continue
            if (getCreationMarkerFile(characterId).exists()) continue
            AtomicTextFile.recoverDirectory(directory)
            AtomicTextFile.recoverDirectory(getCharactersDir(characterId))
            AtomicTextFile.recoverDirectory(getNarrativeDir(characterId))

            val playerFile = File(getCharactersDir(characterId), "player.json")
            val hasRecoverableData = getWorldSettingFile(characterId).isFile ||
                playerFile.isFile ||
                jsonFiles(getNarrativeDir(characterId)).isNotEmpty()
            if (!hasRecoverableData) continue

            var hasCriticalCorruption = false
            val world = try {
                loadWorldSetting(characterId)
            } catch (_: CorruptStoredDataException) {
                hasCriticalCorruption = true
                WorldSetting()
            }
            val player = try {
                loadPlayerState(characterId)
            } catch (_: CorruptStoredDataException) {
                hasCriticalCorruption = true
                null
            }
            val story = try {
                loadStoryState(characterId)
            } catch (_: CorruptStoredDataException) {
                hasCriticalCorruption = true
                StoryState()
            }
            val narratives = try {
                loadAllNarratives(characterId)
            } catch (_: CorruptStoredDataException) {
                hasCriticalCorruption = true
                emptyList()
            }
            val modifiedAt = directory.lastModified().takeIf { it > 0 }
                ?: System.currentTimeMillis()
            val fallbackTimestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedAt),
                ZoneId.systemDefault()
            ).format(dateFormatter)

            recovered += ConversationRecoveryPolicy.create(
                ConversationRecoverySnapshot(
                    id = characterId,
                    playerName = player?.name.orEmpty(),
                    worldName = world.name,
                    worldDescription = if (hasCriticalCorruption) {
                        ConversationRecoveryPolicy.CORRUPT_DATA_DESCRIPTION
                    } else {
                        world.description
                    },
                    currentRound = story.currentRound,
                    narratives = narratives,
                    fallbackTimestamp = fallbackTimestamp
                )
            )
        }
        recovered
    }

    // ========== 世界设定 ==========

    suspend fun loadWorldSetting(characterId: Long): WorldSetting = withContext(Dispatchers.IO) {
        val file = getWorldSettingFile(characterId)
        val json = AtomicTextFile.readOrNull(file) ?: return@withContext WorldSetting()
        storedJsonCodec.decode(json, file, WorldSetting::class.java, "世界设定")
    }

    suspend fun saveWorldSetting(characterId: Long, setting: WorldSetting) = withContext(Dispatchers.IO) {
        AtomicTextFile.write(getWorldSettingFile(characterId), gson.toJson(setting))
    }

    // ========== 故事状态 ==========

    suspend fun loadStoryState(characterId: Long): StoryState = withContext(Dispatchers.IO) {
        val file = getStatesFile(characterId)
        val json = AtomicTextFile.readOrNull(file) ?: return@withContext StoryState()
        storedJsonCodec.decode(json, file, StoryState::class.java, "故事状态")
    }

    suspend fun saveStoryState(characterId: Long, state: StoryState) = withContext(Dispatchers.IO) {
        AtomicTextFile.write(getStatesFile(characterId), gson.toJson(state))
    }

    // ========== 可恢复库存转移 ==========

    suspend fun loadInventoryTransferJournal(
        characterId: Long,
        operationId: String
    ): InventoryTransferJournal? = withContext(Dispatchers.IO) {
        val normalizedId = operationId.trim()
        if (normalizedId.isBlank()) return@withContext null
        val file = File(getInventoryTransferDir(characterId), "${safeFileName(normalizedId)}.json")
        val json = AtomicTextFile.readOrNull(file) ?: return@withContext null
        storedJsonCodec.decode(json, file, InventoryTransferJournal::class.java, "库存转移日志")
    }

    suspend fun saveInventoryTransferJournal(
        characterId: Long,
        journal: InventoryTransferJournal
    ) = withContext(Dispatchers.IO) {
        require(journal.operationId.isNotBlank()) { "库存转移日志标识不能为空" }
        require(journal.round > 0) { "库存转移日志轮次无效" }
        val file = File(
            getInventoryTransferDir(characterId),
            "${safeFileName(journal.operationId)}.json"
        )
        AtomicTextFile.write(file, gson.toJson(journal))
    }

    suspend fun loadInventoryTransferJournals(
        characterId: Long,
        round: Int
    ): List<InventoryTransferJournal> = withContext(Dispatchers.IO) {
        if (round <= 0) return@withContext emptyList()
        jsonFiles(getInventoryTransferDir(characterId))
            .map { file ->
                storedJsonCodec.decode(
                    AtomicTextFile.read(file),
                    file,
                    InventoryTransferJournal::class.java,
                    "库存转移日志"
                )
            }
            .filter { it.round == round }
            .sortedBy { it.operationId }
    }

    suspend fun clearInventoryTransferJournals(
        characterId: Long,
        round: Int
    ) = withContext(Dispatchers.IO) {
        if (round <= 0) return@withContext
        val directory = getInventoryTransferDir(characterId)
        jsonFiles(directory).forEach { file ->
            val journal = storedJsonCodec.decode(
                AtomicTextFile.read(file),
                file,
                InventoryTransferJournal::class.java,
                "库存转移日志"
            )
            if (journal.round == round && file.exists() && !file.delete()) {
                throw IOException("无法清理已完成的库存转移日志")
            }
        }
    }

    suspend fun loadStateMutationJournal(
        characterId: Long,
        operationId: String
    ): StateMutationJournal? = withContext(Dispatchers.IO) {
        val normalizedId = operationId.trim()
        if (normalizedId.isBlank()) return@withContext null
        val file = File(getStateMutationDir(characterId), "${safeFileName(normalizedId)}.json")
        val json = AtomicTextFile.readOrNull(file) ?: return@withContext null
        storedJsonCodec.decode(json, file, StateMutationJournal::class.java, "状态操作日志")
    }

    suspend fun saveStateMutationJournal(
        characterId: Long,
        journal: StateMutationJournal
    ) = withContext(Dispatchers.IO) {
        require(journal.operationId.isNotBlank()) { "状态操作日志标识不能为空" }
        require(journal.round > 0) { "状态操作日志轮次无效" }
        val file = File(
            getStateMutationDir(characterId),
            "${safeFileName(journal.operationId)}.json"
        )
        AtomicTextFile.write(file, gson.toJson(journal))
    }

    suspend fun loadStateMutationJournals(
        characterId: Long,
        round: Int
    ): List<StateMutationJournal> = withContext(Dispatchers.IO) {
        if (round <= 0) return@withContext emptyList()
        jsonFiles(getStateMutationDir(characterId))
            .map { file ->
                storedJsonCodec.decode(
                    AtomicTextFile.read(file),
                    file,
                    StateMutationJournal::class.java,
                    "状态操作日志"
                )
            }
            .filter { it.round == round }
            .sortedBy { it.operationId }
    }

    suspend fun clearStateMutationJournals(
        characterId: Long,
        round: Int
    ) = withContext(Dispatchers.IO) {
        if (round <= 0) return@withContext
        val directory = getStateMutationDir(characterId)
        jsonFiles(directory).forEach { file ->
            val journal = storedJsonCodec.decode(
                AtomicTextFile.read(file),
                file,
                StateMutationJournal::class.java,
                "状态操作日志"
            )
            if (journal.round == round && file.exists() && !file.delete()) {
                throw IOException("无法清理已完成的状态操作日志")
            }
        }
    }

    // ========== 主角状态 ==========

    suspend fun loadPlayerState(characterId: Long): PlayerState? = withContext(Dispatchers.IO) {
        val file = File(getCharactersDir(characterId), "player.json")
        val json = AtomicTextFile.readOrNull(file) ?: return@withContext null
        storedJsonCodec.decode(json, file, PlayerState::class.java, "主角存档")
    }

    suspend fun savePlayerState(characterId: Long, player: PlayerState) = withContext(Dispatchers.IO) {
        AtomicTextFile.write(File(getCharactersDir(characterId), "player.json"), gson.toJson(player))
    }

    // ========== NPC 角色卡 ==========

    suspend fun loadCharacterCard(characterId: Long, cardId: String): CharacterCard? = withContext(Dispatchers.IO) {
        val file = File(getCharactersDir(characterId), "${CharacterCardFilePolicy.fileStem(cardId)}.json")
        val json = AtomicTextFile.readOrNull(file) ?: return@withContext null
        storedJsonCodec.decode(json, file, CharacterCard::class.java, "角色卡")
    }

    suspend fun loadCharacterCardByName(characterId: Long, name: String): CharacterCard? = withContext(Dispatchers.IO) {
        val dir = getCharactersDir(characterId)
        if (!dir.exists()) return@withContext null
        jsonFiles(dir)
            .asSequence()
            .filter { it.name != "player.json" }
            .map { file ->
                storedJsonCodec.decode(
                    AtomicTextFile.read(file),
                    file,
                    CharacterCard::class.java,
                    "角色卡"
                )
            }
            .firstOrNull { it.name == name }
    }

    suspend fun loadAllCharacterCards(characterId: Long): List<CharacterCard> = withContext(Dispatchers.IO) {
        val dir = getCharactersDir(characterId)
        if (!dir.exists()) return@withContext emptyList()
        jsonFiles(dir).filter { it.name != "player.json" }
            .map { file ->
                storedJsonCodec.decode(
                    AtomicTextFile.read(file),
                    file,
                    CharacterCard::class.java,
                    "角色卡"
                )
            }
    }

    suspend fun loadActiveCharacterCards(characterId: Long): List<CharacterCard> = withContext(Dispatchers.IO) {
        CharacterIdentityPolicy.reconcileRoster(loadAllCharacterCards(characterId))
            .cards
            .filter { it.status == "active" }
    }

    suspend fun saveCharacterCard(characterId: Long, card: CharacterCard) = withContext(Dispatchers.IO) {
        val dir = getCharactersDir(characterId)
        val target = File(dir, "${CharacterCardFilePolicy.fileStem(card)}.json")
        AtomicTextFile.write(target, gson.toJson(card))
        jsonFiles(dir).filter { it.name != "player.json" }
            .forEach { file ->
                if (file == target) return@forEach
                val existing = try {
                    gson.fromJson(AtomicTextFile.read(file), CharacterCard::class.java)
                } catch (_: Exception) {
                    null
                }
                val sameStableId = card.id.isNotBlank() && existing?.id == card.id
                val sameName = card.name.isNotBlank() && existing?.name == card.name
                val sameNormalizedPlaceholder = existing != null &&
                    CharacterNamingPolicy.isGenericNpcName(existing.name) &&
                    CharacterNamingPolicy.normalizeNpcCard(existing).name == card.name
                if (sameStableId || sameName || sameNormalizedPlaceholder) file.delete()
            }
    }

    suspend fun deleteCharacterCard(characterId: Long, cardId: String) = withContext(Dispatchers.IO) {
        File(getCharactersDir(characterId), "${CharacterCardFilePolicy.fileStem(cardId)}.json").let { if (it.exists()) it.delete() }
    }

    // ========== 世界书 ==========

    private val worldbookSubDirs = listOf("locations", "items", "organizations", "events", "characters")

    suspend fun loadWorldbookEntry(characterId: Long, entryId: String): WorldbookEntry? = withContext(Dispatchers.IO) {
        val fileName = "${safeFileName(entryId)}.json"
        worldbookSubDirs
            .mapNotNull { subDir ->
                val file = File(getWorldbookSubDir(characterId, subDir), fileName)
                val json = AtomicTextFile.readOrNull(file) ?: return@mapNotNull null
                WorldbookContentPolicy.normalize(
                    storedJsonCodec.decode(json, file, WorldbookEntry::class.java, "世界书条目")
                )
            }
            .maxByOrNull { entry -> entry.lastModified.ifBlank { entry.createdAt } }
    }

    suspend fun loadWorldbookEntryByName(characterId: Long, name: String): WorldbookEntry? = withContext(Dispatchers.IO) {
        for (subDir in worldbookSubDirs) {
            val dir = getWorldbookSubDir(characterId, subDir)
            if (!dir.exists()) continue
            val entry = jsonFiles(dir)
                .asSequence()
                .map { file ->
                    storedJsonCodec.decode(
                        AtomicTextFile.read(file),
                        file,
                        WorldbookEntry::class.java,
                        "世界书条目"
                    )
                }
                .firstOrNull { it.name == name }
                ?.let(WorldbookContentPolicy::normalize)
            if (entry != null) return@withContext entry
        }
        null
    }

    suspend fun loadAllWorldbookEntries(characterId: Long): List<WorldbookEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<WorldbookEntry>()
        for (subDir in worldbookSubDirs) {
            val dir = getWorldbookSubDir(characterId, subDir)
            if (!dir.exists()) continue
            jsonFiles(dir).forEach { file ->
                result += WorldbookContentPolicy.normalize(
                    storedJsonCodec.decode(
                        AtomicTextFile.read(file),
                        file,
                        WorldbookEntry::class.java,
                        "世界书条目"
                    )
                )
            }
        }
        result
            .groupBy { entry ->
                entry.id.takeIf { it.isNotBlank() }
                    ?: "${entry.type}:${entry.name}"
            }
            .values
            .mapNotNull { duplicates ->
                duplicates.maxByOrNull { entry ->
                    entry.lastModified.ifBlank { entry.createdAt }
                }
            }
    }

    suspend fun loadActiveWorldbookEntries(characterId: Long): List<WorldbookEntry> = withContext(Dispatchers.IO) {
        loadAllWorldbookEntries(characterId).filter { it.isActive }
    }

    suspend fun saveWorldbookEntry(characterId: Long, entry: WorldbookEntry) = withContext(Dispatchers.IO) {
        require(entry.id.isNotBlank()) { "世界书条目标识不能为空" }
        val normalizedEntry = WorldbookContentPolicy.normalize(entry)
        require(normalizedEntry.name.isNotBlank()) { "世界书条目名称不能为空" }
        val subDir = when (normalizedEntry.type) {
            "location" -> "locations"; "item" -> "items"
            "organization" -> "organizations"; "event" -> "events"
            "character" -> "characters"; else -> "events"
        }
        val fileName = "${safeFileName(normalizedEntry.id)}.json"
        val target = File(getWorldbookSubDir(characterId, subDir), fileName)
        AtomicTextFile.write(target, gson.toJson(normalizedEntry))
        worldbookSubDirs.forEach { candidateSubDir ->
            val candidate = File(getWorldbookSubDir(characterId, candidateSubDir), fileName)
            if (candidate != target && candidate.exists()) candidate.delete()
        }
    }

    suspend fun deleteWorldbookEntry(characterId: Long, entryId: String) = withContext(Dispatchers.IO) {
        val fileName = "${safeFileName(entryId)}.json"
        for (subDir in worldbookSubDirs) {
            val file = File(getWorldbookSubDir(characterId, subDir), fileName)
            if (file.exists() && !file.delete()) {
                throw IOException("无法删除世界书条目")
            }
        }
    }

    // ========== 缩略剧情 ==========

    suspend fun loadAllSummaries(characterId: Long): List<SummaryEntry> = withContext(Dispatchers.IO) {
        val dir = getSummaryDir(characterId)
        if (!dir.exists()) return@withContext emptyList()
        jsonFiles(dir)
            .mapNotNull { file -> try { gson.fromJson(AtomicTextFile.read(file), SummaryEntry::class.java) } catch (e: Exception) { null } }
            .sortedBy { it.turn }
    }

    suspend fun saveSummary(characterId: Long, summary: SummaryEntry) = withContext(Dispatchers.IO) {
        getSummaryDir(characterId).mkdirs()
        AtomicTextFile.write(
            File(getSummaryDir(characterId), String.format(Locale.ROOT, "turn_%03d.json", summary.turn)),
            gson.toJson(summary)
        )
    }

    // ========== 正文剧情 ==========

    suspend fun loadAllNarratives(characterId: Long): List<NarrativeEntry> = withContext(Dispatchers.IO) {
        val dir = getNarrativeDir(characterId)
        if (!dir.exists()) return@withContext emptyList()
        jsonFiles(dir)
            .mapNotNull { file ->
                try {
                    val entry = storedJsonCodec.decode(
                        AtomicTextFile.read(file),
                        file,
                        NarrativeEntry::class.java,
                        "剧情正文"
                    )
                    val cleaned = CharacterNamingPolicy.normalizeNarrativeText(
                        NarrativeResponseRecovery.cleanNarrativeText(entry.narrative)
                    )
                    if (cleaned != entry.narrative) {
                        entry.copy(narrative = cleaned).also { AtomicTextFile.write(file, gson.toJson(it)) }
                    } else {
                        entry
                    }
                } catch (e: CorruptStoredDataException) {
                    throw e
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException("无法读取剧情正文（${file.name}）", e)
                }
            }
            .sortedBy { it.turn }
    }

    suspend fun saveNarrative(characterId: Long, narrative: NarrativeEntry) = withContext(Dispatchers.IO) {
        getNarrativeDir(characterId).mkdirs()
        AtomicTextFile.write(
            File(getNarrativeDir(characterId), String.format(Locale.ROOT, "turn_%03d.json", narrative.turn)),
            gson.toJson(narrative)
        )
    }

    // ========== 辅助方法 ==========

    fun getCurrentTimeString(): String = LocalDateTime.now().format(dateFormatter)
}
