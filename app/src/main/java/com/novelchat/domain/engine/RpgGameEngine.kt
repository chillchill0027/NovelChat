package com.novelchat.domain.engine

import android.util.Log
import com.google.gson.Gson
import com.novelchat.data.remote.CompletionLengthException
import com.novelchat.data.model.*
import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.AiService
import com.novelchat.data.remote.StructuredOutputKind
import com.novelchat.data.remote.model.ChatMessage
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.domain.parser.JsonResponseParser
import com.novelchat.domain.parser.TaskAssignmentParseException
import com.novelchat.domain.parser.TaskAssignmentParseFailure
import com.novelchat.domain.worldbook.WorldbookMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * RPG 游戏引擎
 * 编排三次 AI 调用的完整流程（对齐设计文档 v1.0 第1.1节）
 */
class RpgGameEngine(
    private val aiService: AiService,
    private val fileStorage: FileStorageManager
) {
    private val promptBuilder = RpgPromptBuilder()
    private val jsonParser = JsonResponseParser()
    private val worldbookMatcher = WorldbookMatcher()
    private val gson = Gson()

    companion object {
        private const val TAG = "RpgGameEngine"
    }

    /**
     * 执行一个完整的游戏回合
     */
    suspend fun executeRound(
        characterId: Long,
        userMessage: String
    ): Flow<GameEvent> {
        check(fileStorage.isInitialized(characterId)) {
            "故事存档不存在或尚未完成初始化"
        }
        fileStorage.saveNextRoundCheckpointIfAbsent(characterId)
        return execute(characterId, userMessage, stateSyncOnly = false)
    }

    suspend fun retryPendingStateSync(characterId: Long): Flow<GameEvent> =
        execute(characterId, userMessage = "", stateSyncOnly = true)

    private fun execute(
        characterId: Long,
        userMessage: String,
        stateSyncOnly: Boolean
    ): Flow<GameEvent> = flow {
        check(fileStorage.isInitialized(characterId)) {
            "故事存档不存在或尚未完成初始化"
        }
        val routeLocality = aiService.roundRouteLocality()

        // ========== 步骤1-4：读取所有状态 ==========
        emit(GameEvent.Loading("正在读取游戏状态..."))

        val worldSetting = fileStorage.loadWorldSetting(characterId)
        var storyState = fileStorage.loadStoryState(characterId)
        val storedPlayerState = fileStorage.loadPlayerState(characterId)
        var playerState = storedPlayerState
            ?: PlayerState(name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME)
        if (storedPlayerState == null) {
            fileStorage.savePlayerState(characterId, playerState)
        }
        val allNarratives = fileStorage.loadAllNarratives(characterId)
        StateSyncPolicy.reconcilePersistedNarratives(storyState, allNarratives).let { recovered ->
            if (recovered != storyState) {
                storyState = recovered
                fileStorage.saveStoryState(characterId, storyState)
            }
        }
        val rawStoredCharacters = fileStorage.loadAllCharacterCards(characterId)
        val identityResult = CharacterIdentityPolicy.reconcileRoster(rawStoredCharacters)
        val storedCharacters = identityResult.cards
        rawStoredCharacters.zip(storedCharacters).forEach { (stored, reconciled) ->
            if (stored != reconciled) fileStorage.saveCharacterCard(characterId, reconciled)
        }
        if (identityResult.aliases.isNotEmpty()) {
            val reconciledActiveCharacters = storyState.activeCharacters
                .map { name -> identityResult.aliases[name] ?: name }
                .distinct()
            if (reconciledActiveCharacters != storyState.activeCharacters) {
                storyState = storyState.copy(activeCharacters = reconciledActiveCharacters)
                fileStorage.saveStoryState(characterId, storyState)
            }
        }
        val allCharacters = storedCharacters.map { card ->
            CharacterProfilePolicy.reconcile(card, playerState.name)
        }
        storedCharacters.zip(allCharacters).forEach { (stored, reconciled) ->
            if (stored != reconciled) fileStorage.saveCharacterCard(characterId, reconciled)
        }
        val allWorldbookEntries = fileStorage.loadActiveWorldbookEntries(characterId)
        val allSummaries = fileStorage.loadAllSummaries(characterId)
        val recentNarratives = allNarratives.takeLast(15)
        val pendingRecoveryNarratives = StateSyncPolicy.recoveryNarratives(
            storyState,
            recentNarratives
        )
        val syncOnlyEntry = if (stateSyncOnly) {
            if (StateSyncPolicy.pendingFromRound(storyState) == null) {
                emit(GameEvent.RoundComplete(storyState.currentRound))
                return@flow
            }
            allNarratives.lastOrNull() ?: run {
                emit(GameEvent.Error("没有找到可补同步的已保存剧情。"))
                return@flow
            }
        } else {
            null
        }
        val effectiveUserMessage = syncOnlyEntry?.userInput.orEmpty().ifBlank { userMessage }

        val matchedWorldbook = worldbookMatcher.match(
            userMessage = effectiveUserMessage,
            storyState = storyState,
            allEntries = allWorldbookEntries,
            recentNarratives = recentNarratives,
            maxEntries = 8
        )
        val currentWorldbook = matchedWorldbook.find {
            it.type == "location" && it.name == storyState.currentLocation
        } ?: allWorldbookEntries.find {
            it.type == "location" && it.name == storyState.currentLocation
        }
        val relatedWorldbook = matchedWorldbook
            .filter { entry -> currentWorldbook == null || entry.id != currentWorldbook.id }
            .take(7)
        val narrativeCharacterContextText = buildString {
            storyState.pendingEvents.take(8).forEach(::appendLine)
            pendingRecoveryNarratives.takeLast(3).forEach { entry ->
                appendLine(entry.userInput.take(240))
                appendLine(entry.narrative.take(600))
            }
        }
        val activeCharacters = CharacterContextPolicy.selectForNarrative(
            allCharacters = allCharacters,
            sceneCharacterNames = storyState.activeCharacters +
                matchedWorldbook.flatMap { it.relatedCharacters },
            userMessage = effectiveUserMessage,
            contextText = narrativeCharacterContextText
        )
        val identityAnchors = NarrativeIdentityAnchorPolicy.resolve(
            userMessage = effectiveUserMessage,
            knownCharacters = allCharacters,
            playerName = playerState.name
        )
        // ========== 步骤5-6：剧情调用 ==========
        emit(GameEvent.Loading("正在生成剧情..."))
        val narrativeGoal = playerState?.storyProgress?.currentGoal.orEmpty()
            .ifBlank { storyState.pendingEvents.firstOrNull().orEmpty() }

        val narrativePrompt = promptBuilder.buildNarrativePrompt(
            worldSetting = worldSetting,
            playerState = playerState,
            activeCharacters = activeCharacters,
            worldbookEntries = matchedWorldbook,
            currentWorldbook = currentWorldbook,
            relatedWorldbook = relatedWorldbook,
            storyState = storyState,
            allSummaries = allSummaries,
            recentNarratives = recentNarratives,
            userMessage = effectiveUserMessage,
            identityAnchors = identityAnchors
        )

        val narrativeMessages = listOf(ChatMessage(role = "user", content = narrativePrompt))

        // 流式接收剧情
        val narrativeBuilder = StringBuilder()
        var emittedNarrativeLength = 0
        if (syncOnlyEntry != null) {
            narrativeBuilder.append(syncOnlyEntry.narrative)
            emit(GameEvent.Loading("正在重新同步第${syncOnlyEntry.turn}轮状态..."))
        } else try {
            requestNarrative(
                narrativeMessages,
                temperature = if (routeLocality.narrative) 0.55f else 0.72f
            ).collect { chunk ->
                narrativeBuilder.append(chunk)
                val preview = NarrativeStreamPreviewPolicy.extract(narrativeBuilder.toString())
                if (preview.length > emittedNarrativeLength) {
                    emit(GameEvent.NarrativeChunk(preview.substring(emittedNarrativeLength)))
                    emittedNarrativeLength = preview.length
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CompletionLengthException) {
            Log.w(TAG, "Narrative reached output limit; retrying as a complete response; type=${e.logType()}")
            emit(GameEvent.Loading("剧情较长，正在自动补全..."))
            try {
                val partialResponse = narrativeBuilder.toString()
                val retryPrompt = if (partialResponse.isBlank()) {
                    narrativePrompt
                } else {
                    promptBuilder.buildNarrativeRepairPrompt(
                        originalPrompt = narrativePrompt,
                        rawResponse = partialResponse,
                        issue = "上一次输出被长度上限截断，请重建完整 JSON，并保留全部动作与后续剧情。"
                    )
                }
                val retryResponse = requestNarrativeRepair(retryPrompt, temperature = 0.55f)
                narrativeBuilder.clear()
                narrativeBuilder.append(retryResponse)
            } catch (retryError: CancellationException) {
                throw retryError
            } catch (retryError: Exception) {
                Log.e(TAG, "Narrative completion retry failed; type=${retryError.logType()}")
                emit(GameEvent.Error("AI 剧情生成失败：${retryError.message}"))
                return@flow
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Narrative streaming failed; type=${e.logType()}")
            emit(GameEvent.Error("AI 剧情生成失败：${e.message}"))
            return@flow
        }

        val fullResponse = narrativeBuilder.toString()

        // 解析 JSON（支持流式累积中完成JSON）
        var narrativeResponse = fullResponse
        var narrativeResult = if (syncOnlyEntry != null) {
            NarrativeResult(
                narrative = syncOnlyEntry.narrative,
                summary = allSummaries.lastOrNull { it.turn == syncOnlyEntry.turn }?.summary.orEmpty()
            )
        } else try {
            prepareNarrativeForValidation(
                result = NarrativeIdentityAnchorPolicy.apply(
                    result = NarrativeResultContentPolicy.normalize(
                        jsonParser.parseNarrative(narrativeResponse)
                    ),
                    anchors = identityAnchors,
                    playerName = playerState.name
                ),
                playerName = playerState.name
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Narrative JSON parse failed; requesting a repaired response; type=${e.logType()}")
            emit(GameEvent.Loading("正在修复剧情格式..."))
            try {
                narrativeResponse = requestNarrativeRepair(
                    prompt = promptBuilder.buildNarrativeRepairPrompt(
                        originalPrompt = narrativePrompt,
                        rawResponse = narrativeResponse,
                        issue = "上一次输出不是可解析的完整 JSON；请重建全部字段，不要遗漏正文"
                    ),
                    temperature = 0.45f
                )
                prepareNarrativeForValidation(
                    result = NarrativeIdentityAnchorPolicy.apply(
                        result = NarrativeResultContentPolicy.normalize(
                            jsonParser.parseNarrative(narrativeResponse)
                        ),
                        anchors = identityAnchors,
                        playerName = playerState.name
                    ),
                    playerName = playerState.name
                )
            } catch (repairError: CancellationException) {
                throw repairError
            } catch (repairError: Exception) {
                Log.e(TAG, "Narrative JSON repair failed; type=${repairError.logType()}")
                emit(GameEvent.Error("AI 返回的剧情格式无法修复，请重试本轮行动。"))
                return@flow
            }
        }
        var narrativeIssue = if (stateSyncOnly) {
            null
        } else {
            NarrativeQualityPolicy.coverageIssue(
                result = narrativeResult,
                userMessage = effectiveUserMessage,
                knownCharacters = allCharacters,
                recentNarratives = recentNarratives,
                currentGoal = narrativeGoal,
                playerName = playerState.name,
                enforceTargetLength = true
            )
        }
        if (!stateSyncOnly && narrativeIssue != null) {
            val repairOutcome = repairNarrativeQuality(
                initialResult = narrativeResult,
                initialResponse = narrativeResponse,
                initialIssue = narrativeIssue,
                narrativePrompt = narrativePrompt,
                userMessage = effectiveUserMessage,
                currentGoal = narrativeGoal,
                playerName = playerState.name,
                identityAnchors = identityAnchors,
                knownCharacters = allCharacters,
                recentNarratives = recentNarratives,
                onLoading = { emit(GameEvent.Loading(it)) }
            )
            narrativeResult = repairOutcome.result
            narrativeResponse = repairOutcome.response
            narrativeIssue = repairOutcome.issue
        }
        if (NarrativeQualityPolicy.containsAgencyViolation(narrativeIssue)) {
            Log.e(TAG, "Narrative rejected after repair because it overrides player agency")
            emit(GameEvent.Error("AI 尝试替你作出关键决定，本轮没有保存。请重试这次行动。"))
            return@flow
        }
        if (NarrativeQualityPolicy.containsContinuationRepetition(narrativeIssue)) {
            Log.e(TAG, "Narrative rejected after repair because it repeats the prior turn")
            emit(GameEvent.Error("AI 重复了上一轮内容，本轮没有保存。点击重试即可再次生成。"))
            return@flow
        }
        if (NarrativeQualityPolicy.containsBlockingIssue(narrativeIssue)) {
            Log.e(TAG, "Narrative rejected after repair because critical quality issues remain")
            emit(GameEvent.Error("AI 连续修订后仍遗漏关键动作、人物或现场反馈，本轮没有保存。请重试这次行动。"))
            return@flow
        }
        if (NarrativeQualityPolicy.containsTargetLengthIssue(narrativeIssue)) {
            Log.w(
                TAG,
                "Narrative accepted outside target length without automatic repair; " +
                    "narrativeLength=${narrativeResult.narrative.count { !it.isWhitespace() }}"
            )
        }
        val finalNarrative = CharacterNamingPolicy.normalizeNarrativeText(
            narrativeResult.narrative
        )
        val summaryText = if (syncOnlyEntry != null) {
            allSummaries.lastOrNull { it.turn == syncOnlyEntry.turn }?.summary.orEmpty()
        } else {
            CharacterNamingPolicy.normalizeNarrativeText(narrativeResult.summary)
        }
        narrativeResult = NarrativeCharacterGroundingPolicy.reconcile(
            result = narrativeResult,
            narrative = finalNarrative,
            knownCharacters = allCharacters,
            playerName = playerState.name
        )

        if (finalNarrative.isBlank()) {
            emit(GameEvent.Error("AI 没有返回可用正文，请重试本轮行动。"))
            return@flow
        }

        // ========== 步骤7：保存文件 ==========
        val newRound = syncOnlyEntry?.turn ?: (maxOf(
            storyState.currentRound,
            allNarratives.maxOfOrNull { it.turn } ?: 0
        ) + 1)
        val currentTime = fileStorage.getCurrentTimeString()

        if (syncOnlyEntry == null) {
            fileStorage.saveNarrative(characterId, NarrativeEntry(
                turn = newRound, narrative = finalNarrative,
                userInput = effectiveUserMessage, createdAt = currentTime
            ))

            if (summaryText.isNotBlank()) {
                fileStorage.saveSummary(characterId, SummaryEntry(
                    turn = newRound, summary = summaryText, createdAt = currentTime
                ))
            }

            // Persist the recovery marker before any state-sync network call. If Android kills
            // the process from this point onward, the next round can replay the saved narrative.
            storyState = StateSyncPolicy.markPending(
                state = storyState.copy(currentRound = newRound),
                round = newRound
            )
            fileStorage.saveStoryState(characterId, storyState)

            emit(GameEvent.NarrativeComplete(finalNarrative))
            emit(GameEvent.Saved("剧情已保存（第${newRound}轮）"))
        }

        // ========== 步骤8：任务分配调用 ==========
        emit(GameEvent.Loading("正在分析状态变化..."))

        val stateSyncCharacters = CharacterContextPolicy.selectForStateSync(
            allCharacters = allCharacters,
            narrativeCharacters = activeCharacters,
            currentNarrative = finalNarrative,
            recoveryNarratives = pendingRecoveryNarratives
        )
        val characterGroundingContext = buildString {
            pendingRecoveryNarratives.takeLast(3).forEach { appendLine(it.narrative) }
            append(finalNarrative)
        }

        val taskPrompt = promptBuilder.buildTaskAssignmentPrompt(
            worldSetting = worldSetting,
            playerState = playerState,
            storyState = storyState,
            activeCharacters = stateSyncCharacters,
            worldbookEntries = matchedWorldbook.ifEmpty { allWorldbookEntries.take(10) },
            narrative = finalNarrative,
            summary = summaryText,
            recentNarratives = recentNarratives,
            endingScene = narrativeResult.endingScene,
            introducedCharacters = narrativeResult.introducedCharacters
        )
        val essentialTaskPrompt = promptBuilder.buildEssentialStateSyncPrompt(
            worldSetting = worldSetting,
            playerState = playerState,
            storyState = storyState,
            activeCharacters = stateSyncCharacters,
            narrative = finalNarrative,
            summary = summaryText,
            recentNarratives = recentNarratives,
            endingScene = narrativeResult.endingScene,
            introducedCharacters = narrativeResult.introducedCharacters
        )

        val taskAssignment = try {
            requestTaskAssignment(
                taskPrompt,
                essentialTaskPrompt,
                routeLocality.taskAssignment,
                validate = { assignment ->
                    TaskAssignmentQualityPolicy.requireNarrativeConsistency(
                        worldSetting = worldSetting,
                        narrative = "$effectiveUserMessage\n$finalNarrative",
                        assignment = assignment
                    )
                }
            ) { assignment ->
                assignment
                .let { candidate ->
                    EndingScenePolicy.reconcile(
                        assignment = candidate,
                        endingScene = narrativeResult.endingScene,
                        playerName = playerState.name,
                        sceneDescription = summaryText
                    )
                }
                .let { assignment ->
                    TaskAssignmentRosterPolicy.reconcile(
                        assignment = assignment,
                        knownCharacters = allCharacters,
                        introducedCharacters = narrativeResult.introducedCharacters,
                        playerName = playerState.name,
                        narrativeContext = characterGroundingContext,
                        currentNarrative = finalNarrative
                    )
                }
                .let { assignment ->
                    TaskAssignmentExecutionPolicy.sanitize(
                        assignment = assignment,
                        playerName = playerState.name
                    )
                }
            }.also { assignment ->
                Log.d(TAG, "TaskAssignment: playerUpdate=${assignment.playerUpdate != null}, " +
                        "storyUpdate=${assignment.storyUpdate != null}, " +
                        "newChars=${assignment.newCharacters.size}, " +
                        "retired=${assignment.retiredCharacters.size}, " +
                        "updates=${assignment.characterUpdates.size}, " +
                        "wbUpdates=${assignment.worldbookUpdates.size}, " +
                        "transfers=${assignment.itemTransfers.size}, " +
                        "events=${assignment.events.size}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Task assignment failed after retry; type=${e.logType()}")
            storyState = StateSyncPolicy.markPending(storyState, newRound)
            try {
                fileStorage.saveStoryState(characterId, storyState)
            } catch (saveError: CancellationException) {
                throw saveError
            } catch (saveError: Exception) {
                Log.e(TAG, "Failed to persist pending state-sync marker; type=${saveError.logType()}")
            }
            emit(GameEvent.Warning("本轮剧情已保存，状态尚未同步；请点击“补同步”完成恢复后再继续剧情。"))
            emit(GameEvent.RoundComplete(newRound))
            return@flow
        }

        emit(GameEvent.TaskComplete(taskAssignment))

        // ========== 步骤9：逐个执行任务 ==========
        val stateSyncRound = StateSyncPolicy.pendingFromRound(storyState) ?: newRound
        try {
        var stateSyncIncomplete = false
        playerState = recoverPreparedStateMutations(
            characterId = characterId,
            round = stateSyncRound,
            playerState = playerState,
            emitEvent = { emit(it) }
        )

        taskAssignment.storyUpdate?.let { update ->
            storyState = applyStoryUpdate(storyState, update)
        }
        val completionContext = buildString {
            allSummaries.forEach { appendLine(it.summary) }
            recentNarratives.forEach { appendLine(it.narrative) }
            appendLine(finalNarrative)
            appendLine(summaryText)
        }
        val reconciledPending = PendingEventPolicy.reconcile(
            events = storyState.pendingEvents,
            completedContext = completionContext
        )
        if (reconciledPending.size != storyState.pendingEvents.size) {
            Log.d(TAG, "Pending events reconciled: before=${storyState.pendingEvents.size}, after=${reconciledPending.size}")
            storyState = storyState.copy(pendingEvents = reconciledPending)
        }
        val prioritizedPending = PendingEventPolicy.prioritizeImmediateConsequences(
            events = storyState.pendingEvents,
            narrative = finalNarrative
        )
        if (prioritizedPending != storyState.pendingEvents) {
            storyState = storyState.copy(pendingEvents = prioritizedPending)
            Log.d(TAG, "Immediate consequence prioritized; pending=${prioritizedPending.size}")
        }
        Log.d(TAG, "Story state updated: active=${storyState.activeCharacters.size}, pending=${storyState.pendingEvents.size}")

        // 1. 主角状态更新
        taskAssignment.playerUpdate?.let { update ->
            emit(GameEvent.Loading("正在更新主角状态..."))
            val updated = applyPlayerUpdate(playerState, update, worldSetting.characterFields)
            val mutation = applyPlayerMutationOnce(
                characterId = characterId,
                round = stateSyncRound,
                current = playerState,
                updated = updated
            )
            playerState = mutation.value
            if (mutation.applied) {
                Log.d(TAG, "Player state updated")
                emit(GameEvent.PlayerUpdated(playerState))
            }
        }
        run {
            val current = playerState
            val aligned = PlayerGoalPolicy.alignWithPendingEvents(current, storyState.pendingEvents)
            if (aligned != current) {
                fileStorage.savePlayerState(characterId, aligned)
                playerState = aligned
                Log.d(TAG, "Player goal aligned with pending event")
                emit(GameEvent.PlayerUpdated(aligned))
            }
        }

        // 2. 新角色创建
        val normalizedNewCharacterNames = mutableMapOf<String, String>()
        taskAssignment.newCharacters.forEach { newChar ->
            emit(GameEvent.Loading("正在创建角色：${newChar.name}"))
            val existingCard = resolveCharacterCard(characterId, newChar.name)
            val initialCard = existingCard ?: run {
                val rawInitialCard = newChar.initialStatus ?: CharacterCard(
                    id = newChar.name, name = newChar.name, status = "active",
                    race = newChar.race, age = newChar.age, gender = newChar.gender,
                    occupation = newChar.occupation, personality = newChar.personality,
                    background = newChar.background, appearance = newChar.appearance,
                    relationshipStatus = TaskAssignmentRosterPolicy.relationshipStatusFor(newChar.relationshipToPlayer),
                    relationships = playerState.name.takeIf { it.isNotBlank() }?.let { playerName ->
                        mapOf(playerName to Relationship(stage = newChar.relationshipToPlayer.ifBlank { "初识" }))
                    } ?: emptyMap(),
                    customAttributes = CharacterFieldPolicy.normalizeImportedAttributes(
                        emptyMap(),
                        worldSetting.characterFields,
                        CharacterFieldDefinition.TARGET_NPC
                    ),
                    createdBy = "ai", createdAt = currentTime, lastModified = currentTime
                )
                validateCharacterCard(
                    applyNpcAttributes(
                        card = rawInitialCard.copy(
                            createdBy = rawInitialCard.createdBy.ifBlank { "ai" },
                            createdAt = rawInitialCard.createdAt.ifBlank { currentTime },
                            lastModified = currentTime
                        ),
                        attributes = newChar.attributes,
                        characterFields = worldSetting.characterFields,
                        playerName = playerState.name.ifBlank { "主角" },
                        includeManual = true
                    ),
                    worldSetting.characterFields
                ).let { card ->
                    CharacterProfilePolicy.reconcile(
                        card = card,
                        playerName = playerState.name.ifBlank { "主角" }
                    )
                }.also { created ->
                    fileStorage.saveCharacterCard(characterId, created)
                    emit(GameEvent.CharacterCreated(created))
                }
            }
            normalizedNewCharacterNames[newChar.name] = initialCard.name

            if (routeLocality.characterState) {
                Log.d(TAG, "New character initialized from compact local state sync")
                if (existingCard == null) emit(GameEvent.CharacterUpdated(initialCard))
            } else {
                // Remote models can afford a second pass that enriches short-term card state.
                emit(GameEvent.Loading("正在生成角色状态：${newChar.name}"))
                try {
                    val mutation = applyCharacterStateMutationOnce(
                        characterId = characterId,
                        round = stateSyncRound,
                        card = initialCard
                    ) { currentCard ->
                        val statePrompt = promptBuilder.buildCharacterStatePrompt(
                            worldSetting = worldSetting, character = currentCard,
                            recentNarratives = recentNarratives, narrative = finalNarrative,
                            userMessage = effectiveUserMessage,
                            playerName = playerState.name.ifBlank { "主角" },
                            updateReason = "补全本轮首次登场角色状态",
                            recoveryNarratives = pendingRecoveryNarratives
                        )
                        val patch = requestCharacterStatePatch(statePrompt)
                        applyCharacterStatePatch(
                            card = currentCard,
                            patch = patch,
                            playerName = playerState.name.ifBlank { "主角" },
                            currentTime = currentTime,
                            characterFields = worldSetting.characterFields
                        )
                    }
                    if (mutation.applied) {
                        emit(GameEvent.CharacterUpdated(mutation.value))
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "New character state generation failed; type=${e.logType()}")
                    stateSyncIncomplete = true
                    emit(GameEvent.CharacterUpdated(initialCard))
                }
            }
        }

        // 3. 退场角色
        taskAssignment.retiredCharacters.forEach { retired ->
            emit(GameEvent.Loading("角色退场：${retired.name}"))
            val card = resolveCharacterCard(characterId, retired.name)
            if (card != null) {
                fileStorage.saveCharacterCard(characterId, card.copy(status = "pool", lastModified = currentTime))
                emit(GameEvent.CharacterRetired(card.name))
            } else {
                Log.w(TAG, "Retired character could not be resolved")
                stateSyncIncomplete = true
            }
        }

        // 4. NPC角色状态更新（逐个串行）
        taskAssignment.characterUpdates.forEach { update ->
            emit(GameEvent.Loading("正在更新角色：${update.name}"))
            val card = resolveCharacterCard(characterId, update.name)
            if (card != null) {
                try {
                    val mutation = applyCharacterStateMutationOnce(
                        characterId = characterId,
                        round = stateSyncRound,
                        card = card
                    ) { currentCard ->
                        val statePrompt = promptBuilder.buildCharacterStatePrompt(
                            worldSetting = worldSetting, character = currentCard,
                            recentNarratives = recentNarratives, narrative = finalNarrative,
                            userMessage = effectiveUserMessage,
                            playerName = playerState.name.ifBlank { "主角" },
                            updateReason = update.reason,
                            recoveryNarratives = pendingRecoveryNarratives
                        )
                        val patch = requestCharacterStatePatch(statePrompt)
                        applyCharacterStatePatch(
                            card = currentCard,
                            patch = patch,
                            playerName = playerState.name.ifBlank { "主角" },
                            currentTime = currentTime,
                            characterFields = worldSetting.characterFields
                        )
                    }
                    if (mutation.applied) {
                        Log.d(TAG, "Character state updated")
                        emit(GameEvent.CharacterUpdated(mutation.value))
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Character state update failed; type=${e.logType()}")
                    stateSyncIncomplete = true
                    emit(GameEvent.CharacterUpdated(card))
                }
            } else {
                Log.w(TAG, "Character update skipped; no unambiguous match")
                stateSyncIncomplete = true
            }
        }

        // 5. 世界书更新
        if (applyWorldbookUpdates(
                characterId = characterId,
                updates = taskAssignment.worldbookUpdates,
                round = stateSyncRound,
                currentTime = currentTime,
                emitEvent = { emit(it) }
            )
        ) {
            stateSyncIncomplete = true
        }

        // 6. 物品转移
        val recoveryTransfers = fileStorage
            .loadInventoryTransferJournals(characterId, stateSyncRound)
            .map { journal ->
                ItemTransfer(
                    item = journal.item.name,
                    quantity = journal.quantity,
                    from = inventoryParticipantReference(
                        characterId = characterId,
                        participantKey = journal.sourceKey,
                        playerName = playerState.name
                    ),
                    to = inventoryParticipantReference(
                        characterId = characterId,
                        participantKey = journal.targetKey,
                        playerName = playerState.name
                    ),
                    reason = "恢复未完成的库存转移"
                )
            }
        val transferResult = applyItemTransfers(
            characterId = characterId,
            transfers = recoveryTransfers + taskAssignment.itemTransfers,
            initialPlayerState = playerState,
            round = stateSyncRound,
            currentTime = currentTime,
            emitEvent = { emit(it) }
        )
        playerState = transferResult.playerState
        if (transferResult.isIncomplete) stateSyncIncomplete = true

        // 7. 事件记录到世界书的status_history
        applyEventRecords(
            characterId = characterId,
            events = taskAssignment.events,
            round = stateSyncRound,
            currentTime = currentTime,
            emitEvent = { emit(it) }
        )

        // ========== 步骤10：完成 ==========
        val normalizedSceneCharacters = normalizeSceneCharacterNames(
            characterId = characterId,
            names = storyState.activeCharacters,
            knownRenames = normalizedNewCharacterNames
        )
        val normalizedRetiredCharacters = normalizeSceneCharacterNames(
            characterId = characterId,
            names = taskAssignment.retiredCharacters.map { it.name },
            knownRenames = normalizedNewCharacterNames
        ).toSet()
        val inferredNewCharacters = if (taskAssignment.storyUpdate?.activeCharacters == null) {
            taskAssignment.newCharacters.map { normalizedNewCharacterNames[it.name] ?: it.name }
        } else {
            emptyList()
        }
        val updatedActiveChars = (normalizedSceneCharacters + inferredNewCharacters)
            .filterNot { it in normalizedRetiredCharacters }
            .distinct()

        val updatedStoryState = storyState.copy(activeCharacters = updatedActiveChars)
        storyState = if (stateSyncIncomplete) {
            StateSyncPolicy.markPending(updatedStoryState, newRound)
        } else {
            fileStorage.clearInventoryTransferJournals(characterId, stateSyncRound)
            fileStorage.clearStateMutationJournals(characterId, stateSyncRound)
            StateSyncPolicy.clearPending(updatedStoryState)
        }
        fileStorage.saveStoryState(characterId, storyState)
        if (stateSyncIncomplete) {
            emit(GameEvent.Warning("剧情和主要状态已保存，但个别角色卡仍待补同步；请完成补同步后继续剧情。"))
        }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Applying task assignment failed; preserving round for later reconciliation; type=${e.logType()}"
            )
            storyState = StateSyncPolicy.markPending(storyState, newRound)
            try {
                fileStorage.saveStoryState(characterId, storyState)
            } catch (saveError: CancellationException) {
                throw saveError
            } catch (saveError: Exception) {
                Log.e(TAG, "Failed to persist pending state-sync marker; type=${saveError.logType()}")
            }
            emit(GameEvent.Warning("剧情已保存，但部分状态更新未完成；请完成补同步后继续剧情。"))
        }
        emit(GameEvent.RoundComplete(newRound))
    }.flowOn(Dispatchers.IO)

    // ========== 辅助方法 ==========

    private data class NarrativeRepairOutcome(
        val result: NarrativeResult,
        val response: String,
        val issue: String?
    )

    private suspend fun repairNarrativeQuality(
        initialResult: NarrativeResult,
        initialResponse: String,
        initialIssue: String,
        narrativePrompt: String,
        userMessage: String,
        currentGoal: String,
        playerName: String,
        identityAnchors: List<NarrativeIdentityAnchor>,
        knownCharacters: List<CharacterCard>,
        recentNarratives: List<NarrativeEntry>,
        onLoading: suspend (String) -> Unit
    ): NarrativeRepairOutcome {
        var result = initialResult
        var response = initialResponse
        var issue: String? = initialIssue
        var qualityAttempt = 0
        var fullRepairAttempts = 0
        var styleRepairAttempts = 0
        while (issue != null && qualityAttempt < 3) {
            if (NarrativeQualityPolicy.isNonBlockingLengthIssue(issue)) {
                break
            }
            val styleOnlyRepair = NarrativeQualityPolicy.isStyleOnlyIssue(issue)
            if (
                (styleOnlyRepair && styleRepairAttempts >= 2) ||
                (!styleOnlyRepair && fullRepairAttempts >= 3)
            ) {
                break
            }
            qualityAttempt += 1
            if (styleOnlyRepair) styleRepairAttempts += 1 else fullRepairAttempts += 1
            Log.w(
                TAG,
                "Narrative quality retry $qualityAttempt: issues=${issue.issueCount()}; " +
                    "mode=${if (styleOnlyRepair) "style" else "full"}; " +
                    "responseLength=${response.length}; issue=${issue.take(420)}"
            )
            onLoading(
                when {
                    styleOnlyRepair -> "正在精简叙事..."
                    qualityAttempt == 1 -> "正在补全遗漏剧情..."
                    else -> "正在校正人物与叙事..."
                }
            )
            try {
                val repairPrompt = buildQualityRepairPrompt(
                    styleOnlyRepair = styleOnlyRepair,
                    fullRepairAttempts = fullRepairAttempts,
                    narrativeResponse = response,
                    narrativeIssue = issue,
                    narrativePrompt = narrativePrompt,
                    userMessage = userMessage,
                    currentGoal = currentGoal,
                    playerName = playerName,
                    identityAnchors = identityAnchors
                )
                val repairedResponse = requestNarrativeRepair(
                    prompt = repairPrompt,
                    temperature = if (styleOnlyRepair) 0.25f else 0.55f
                )
                val repairedResult = prepareNarrativeForValidation(
                    result = NarrativeIdentityAnchorPolicy.apply(
                        result = NarrativeResultContentPolicy.normalize(
                            jsonParser.parseNarrative(repairedResponse)
                        ),
                        anchors = identityAnchors,
                        playerName = playerName
                    ),
                    playerName = playerName
                )
                val repairedIssue = NarrativeQualityPolicy.coverageIssue(
                    result = repairedResult,
                    userMessage = userMessage,
                    knownCharacters = knownCharacters,
                    recentNarratives = recentNarratives,
                    currentGoal = currentGoal,
                    playerName = playerName,
                    enforceTargetLength = true
                )
                if (shouldAdoptNarrativeRepair(result, repairedResult, issue, repairedIssue)) {
                    result = repairedResult
                    response = repairedResponse
                    issue = repairedIssue
                }
                if (issue == null) {
                    Log.d(TAG, "Narrative quality retry passed; narrativeLength=${repairedResult.narrative.length}")
                } else {
                    Log.w(TAG, "Narrative quality retry remained imperfect; issues=${issue.issueCount()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Narrative quality retry failed; keeping best response; type=${e.logType()}")
                break
            }
        }
        if (NarrativeQualityPolicy.isNonBlockingLengthIssue(issue)) {
            Log.w(
                TAG,
                "Narrative length target not met; automatic length repair disabled; " +
                    "issue=${issue?.take(420)}"
            )
        } else {
            issue?.let {
                Log.w(
                    TAG,
                    "Narrative quality attempts exhausted: issues=${it.issueCount()}; issue=${it.take(420)}"
                )
            }
        }
        return NarrativeRepairOutcome(result, response, issue)
    }

    private fun buildQualityRepairPrompt(
        styleOnlyRepair: Boolean,
        fullRepairAttempts: Int,
        narrativeResponse: String,
        narrativeIssue: String,
        narrativePrompt: String,
        userMessage: String,
        currentGoal: String,
        playerName: String,
        identityAnchors: List<NarrativeIdentityAnchor>
    ): String = when {
        styleOnlyRepair -> promptBuilder.buildNarrativeStyleRepairPrompt(
            rawResponse = narrativeResponse,
            issue = narrativeIssue
        )
        fullRepairAttempts >= 2 -> promptBuilder.buildNarrativeFocusedRepairPrompt(
            rawResponse = narrativeResponse,
            issue = narrativeIssue,
            userMessage = userMessage,
            currentGoal = currentGoal,
            playerName = playerName,
            identityAnchors = identityAnchors
        )
        else -> promptBuilder.buildNarrativeRepairPrompt(
            originalPrompt = narrativePrompt,
            rawResponse = narrativeResponse,
            issue = narrativeIssue
        )
    }

    private fun shouldAdoptNarrativeRepair(
        currentResult: NarrativeResult,
        repairedResult: NarrativeResult,
        currentIssue: String?,
        repairedIssue: String?
    ): Boolean {
        if (repairedResult.narrative.isBlank()) return false
        return NarrativeQualityPolicy.isRepairImprovement(currentIssue, repairedIssue) ||
            NarrativeQualityPolicy.isTargetLengthImprovement(
                currentNarrative = currentResult.narrative,
                repairedNarrative = repairedResult.narrative,
                currentIssue = currentIssue,
                repairedIssue = repairedIssue
            )
    }

    private fun prepareNarrativeForValidation(
        result: NarrativeResult,
        playerName: String
    ): NarrativeResult = NarrativeCharacterGroundingPolicy.excludePlayerFromEndingScene(
        result = result,
        playerName = playerName
    )

    private fun requestNarrative(
        messages: List<ChatMessage>,
        temperature: Float
    ): Flow<String> = aiService.chatStream(
        messages = messages,
        temperature = temperature,
        maxTokens = 5600,
        jsonMode = true,
        structuredOutputKind = StructuredOutputKind.NARRATIVE,
        stage = AiCallStage.NARRATIVE
    )

    private suspend fun requestNarrativeRepair(
        prompt: String,
        temperature: Float
    ): String = aiService.chatComplete(
        messages = listOf(ChatMessage(role = "user", content = prompt)),
        temperature = temperature,
        maxTokens = 5600,
        jsonMode = true,
        structuredOutputKind = StructuredOutputKind.NARRATIVE,
        stage = AiCallStage.NARRATIVE_REPAIR
    )

    private suspend fun applyWorldbookUpdates(
        characterId: Long,
        updates: List<WorldbookUpdate>,
        round: Int,
        currentTime: String,
        emitEvent: suspend (GameEvent) -> Unit
    ): Boolean {
        var isIncomplete = false
        updates.forEach { update ->
            val action = update.action.trim().lowercase()
            val reference = update.id?.trim()?.takeIf { it.isNotBlank() }
                ?: update.name?.let(WorldbookContentPolicy::normalizeName).orEmpty()
            val operationKey = "worldbook:$action:${reference.lowercase()}"
            val operationId = StateMutationPolicy.operationId(round, operationKey)
            val existingJournal = fileStorage.loadStateMutationJournal(characterId, operationId)
            when (action) {
                "create" -> {
                    val entryName = WorldbookContentPolicy.normalizeName(update.name.orEmpty())
                    if (entryName.isBlank() && existingJournal == null) {
                        Log.w(TAG, "Worldbook create skipped because name is blank")
                        isIncomplete = true
                        return@forEach
                    }
                    val entry = existingJournal?.let { null } ?: WorldbookEntry(
                        id = update.id?.trim()?.takeIf { it.isNotBlank() }
                            ?: "wb_${round}_${operationId.takeLast(12)}",
                        type = WorldbookContentPolicy.normalizeType(update.type),
                        name = entryName,
                        description = WorldbookContentPolicy.normalizeDescription(update.description.orEmpty()),
                        keywords = WorldbookContentPolicy.normalizeKeywords(update.keywords.orEmpty()),
                        isActive = true,
                        createdBy = "ai",
                        createdAt = currentTime,
                        lastModified = currentTime
                    )
                    val before = entry?.let { fileStorage.loadWorldbookEntry(characterId, it.id) }
                    applyWorldbookMutationOnce(
                        characterId = characterId,
                        round = round,
                        operationKey = operationKey,
                        before = before,
                        after = entry,
                        emitEvent = emitEvent
                    )
                }
                "update" -> {
                    val existing = update.id?.let { fileStorage.loadWorldbookEntry(characterId, it) }
                        ?: update.name?.let { fileStorage.loadWorldbookEntryByName(characterId, it) }
                    if (existing == null && existingJournal == null) {
                        Log.w(TAG, "Worldbook update target could not be resolved")
                        isIncomplete = true
                        return@forEach
                    }
                    val updated = existing?.let { current ->
                        val updatedStatus = update.changes?.get("status")
                            ?.toString()
                            ?.let(WorldbookContentPolicy::normalizeStatus)
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        WorldbookContentPolicy.normalize(current.copy(
                            type = update.type?.takeIf { it.isNotBlank() }
                                ?.let { type -> WorldbookContentPolicy.normalizeType(type) }
                                ?: current.type,
                            name = update.name
                                ?.let(WorldbookContentPolicy::normalizeName)
                                ?.takeIf { it.isNotBlank() }
                                ?: current.name,
                            description = update.description ?: current.description,
                            keywords = update.keywords?.let(WorldbookContentPolicy::normalizeKeywords)
                                ?: current.keywords,
                            status = updatedStatus ?: current.status,
                            lastModified = currentTime
                        ))
                    }
                    applyWorldbookMutationOnce(
                        characterId = characterId,
                        round = round,
                        operationKey = operationKey,
                        before = existing,
                        after = updated,
                        emitEvent = emitEvent
                    )
                }
                "delete" -> {
                    val existing = update.id?.let { fileStorage.loadWorldbookEntry(characterId, it) }
                        ?: update.name?.let { fileStorage.loadWorldbookEntryByName(characterId, it) }
                    if (existing == null && existingJournal == null) {
                        Log.w(TAG, "Worldbook delete target could not be resolved")
                        isIncomplete = true
                        return@forEach
                    }
                    applyWorldbookMutationOnce(
                        characterId = characterId,
                        round = round,
                        operationKey = operationKey,
                        before = existing,
                        after = null,
                        emitEvent = emitEvent
                    )
                }
                else -> {
                    Log.w(TAG, "Unsupported worldbook action")
                    isIncomplete = true
                }
            }
        }
        return isIncomplete
    }

    private suspend fun applyWorldbookMutationOnce(
        characterId: Long,
        round: Int,
        operationKey: String,
        before: WorldbookEntry?,
        after: WorldbookEntry?,
        emitEvent: suspend (GameEvent) -> Unit
    ): Boolean {
        val operationId = StateMutationPolicy.operationId(round, operationKey)
        val existingJournal = fileStorage.loadStateMutationJournal(characterId, operationId)
        val targetKey = existingJournal?.targetKey
            ?: after?.id
            ?: before?.id
            ?: throw IllegalStateException("世界书状态操作缺少目标")
        val current = fileStorage.loadWorldbookEntry(characterId, targetKey)
        val decision = if (existingJournal == null) {
            StateMutationPolicy.prepare(
                round = round,
                operationKey = operationKey,
                entityType = StateMutationJournal.ENTITY_WORLDBOOK,
                targetKey = targetKey,
                before = stateSnapshot(before),
                after = stateSnapshot(after)
            )
        } else {
            StateMutationPolicy.resume(
                journal = existingJournal,
                expectedOperationId = operationId,
                expectedEntityType = StateMutationJournal.ENTITY_WORLDBOOK,
                expectedTargetKey = targetKey,
                current = stateSnapshot(current)
            )
        }
        val plan = decision.plan
            ?: throw IllegalStateException("世界书状态检查点冲突：${decision.failure ?: "UNKNOWN"}")
        if (existingJournal == null) {
            fileStorage.saveStateMutationJournal(characterId, plan.journal)
        }
        if (plan.alreadyCompleted) {
            if (plan.journal.status != StateMutationJournal.STATUS_COMPLETED) {
                fileStorage.saveStateMutationJournal(characterId, plan.journal)
            }
            return false
        }
        check(plan.applyAfter) { "世界书状态检查点不可执行" }
        if (plan.journal.afterExists) {
            val saved = requireStatePayload(plan.journal, WorldbookEntry::class.java)
            fileStorage.saveWorldbookEntry(characterId, saved)
            emitEvent(GameEvent.WorldbookUpdated(saved))
        } else {
            fileStorage.deleteWorldbookEntry(characterId, targetKey)
            emitEvent(GameEvent.WorldbookDeleted(targetKey))
        }
        fileStorage.saveStateMutationJournal(
            characterId,
            StateMutationPolicy.markCompleted(plan.journal)
        )
        return true
    }

    private suspend fun applyItemTransfers(
        characterId: Long,
        transfers: List<ItemTransfer>,
        initialPlayerState: PlayerState,
        round: Int,
        currentTime: String,
        emitEvent: suspend (GameEvent) -> Unit
    ): ItemTransferApplicationResult {
        var playerState = initialPlayerState
        var isIncomplete = false
        val processedOperationIds = mutableSetOf<String>()
        transfers.forEach { transfer ->
            val sourceName = transfer.from.trim()
            val targetName = transfer.to.trim()
            val sourceCard = if (sourceName == playerState.name) null else {
                resolveCharacterCard(characterId, sourceName)
            }
            val targetCard = if (targetName == playerState.name) null else {
                resolveCharacterCard(characterId, targetName)
            }
            if ((sourceName != playerState.name && sourceCard == null) ||
                (targetName != playerState.name && targetCard == null)
            ) {
                Log.w(TAG, "Item transfer participants could not be resolved")
                isIncomplete = true
                return@forEach
            }
            if (sourceCard != null && targetCard != null &&
                sourceCard.id.ifBlank { sourceCard.name } == targetCard.id.ifBlank { targetCard.name }
            ) {
                Log.w(TAG, "Item transfer resolves to the same character")
                isIncomplete = true
                return@forEach
            }

            val sourceKey = if (sourceName == playerState.name) {
                "player"
            } else {
                requireNotNull(sourceCard).id.ifBlank { sourceCard.name }
            }
            val targetKey = if (targetName == playerState.name) {
                "player"
            } else {
                requireNotNull(targetCard).id.ifBlank { targetCard.name }
            }
            val sourceInventory = sourceCard?.inventory ?: playerState.inventory
            val targetInventory = targetCard?.inventory ?: playerState.inventory
            val operationId = InventoryTransferPolicy.operationId(
                round = round,
                itemName = transfer.item,
                sourceKey = sourceKey,
                targetKey = targetKey
            )
            if (!processedOperationIds.add(operationId)) return@forEach
            val existingJournal = fileStorage.loadInventoryTransferJournal(
                characterId = characterId,
                operationId = operationId
            )
            val decision = if (existingJournal == null) {
                InventoryTransferPolicy.prepare(
                    round = round,
                    itemName = transfer.item,
                    quantity = transfer.quantity,
                    sourceKey = sourceKey,
                    targetKey = targetKey,
                    sourceInventory = sourceInventory,
                    targetInventory = targetInventory
                )
            } else {
                InventoryTransferPolicy.resume(
                    journal = existingJournal,
                    requestedItemName = transfer.item,
                    requestedQuantity = transfer.quantity,
                    sourceKey = sourceKey,
                    targetKey = targetKey,
                    sourceInventory = sourceInventory,
                    targetInventory = targetInventory
                )
            }
            val plan = decision.plan
            if (plan == null) {
                Log.w(TAG, "Item transfer rejected; reason=${decision.failure ?: "UNKNOWN"}")
                isIncomplete = true
                return@forEach
            }
            if (existingJournal == null) {
                fileStorage.saveInventoryTransferJournal(characterId, plan.journal)
            }
            if (plan.alreadyCompleted) {
                if (plan.journal.status != InventoryTransferJournal.STATUS_COMPLETED) {
                    fileStorage.saveInventoryTransferJournal(
                        characterId,
                        InventoryTransferPolicy.markCompleted(plan.journal)
                    )
                }
                return@forEach
            }

            if (plan.writeSource) {
                if (sourceName == playerState.name) {
                    val updated = playerState.copy(inventory = plan.sourceInventory)
                    fileStorage.savePlayerState(characterId, updated)
                    playerState = updated
                    emitEvent(GameEvent.PlayerUpdated(updated))
                } else {
                    val card = requireNotNull(sourceCard)
                    val updated = card.copy(
                        inventory = plan.sourceInventory,
                        lastModified = currentTime
                    )
                    fileStorage.saveCharacterCard(characterId, updated)
                    emitEvent(GameEvent.CharacterUpdated(updated))
                }
            }

            if (plan.writeTarget) {
                if (targetName == playerState.name) {
                    val updated = playerState.copy(inventory = plan.targetInventory)
                    fileStorage.savePlayerState(characterId, updated)
                    playerState = updated
                    emitEvent(GameEvent.PlayerUpdated(updated))
                } else {
                    val card = requireNotNull(targetCard)
                    val updated = card.copy(
                        inventory = plan.targetInventory,
                        lastModified = currentTime
                    )
                    fileStorage.saveCharacterCard(characterId, updated)
                    emitEvent(GameEvent.CharacterUpdated(updated))
                }
            }
            fileStorage.saveInventoryTransferJournal(
                characterId,
                InventoryTransferPolicy.markCompleted(plan.journal)
            )
            emitEvent(GameEvent.ItemTransferred(transfer))
        }
        return ItemTransferApplicationResult(playerState, isIncomplete)
    }

    private suspend fun inventoryParticipantReference(
        characterId: Long,
        participantKey: String,
        playerName: String
    ): String {
        if (participantKey == "player") return playerName
        return fileStorage.loadCharacterCard(characterId, participantKey)?.name
            ?: resolveCharacterCard(characterId, participantKey)?.name
            ?: participantKey
    }

    private suspend fun applyEventRecords(
        characterId: Long,
        events: List<EventRecord>,
        round: Int,
        currentTime: String,
        emitEvent: suspend (GameEvent) -> Unit
    ) {
        events.forEach { event ->
            val operationKey = "event:${event.type.trim().lowercase()}:${event.target.trim().lowercase()}"
            val operationId = StateMutationPolicy.operationId(round, operationKey)
            val historyEntry = StatusHistory(
                time = "第${round}轮",
                status = event.type,
                event = event.description,
                causedBy = event.causedBy
            )
            val existing = fileStorage.loadWorldbookEntryByName(characterId, event.target)
            val updated = if (existing != null) {
                WorldbookContentPolicy.normalize(existing.copy(
                    status = event.type,
                    statusHistory = (existing.statusHistory + historyEntry).distinctBy { history ->
                        listOf(history.time, history.status, history.event, history.causedBy)
                    },
                    lastModified = currentTime
                ))
            } else {
                WorldbookContentPolicy.normalize(WorldbookEntry(
                    id = "event_${round}_${operationId.takeLast(12)}",
                    type = "event",
                    name = listOf(event.type, event.target)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                        .ifBlank { "第${round}轮剧情事件" },
                    description = event.description,
                    keywords = listOf(event.target, event.causedBy, event.type)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinct(),
                    status = event.type,
                    statusHistory = listOf(historyEntry),
                    isActive = true,
                    createdBy = "ai",
                    createdAt = currentTime,
                    lastModified = currentTime
                ))
            }
            val applied = applyWorldbookMutationOnce(
                characterId = characterId,
                round = round,
                operationKey = operationKey,
                before = existing,
                after = updated,
                emitEvent = emitEvent
            )
            if (applied) {
                emitEvent(GameEvent.EventRecorded(event.type, event.target))
            }
        }
    }

    private data class ItemTransferApplicationResult(
        val playerState: PlayerState,
        val isIncomplete: Boolean
    )

    private data class EntityMutationResult<T>(
        val value: T,
        val applied: Boolean
    )

    private suspend fun applyPlayerMutationOnce(
        characterId: Long,
        round: Int,
        current: PlayerState,
        updated: PlayerState
    ): EntityMutationResult<PlayerState> {
        if (updated == current) return EntityMutationResult(current, applied = false)
        val operationKey = "player_update"
        val operationId = StateMutationPolicy.operationId(round, operationKey)
        val existingJournal = fileStorage.loadStateMutationJournal(characterId, operationId)
        val currentSnapshot = stateSnapshot(current)
        val decision = if (existingJournal == null) {
            StateMutationPolicy.prepare(
                round = round,
                operationKey = operationKey,
                entityType = StateMutationJournal.ENTITY_PLAYER,
                targetKey = "player",
                before = currentSnapshot,
                after = stateSnapshot(updated)
            )
        } else {
            StateMutationPolicy.resume(
                journal = existingJournal,
                expectedOperationId = operationId,
                expectedEntityType = StateMutationJournal.ENTITY_PLAYER,
                expectedTargetKey = "player",
                current = currentSnapshot
            )
        }
        val plan = decision.plan
            ?: throw IllegalStateException("主角状态检查点冲突：${decision.failure ?: "UNKNOWN"}")
        if (existingJournal == null) {
            fileStorage.saveStateMutationJournal(characterId, plan.journal)
        }
        if (plan.alreadyCompleted) {
            if (plan.journal.status != StateMutationJournal.STATUS_COMPLETED) {
                fileStorage.saveStateMutationJournal(characterId, plan.journal)
            }
            return EntityMutationResult(current, applied = false)
        }
        check(plan.applyAfter) { "主角状态检查点不可执行" }
        val after = requireStatePayload(plan.journal, PlayerState::class.java)
        fileStorage.savePlayerState(characterId, after)
        fileStorage.saveStateMutationJournal(
            characterId,
            StateMutationPolicy.markCompleted(plan.journal)
        )
        return EntityMutationResult(after, applied = true)
    }

    private suspend fun applyCharacterStateMutationOnce(
        characterId: Long,
        round: Int,
        card: CharacterCard,
        produceUpdated: suspend (CharacterCard) -> CharacterCard
    ): EntityMutationResult<CharacterCard> {
        val targetKey = card.id.ifBlank { card.name }
        val operationKey = "character_state:$targetKey"
        val operationId = StateMutationPolicy.operationId(round, operationKey)
        val existingJournal = fileStorage.loadStateMutationJournal(characterId, operationId)
        val currentSnapshot = stateSnapshot(card)
        val decision = if (existingJournal == null) {
            val updated = produceUpdated(card)
            if (updated == card) return EntityMutationResult(card, applied = false)
            StateMutationPolicy.prepare(
                round = round,
                operationKey = operationKey,
                entityType = StateMutationJournal.ENTITY_CHARACTER,
                targetKey = targetKey,
                before = currentSnapshot,
                after = stateSnapshot(updated)
            )
        } else {
            StateMutationPolicy.resume(
                journal = existingJournal,
                expectedOperationId = operationId,
                expectedEntityType = StateMutationJournal.ENTITY_CHARACTER,
                expectedTargetKey = targetKey,
                current = currentSnapshot
            )
        }
        val plan = decision.plan
            ?: throw IllegalStateException("角色状态检查点冲突：${decision.failure ?: "UNKNOWN"}")
        if (existingJournal == null) {
            fileStorage.saveStateMutationJournal(characterId, plan.journal)
        }
        if (plan.alreadyCompleted) {
            if (plan.journal.status != StateMutationJournal.STATUS_COMPLETED) {
                fileStorage.saveStateMutationJournal(characterId, plan.journal)
            }
            return EntityMutationResult(card, applied = false)
        }
        check(plan.applyAfter) { "角色状态检查点不可执行" }
        val after = requireStatePayload(plan.journal, CharacterCard::class.java)
        fileStorage.saveCharacterCard(characterId, after)
        fileStorage.saveStateMutationJournal(
            characterId,
            StateMutationPolicy.markCompleted(plan.journal)
        )
        return EntityMutationResult(after, applied = true)
    }

    private suspend fun recoverPreparedStateMutations(
        characterId: Long,
        round: Int,
        playerState: PlayerState,
        emitEvent: suspend (GameEvent) -> Unit
    ): PlayerState {
        var recoveredPlayer = playerState
        fileStorage.loadStateMutationJournals(characterId, round)
            .filter { it.status == StateMutationJournal.STATUS_PREPARED }
            .forEach { journal ->
                val current = currentStateMutationSnapshot(
                    characterId = characterId,
                    journal = journal,
                    playerState = recoveredPlayer
                )
                val decision = StateMutationPolicy.resume(
                    journal = journal,
                    expectedOperationId = StateMutationPolicy.operationId(
                        journal.round,
                        journal.operationKey
                    ),
                    expectedEntityType = journal.entityType,
                    expectedTargetKey = journal.targetKey,
                    current = current
                )
                val plan = decision.plan
                    ?: throw IllegalStateException("状态恢复检查点冲突：${decision.failure ?: "UNKNOWN"}")
                if (plan.applyAfter) {
                    recoveredPlayer = applyRecoveredStateMutation(
                        characterId = characterId,
                        journal = plan.journal,
                        playerState = recoveredPlayer,
                        emitEvent = emitEvent
                    )
                }
                fileStorage.saveStateMutationJournal(
                    characterId,
                    StateMutationPolicy.markCompleted(plan.journal)
                )
            }
        return recoveredPlayer
    }

    private suspend fun currentStateMutationSnapshot(
        characterId: Long,
        journal: StateMutationJournal,
        playerState: PlayerState
    ): StateMutationPolicy.Snapshot {
        return when (journal.entityType) {
            StateMutationJournal.ENTITY_PLAYER -> stateSnapshot(playerState)
            StateMutationJournal.ENTITY_CHARACTER -> {
                val card = fileStorage.loadCharacterCard(characterId, journal.targetKey)
                    ?: resolveCharacterCard(characterId, journal.targetKey)
                stateSnapshot(card)
            }
            StateMutationJournal.ENTITY_WORLDBOOK -> {
                stateSnapshot(fileStorage.loadWorldbookEntry(characterId, journal.targetKey))
            }
            else -> StateMutationPolicy.Snapshot(exists = false)
        }
    }

    private suspend fun applyRecoveredStateMutation(
        characterId: Long,
        journal: StateMutationJournal,
        playerState: PlayerState,
        emitEvent: suspend (GameEvent) -> Unit
    ): PlayerState {
        return when (journal.entityType) {
            StateMutationJournal.ENTITY_PLAYER -> {
                check(journal.afterExists) { "主角恢复快照不能为空" }
                requireStatePayload(journal, PlayerState::class.java).also { recovered ->
                    fileStorage.savePlayerState(characterId, recovered)
                    emitEvent(GameEvent.PlayerUpdated(recovered))
                }
            }
            StateMutationJournal.ENTITY_CHARACTER -> {
                if (journal.afterExists) {
                    val recovered = requireStatePayload(journal, CharacterCard::class.java)
                    fileStorage.saveCharacterCard(characterId, recovered)
                    emitEvent(GameEvent.CharacterUpdated(recovered))
                } else {
                    fileStorage.deleteCharacterCard(characterId, journal.targetKey)
                    emitEvent(GameEvent.CharacterRetired(journal.targetKey))
                }
                playerState
            }
            StateMutationJournal.ENTITY_WORLDBOOK -> {
                if (journal.afterExists) {
                    val recovered = requireStatePayload(journal, WorldbookEntry::class.java)
                    fileStorage.saveWorldbookEntry(characterId, recovered)
                    emitEvent(GameEvent.WorldbookUpdated(recovered))
                } else {
                    fileStorage.deleteWorldbookEntry(characterId, journal.targetKey)
                    emitEvent(GameEvent.WorldbookDeleted(journal.targetKey))
                }
                playerState
            }
            else -> throw IllegalStateException("未知状态恢复实体")
        }
    }

    private fun stateSnapshot(value: Any?): StateMutationPolicy.Snapshot {
        return if (value == null) {
            StateMutationPolicy.Snapshot(exists = false)
        } else {
            StateMutationPolicy.Snapshot(exists = true, state = gson.toJsonTree(value))
        }
    }

    private fun <T> requireStatePayload(
        journal: StateMutationJournal,
        type: Class<T>
    ): T {
        check(journal.afterExists && journal.afterState != null && !journal.afterState.isJsonNull) {
            "状态恢复快照不完整"
        }
        return gson.fromJson(journal.afterState, type)
            ?: throw IllegalStateException("状态恢复快照无法解析")
    }

    private suspend fun requestCharacterStatePatch(prompt: String): CharacterStatePatch {
        return CharacterStateRequestPolicy.execute(
            onLengthRetry = { retryBudget ->
                Log.w(TAG, "Character state reached output limit; retrying with maxTokens=$retryBudget")
            }
        ) { maxTokens ->
            val response = aiService.chatComplete(
                listOf(ChatMessage(role = "user", content = prompt)),
                temperature = 0.2f,
                maxTokens = maxTokens,
                jsonMode = true,
                stage = AiCallStage.CHARACTER_STATE
            )
            jsonParser.parseCharacterStatePatch(response)
        }
    }

    private suspend fun requestTaskAssignment(
        taskPrompt: String,
        essentialTaskPrompt: String,
        isLocalEndpoint: Boolean,
        validate: (TaskAssignment) -> TaskAssignment,
        normalize: (TaskAssignment) -> TaskAssignment
    ): TaskAssignment {
        var prompt = if (isLocalEndpoint) essentialTaskPrompt else taskPrompt
        var mode = if (isLocalEndpoint) {
            TaskAssignmentPromptMode.ESSENTIAL
        } else {
            TaskAssignmentPromptMode.FULL
        }
        var lastError: Exception? = null
        var accumulated: TaskAssignment? = null

        repeat(3) { attempt ->
            var response: String? = null
            try {
                response = aiService.chatComplete(
                    listOf(ChatMessage(role = "user", content = prompt)),
                    temperature = if (attempt == 0) 0.1f else 0f,
                    maxTokens = when (mode) {
                        TaskAssignmentPromptMode.FULL -> 2400
                        TaskAssignmentPromptMode.REPAIR -> 2800
                        TaskAssignmentPromptMode.ESSENTIAL -> 1800
                    },
                    jsonMode = true,
                    stage = AiCallStage.TASK_ASSIGNMENT
                )
                val candidate = normalize(jsonParser.parseTaskAssignment(response))
                val merged = TaskAssignmentMergePolicy.merge(accumulated, candidate)
                accumulated = merged
                return TaskAssignmentQualityPolicy.requireMeaningful(validate(merged))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                Log.w(
                    TAG,
                    "Task assignment attempt ${attempt + 1} failed in $mode; " +
                        "responseLength=${response?.length ?: 0}; type=${e.logType()}; " +
                        "parseFailure=${(e as? TaskAssignmentParseException)?.failure ?: "NONE"}; " +
                        "stateIssues=${(e as? IncompleteStateSyncException)?.missingFields?.joinToString(",") ?: "NONE"}"
                )
                if (attempt == 2) return@repeat

                val canRepairResponse = !response.isNullOrBlank() &&
                    e is TaskAssignmentParseException &&
                    e.failure == TaskAssignmentParseFailure.FIELD_SHAPE &&
                    mode != TaskAssignmentPromptMode.REPAIR
                if (canRepairResponse) {
                    prompt = promptBuilder.buildTaskAssignmentRepairPrompt(response)
                    mode = TaskAssignmentPromptMode.REPAIR
                } else {
                    prompt = buildString {
                        append(essentialTaskPrompt)
                        if (e is IncompleteStateSyncException) {
                            appendLine()
                            appendLine(
                                "【必须修复的状态遗漏】正文已明确触发以下字段：" +
                                    e.missingFields.joinToString("、") +
                                    "。逐项核对正文结尾；角色字段在 player_update.changes 中写最终值，场景字段写入 story_update。即使最终值不变也必须显式写出。"
                            )
                        }
                        if (mode == TaskAssignmentPromptMode.ESSENTIAL) {
                            appendLine()
                            appendLine("【再次强调】上次必要同步仍不可执行；必须依据正文返回真实变化，或用具体理由声明 no_change。")
                        }
                    }
                    mode = TaskAssignmentPromptMode.ESSENTIAL
                }
            }
        }
        throw lastError ?: IllegalStateException("任务分配未完成")
    }

    private enum class TaskAssignmentPromptMode {
        FULL,
        REPAIR,
        ESSENTIAL
    }

    private suspend fun resolveCharacterCard(characterId: Long, requestedName: String): CharacterCard? {
        fileStorage.loadCharacterCardByName(characterId, requestedName)?.let { return it }
        return resolveCharacterReference(
            cards = fileStorage.loadAllCharacterCards(characterId),
            requestedName = requestedName
        )
    }

    private fun resolveCharacterReference(
        cards: List<CharacterCard>,
        requestedName: String
    ): CharacterCard? {
        return CharacterReferencePolicy.resolve(cards, requestedName)?.also { resolved ->
            if (resolved.name != requestedName.trim() && resolved.id != requestedName.trim()) {
                Log.d(TAG, "Resolved character alias to an existing card")
            }
        }
    }

    private suspend fun normalizeSceneCharacterNames(
        characterId: Long,
        names: List<String>,
        knownRenames: Map<String, String>
    ): List<String> {
        val cards = fileStorage.loadAllCharacterCards(characterId)
        return names.mapNotNull { rawName ->
            val requested = rawName.trim()
            if (requested.isBlank()) return@mapNotNull null
            knownRenames[requested]
                ?: resolveCharacterReference(cards, requested)?.name
                ?: requested
        }.distinct()
    }

    private fun applyPlayerUpdate(
        player: PlayerState,
        update: PlayerUpdate,
        characterFields: List<CharacterFieldDefinition>
    ): PlayerState {
        val changes = update.changes
        var updatedInventory = player.inventory
        changes.inventory.forEach { invChange ->
            updatedInventory = when (invChange.action.trim().lowercase()) {
                "add" -> addItems(updatedInventory, invChange.item, invChange.quantity)
                "remove" -> removeItems(updatedInventory, invChange.item, invChange.quantity)
                else -> updatedInventory
            }
        }
        val updatedRelationships = PlayerStatePatchPolicy.mergeRelationships(
            player.relationships,
            changes.relationships
        )
        val updated = player.copy(
            inventory = updatedInventory,
            relationships = updatedRelationships,
            questLog = changes.questLog ?: player.questLog,
            storyProgress = PlayerStatePatchPolicy.mergeStoryProgress(
                player.storyProgress,
                changes.storyProgress
            ),
            physicalState = changes.physicalState?.let {
                applyPhysicalPatch(player.physicalState, it, characterFields)
            }
                ?: player.physicalState,
            mentalState = changes.mentalState?.let {
                applyMentalPatch(player.mentalState, it, characterFields)
            }
                ?: player.mentalState,
            cv = changes.risk?.let {
                numberFieldValue(characterFields, "risk", it, player.cv)
            } ?: player.cv
        )
        return applyPlayerAttributes(updated, changes.attributes, characterFields)
    }

    private fun applyStoryUpdate(current: StoryState, update: StoryStateUpdate): StoryState {
        return current.copy(
            currentLocation = update.currentLocation?.takeIf { it.isNotBlank() } ?: current.currentLocation,
            timeOfDay = update.timeOfDay?.takeIf { it.isNotBlank() } ?: current.timeOfDay,
            weather = update.weather?.takeIf { it.isNotBlank() } ?: current.weather,
            mood = update.mood?.takeIf { it.isNotBlank() } ?: current.mood,
            sceneDescription = update.sceneDescription?.takeIf { it.isNotBlank() }
                ?: current.sceneDescription,
            activeCharacters = update.activeCharacters
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: current.activeCharacters,
            pendingEvents = update.pendingEvents
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: current.pendingEvents
        )
    }

    private fun applyCharacterStatePatch(
        card: CharacterCard,
        patch: CharacterStatePatch,
        playerName: String,
        currentTime: String,
        characterFields: List<CharacterFieldDefinition>
    ): CharacterCard {
        val boundedPatch = CharacterStatePatchContentPolicy.normalize(patch)
        var updatedInventory = card.inventory
        boundedPatch.inventoryChanges.forEach { change ->
            updatedInventory = when (change.action.trim().lowercase()) {
                "add" -> addItems(updatedInventory, change.item, change.quantity)
                "remove" -> removeItems(updatedInventory, change.item, change.quantity)
                else -> updatedInventory
            }
        }

        val currentRelationship = card.relationships[playerName]
            ?: Relationship(favor = card.favor, stage = "陌生人")
        val relationshipPatch = boundedPatch.relationshipToPlayer
        val updatedRelationship = if (relationshipPatch != null) {
            currentRelationship.copy(
                favor = (currentRelationship.favor + relationshipPatch.favorDelta).coerceIn(-100, 100),
                trust = (currentRelationship.trust + relationshipPatch.trustDelta).coerceIn(0, 100),
                stage = relationshipPatch.stage?.takeIf { it.isNotBlank() } ?: currentRelationship.stage
            )
        } else {
            currentRelationship
        }
        val updatedRelationships = card.relationships.toMutableMap().apply {
            put(playerName, updatedRelationship)
        }

        val updatedCard = card.copy(
                emotion = boundedPatch.emotion?.takeIf { it.isNotBlank() } ?: card.emotion,
                favor = updatedRelationship.favor,
                physicalState = boundedPatch.physicalState?.let {
                    applyPhysicalPatch(card.physicalState, it, characterFields)
                }
                    ?: card.physicalState,
                mentalState = boundedPatch.mentalState?.let {
                    applyMentalPatch(card.mentalState, it, characterFields)
                }
                    ?: card.mentalState,
                relationships = updatedRelationships,
                inventory = updatedInventory,
                lastModified = currentTime
            )
        val withAttributes = applyNpcAttributes(
            updatedCard,
            boundedPatch.attributes,
            characterFields,
            playerName
        )
        return CharacterProfilePolicy.reconcile(
            validateCharacterCard(withAttributes, characterFields),
            playerName
        )
    }

    private fun applyPhysicalPatch(
        current: PhysicalState,
        patch: PhysicalStatePatch,
        characterFields: List<CharacterFieldDefinition>
    ): PhysicalState {
        return current.copy(
            stamina = patch.stamina?.let {
                numberFieldValue(characterFields, "stamina", it, current.stamina)
            } ?: current.stamina,
            health = textFieldValue(characterFields, "health", patch.health, current.health),
            injuries = patch.injuries ?: current.injuries,
            fatigue = textFieldValue(characterFields, "fatigue", patch.fatigue, current.fatigue),
            hunger = textFieldValue(characterFields, "hunger", patch.hunger, current.hunger),
            pain = textFieldValue(characterFields, "pain", patch.pain, current.pain)
        )
    }

    private fun applyMentalPatch(
        current: MentalState,
        patch: MentalStatePatch,
        characterFields: List<CharacterFieldDefinition>
    ): MentalState {
        return current.copy(
            mood = textFieldValue(characterFields, "mood", patch.mood, current.mood),
            stress = textFieldValue(characterFields, "stress", patch.stress, current.stress),
            focus = textFieldValue(characterFields, "focus", patch.focus, current.focus),
            currentThoughts = textFieldValue(
                characterFields,
                "current_thoughts",
                patch.currentThoughts,
                current.currentThoughts
            )
        )
    }

    private fun validateCharacterCard(
        card: CharacterCard,
        characterFields: List<CharacterFieldDefinition>
    ): CharacterCard {
        val namedCard = CharacterNamingPolicy.normalizeNpcCard(card)
        return namedCard.copy(
            age = numberFieldValue(characterFields, "age", card.age, 0),
            physicalState = namedCard.physicalState.copy(
                stamina = numberFieldValue(
                    characterFields,
                    "stamina",
                    namedCard.physicalState.stamina,
                    100
                ),
                health = textFieldValue(
                    characterFields,
                    "health",
                    namedCard.physicalState.health,
                    "良好"
                ),
                fatigue = textFieldValue(
                    characterFields,
                    "fatigue",
                    namedCard.physicalState.fatigue,
                    "无"
                ),
                hunger = textFieldValue(
                    characterFields,
                    "hunger",
                    namedCard.physicalState.hunger,
                    "不饿"
                ),
                pain = textFieldValue(
                    characterFields,
                    "pain",
                    namedCard.physicalState.pain,
                    "无"
                )
            ),
            mentalState = namedCard.mentalState.copy(
                stress = textFieldValue(
                    characterFields,
                    "stress",
                    namedCard.mentalState.stress,
                    "低"
                )
            ),
            customAttributes = CharacterFieldPolicy.normalizeImportedAttributes(
                namedCard.customAttributes,
                characterFields,
                CharacterFieldDefinition.TARGET_NPC
            ),
            relationships = namedCard.relationships.mapValues { (_, rel) ->
                rel.copy(favor = rel.favor.coerceIn(-100, 100), trust = rel.trust.coerceIn(0, 100))
            },
            inventory = namedCard.inventory.map { it.copy(quantity = it.quantity.coerceAtLeast(1)) }
        )
    }

    private fun applyPlayerAttributes(
        player: PlayerState,
        attributes: Map<String, String>,
        characterFields: List<CharacterFieldDefinition>
    ): PlayerState {
        val values = mutableAttributeUpdates(
            attributes,
            characterFields,
            CharacterFieldDefinition.TARGET_PLAYER
        )
        if (values.isEmpty()) return player

        var updated = player
        values.forEach { (field, value) ->
            updated = when (field.key) {
                "age" -> updated.copy(age = value.toIntOrNull() ?: updated.age)
                "gender" -> updated.copy(gender = value)
                "race" -> updated.copy(race = value)
                "occupation" -> updated.copy(class_ = value)
                "appearance" -> updated.copy(appearance = value)
                "personality" -> updated.copy(personality = value)
                "background" -> updated.copy(background = value)
                "skills" -> {
                    val existing = updated.skills.associateBy { it.name }
                    val skills = value.split('、').map(String::trim).filter(String::isNotBlank)
                        .map { name -> existing[name] ?: PlayerSkill(name = name, level = 1) }
                    updated.copy(skills = skills)
                }
                "level" -> updated.copy(level = value.toIntOrNull() ?: updated.level)
                "charm" -> updated.copy(charm = value.toIntOrNull() ?: updated.charm)
                "action_skill" -> updated.copy(
                    huntingSkill = value.toIntOrNull() ?: updated.huntingSkill
                )
                "social_rank" -> updated.copy(socialRank = value)
                "luck" -> updated.copy(luck = value.toIntOrNull() ?: updated.luck)
                "willpower" -> updated.copy(alpha = value.toIntOrNull() ?: updated.alpha)
                "resilience" -> updated.copy(
                    resistance = value.toIntOrNull() ?: updated.resistance
                )
                "stamina" -> updated.copy(
                    physicalState = updated.physicalState.copy(
                        stamina = value.toIntOrNull() ?: updated.physicalState.stamina
                    )
                )
                "health" -> updated.copy(physicalState = updated.physicalState.copy(health = value))
                "fatigue" -> updated.copy(physicalState = updated.physicalState.copy(fatigue = value))
                "hunger" -> updated.copy(physicalState = updated.physicalState.copy(hunger = value))
                "pain" -> updated.copy(physicalState = updated.physicalState.copy(pain = value))
                "mood" -> updated.copy(mentalState = updated.mentalState.copy(mood = value))
                "stress" -> updated.copy(mentalState = updated.mentalState.copy(stress = value))
                "focus" -> updated.copy(mentalState = updated.mentalState.copy(focus = value))
                "current_thoughts" -> updated.copy(
                    mentalState = updated.mentalState.copy(currentThoughts = value)
                )
                "risk" -> updated.copy(cv = value.toIntOrNull() ?: updated.cv)
                "name" -> updated
                else -> updated
            }
        }
        return updated.copy(
            customAttributes = CharacterFieldPolicy.mergeCustomAttributes(
                current = updated.customAttributes,
                patch = attributes,
                configured = characterFields,
                target = CharacterFieldDefinition.TARGET_PLAYER
            )
        )
    }

    private fun applyNpcAttributes(
        card: CharacterCard,
        attributes: Map<String, String>,
        characterFields: List<CharacterFieldDefinition>,
        playerName: String,
        includeManual: Boolean = false
    ): CharacterCard {
        val values = mutableAttributeUpdates(
            attributes,
            characterFields,
            CharacterFieldDefinition.TARGET_NPC,
            includeManual
        )
        if (values.isEmpty()) return card

        var updated = card
        values.forEach { (field, value) ->
            updated = when (field.key) {
                "age" -> updated.copy(age = value.toIntOrNull() ?: updated.age)
                "gender" -> updated.copy(gender = value)
                "race" -> updated.copy(race = value)
                "occupation" -> updated.copy(occupation = value)
                "appearance" -> updated.copy(appearance = value)
                "personality" -> updated.copy(personality = value)
                "background" -> updated.copy(background = value)
                "rarity" -> updated.copy(rarity = value)
                "action_tendency" -> updated.copy(libido = value.toIntOrNull() ?: updated.libido)
                "defense_threshold" -> updated.copy(
                    threshold = value.toIntOrNull() ?: updated.threshold
                )
                "relationship_status" -> updated.copy(relationshipStatus = value)
                "hidden_clue" -> updated.copy(kink = value)
                "stamina" -> updated.copy(
                    physicalState = updated.physicalState.copy(
                        stamina = value.toIntOrNull() ?: updated.physicalState.stamina
                    )
                )
                "health" -> updated.copy(physicalState = updated.physicalState.copy(health = value))
                "fatigue" -> updated.copy(physicalState = updated.physicalState.copy(fatigue = value))
                "hunger" -> updated.copy(physicalState = updated.physicalState.copy(hunger = value))
                "pain" -> updated.copy(physicalState = updated.physicalState.copy(pain = value))
                "mood" -> updated.copy(
                    emotion = value,
                    mentalState = updated.mentalState.copy(mood = value)
                )
                "stress" -> updated.copy(mentalState = updated.mentalState.copy(stress = value))
                "focus" -> updated.copy(mentalState = updated.mentalState.copy(focus = value))
                "current_thoughts" -> updated.copy(
                    mentalState = updated.mentalState.copy(currentThoughts = value)
                )
                "relationship_stage", "favor", "trust" -> {
                    val current = updated.relationships[playerName]
                        ?: Relationship(favor = updated.favor, stage = updated.relationshipStatus)
                    val relationship = when (field.key) {
                        "relationship_stage" -> current.copy(stage = value)
                        "favor" -> current.copy(favor = value.toIntOrNull() ?: current.favor)
                        else -> current.copy(trust = value.toIntOrNull() ?: current.trust)
                    }
                    updated.copy(
                        favor = relationship.favor,
                        relationships = updated.relationships + (playerName to relationship)
                    )
                }
                "name" -> updated
                else -> updated
            }
        }
        return updated.copy(
            customAttributes = CharacterFieldPolicy.mergeCustomAttributes(
                current = updated.customAttributes,
                patch = attributes,
                configured = characterFields,
                target = CharacterFieldDefinition.TARGET_NPC
            )
        )
    }

    private fun mutableAttributeUpdates(
        attributes: Map<String, String>,
        characterFields: List<CharacterFieldDefinition>,
        target: String,
        includeManual: Boolean = false
    ): List<Pair<CharacterFieldDefinition, String>> {
        if (attributes.isEmpty()) return emptyList()
        val definitions = CharacterFieldPolicy.effectiveDefinitions(characterFields)
            .filter { field ->
                (includeManual || field.updateMode != CharacterFieldDefinition.UPDATE_MANUAL) &&
                    CharacterFieldPolicy.appliesTo(field, target)
            }
            .associateBy { it.key }
        return attributes.mapNotNull { (rawKey, rawValue) ->
            val field = definitions[rawKey.trim()] ?: return@mapNotNull null
            CharacterFieldPolicy.sanitizeValue(field, rawValue)?.let { field to it }
        }
    }

    private fun numberFieldValue(
        characterFields: List<CharacterFieldDefinition>,
        key: String,
        value: Int,
        fallback: Int
    ): Int {
        val field = CharacterFieldPolicy.definitionFor(characterFields, key) ?: return value
        return CharacterFieldPolicy.sanitizeValue(field, value.toString())?.toIntOrNull() ?: fallback
    }

    private fun textFieldValue(
        characterFields: List<CharacterFieldDefinition>,
        key: String,
        value: String?,
        fallback: String
    ): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        val field = CharacterFieldPolicy.definitionFor(characterFields, key) ?: return raw
        return CharacterFieldPolicy.sanitizeValue(field, raw) ?: fallback
    }

    private fun addItems(inventory: List<InventoryItem>, itemName: String, quantity: Int): List<InventoryItem> {
        val normalizedName = itemName.trim()
        if (normalizedName.isBlank()) return inventory
        val normalizedQuantity = quantity.coerceIn(1, 9999)
        val updated = inventory.toMutableList()
        val existing = updated.find { it.name == normalizedName }
        if (existing != null) {
            val idx = updated.indexOf(existing)
            updated[idx] = existing.copy(
                quantity = (existing.quantity.coerceAtLeast(0) + normalizedQuantity).coerceAtMost(9999)
            )
        } else {
            updated.add(InventoryItem(name = normalizedName, quantity = normalizedQuantity))
        }
        return updated
    }

    private fun removeItems(inventory: List<InventoryItem>, itemName: String, quantity: Int): List<InventoryItem> {
        val normalizedName = itemName.trim()
        if (normalizedName.isBlank()) return inventory
        val normalizedQuantity = quantity.coerceIn(1, 9999)
        val updated = inventory.toMutableList()
        val idx = updated.indexOfFirst { it.name == normalizedName }
        if (idx >= 0) {
            val item = updated[idx]
            if (item.quantity > normalizedQuantity) {
                updated[idx] = item.copy(quantity = item.quantity - normalizedQuantity)
            } else {
                updated.removeAt(idx)
            }
        }
        return updated
    }

}

private fun Throwable.logType(): String = javaClass.simpleName.ifBlank { "Exception" }

private fun String?.issueCount(): Int = this
    ?.split('；')
    ?.count { it.isNotBlank() }
    ?: 0
