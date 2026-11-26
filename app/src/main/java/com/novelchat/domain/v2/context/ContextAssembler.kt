package com.novelchat.domain.v2.context

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.remote.llm.LlmMessage
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.engine.CharacterContextPolicy
import com.novelchat.domain.engine.NarrativeIdentityAnchorPolicy
import com.novelchat.domain.v2.memory.ChapterSummary
import com.novelchat.domain.v2.prompt.DefaultStyleGuide
import com.novelchat.domain.v2.prompt.NarratorPromptTemplate
import com.novelchat.domain.v2.round.LengthPolicy
import com.novelchat.domain.worldbook.WorldbookMatcher
import com.novelchat.data.model.CharacterReferencePolicy

object ContextAssembler {

    private val worldbookMatcher = WorldbookMatcher()

    fun assemble(
        snapshot: StorySnapshot,
        input: PlayerInput,
        budget: ContextBudget
    ): PromptContext {
        val trimmedItems = mutableListOf<String>()
        val playerName = snapshot.player.name.ifBlank { "主角" }
        val isOpening = snapshot.narratives.isEmpty()

        val effectiveLengthPref = snapshot.narration.resolvedLength(input.lengthPreference)
        val lengthHint = LengthPolicy.hint(input.raw, effectiveLengthPref, isOpening = isOpening)

        // 1. 静态世界与主角档案（放入 system）
        val worldMaxChars = (budget.systemChars * 0.55).toInt()
        val playerMaxChars = (budget.systemChars * 0.30).toInt()
        val worldProse = WorldRenderer.prose(snapshot.world, maxChars = worldMaxChars)
        val playerProfile = PlayerRenderer.profile(snapshot.player, snapshot.world, maxChars = playerMaxChars)

        // 2. 章节摘要（放入 system 末尾）
        val (chaptersText, lastChapterToTurn) = selectChapters(
            chapters = snapshot.chapters,
            budgetChars = budget.chapterChars,
            trimmedItems = trimmedItems
        )

        val systemPrompt = NarratorPromptTemplate.system(
            playerName = playerName,
            pov = snapshot.narration.resolvedPov(),
            styleGuide = snapshot.world.lore.narrativeStyle.ifBlank { DefaultStyleGuide.zh },
            styleSample = snapshot.narration.styleSample,
            worldProse = worldProse,
            playerProfile = playerProfile,
            chaptersText = chaptersText
        )

        // 3. 多轮历史（从最新回合倒序装入 historyChars）
        val historySelection = selectHistory(
            narratives = snapshot.narratives,
            budgetChars = budget.historyChars,
            latestFloorChars = budget.latestTurnFloorChars
        )

        // 4. 历史窗口之外、且未被章节覆盖的近期回合摘要
        val recentSummaries = snapshot.summaries
            .filter { summary ->
                summary.turn > lastChapterToTurn &&
                    summary.turn < historySelection.oldestIncludedTurn &&
                    summary.summary.isNotBlank()
            }
            .sortedBy { it.turn }
            .map { "第${it.turn}轮：${it.summary.trim()}" }

        // 5. 在场与相关世界书/人物
        val recentNarratives = snapshot.narratives.takeLast(8)
        val matchedWorldbook = worldbookMatcher.match(
            userMessage = input.raw,
            storyState = snapshot.story,
            allEntries = snapshot.worldbook,
            recentNarratives = recentNarratives,
            maxEntries = budget.maxWorldbookEntries
        )

        val allCards = snapshot.characters
        val presentCards = resolvePresentCharacters(
            allCards = allCards,
            activeNames = snapshot.story.activeCharacters,
            maxPresent = budget.maxPresentNpcs
        )
        val presentKeys = presentCards.map { it.id.ifBlank { it.name } }.toSet()

        val contextText = buildString {
            snapshot.story.pendingEvents.take(4).forEach(::appendLine)
            recentNarratives.takeLast(2).forEach {
                appendLine(it.userInput.take(200))
                appendLine(it.narrative.take(400))
            }
        }
        val candidateRelated = CharacterContextPolicy.selectForNarrative(
            allCharacters = allCards,
            sceneCharacterNames = snapshot.story.activeCharacters + matchedWorldbook.flatMap { it.relatedCharacters },
            userMessage = input.raw,
            contextText = contextText
        ).filter { card ->
            val key = card.id.ifBlank { card.name }
            key !in presentKeys
        }.take(budget.maxRelatedNpcs)

        val identityAnchors = NarrativeIdentityAnchorPolicy.resolve(
            userMessage = input.raw,
            knownCharacters = allCards,
            playerName = playerName
        )

        val currentGoal = snapshot.player.storyProgress.currentGoal.trim()
            .ifBlank { snapshot.story.pendingEvents.firstOrNull().orEmpty().trim() }
        val otherPendingAll = snapshot.story.pendingEvents
            .map(String::trim)
            .filter { it.isNotBlank() && it != currentGoal }
        val playerStatus = PlayerRenderer.status(snapshot.player, snapshot.world)
        val currentRound = snapshot.story.currentRound.coerceAtLeast(
            snapshot.narratives.maxOfOrNull { it.turn } ?: 0
        )

        // 6. 组装动态情境块并按预算逐级裁剪：世界书 -> 相关人物 -> 人物记忆 -> 待办
        var wbLimit = matchedWorldbook.size
        var relatedLimit = candidateRelated.size
        var memLimit = budget.maxMemoriesPerNpc
        var pendingLimit = minOf(otherPendingAll.size, budget.maxPendingEvents)

        fun renderContext(): String {
            val wbLines = matchedWorldbook.take(wbLimit).map { renderWorldbookLine(it) }
            val presentSheets = presentCards.map { card ->
                CharacterSheetRenderer.full(
                    card = card,
                    playerName = playerName,
                    characterFields = snapshot.world.characterFields,
                    currentRound = currentRound,
                    maxMemories = memLimit
                )
            }
            val relatedBriefs = candidateRelated.take(relatedLimit).map { card ->
                CharacterSheetRenderer.brief(card, playerName)
            }
            return NarratorPromptTemplate.turnContext(
                timeOfDay = snapshot.story.timeOfDay,
                location = snapshot.story.currentLocation,
                weather = snapshot.story.weather,
                sceneDescription = if (isOpening) snapshot.story.sceneDescription else "",
                playerName = playerName,
                presentNames = presentCards.map { it.name },
                playerStatus = playerStatus,
                currentGoal = currentGoal,
                otherPending = otherPendingAll.take(pendingLimit),
                presentSheets = presentSheets,
                relatedBriefs = relatedBriefs,
                worldbookLines = wbLines,
                recentSummaries = recentSummaries,
                identityAnchors = identityAnchors,
                lengthHint = lengthHint
            )
        }

        var turnContext = renderContext()
        if (turnContext.length > budget.contextChars && wbLimit > 4) {
            wbLimit = 4
            trimmedItems += "worldbook:->4"
            turnContext = renderContext()
        }
        if (turnContext.length > budget.contextChars && relatedLimit > 0) {
            relatedLimit = 0
            trimmedItems += "relatedNpcs:->0"
            turnContext = renderContext()
        }
        if (turnContext.length > budget.contextChars && memLimit > 1) {
            memLimit = 1
            trimmedItems += "npcMemories:->1"
            turnContext = renderContext()
        }
        if (turnContext.length > budget.contextChars && memLimit > 0) {
            memLimit = 0
            trimmedItems += "npcMemories:->0"
            turnContext = renderContext()
        }
        if (turnContext.length > budget.contextChars && pendingLimit > 0) {
            pendingLimit = 0
            trimmedItems += "pendingEvents:->0"
            turnContext = renderContext()
        }
        if (turnContext.length > budget.contextChars && wbLimit > 2) {
            wbLimit = 2
            trimmedItems += "worldbook:->2"
            turnContext = renderContext()
        }

        val userTurn = NarratorPromptTemplate.formatUserTurn(turnContext, input.raw)
        return PromptContext(
            systemPrompt = systemPrompt,
            history = historySelection.messages,
            turnContext = turnContext,
            userTurn = userTurn,
            lengthHint = lengthHint,
            debug = ContextDebugInfo(
                systemChars = systemPrompt.length,
                chapterChars = chaptersText.length,
                historyChars = historySelection.totalChars,
                historyTurnsIncluded = historySelection.turnsIncluded,
                contextChars = turnContext.length,
                presentNpcs = presentCards.map { it.name },
                relatedNpcs = candidateRelated.take(relatedLimit).map { it.name },
                worldbookNames = matchedWorldbook.take(wbLimit).map { it.name },
                trimmedItems = trimmedItems
            )
        )
    }

    private fun resolvePresentCharacters(
        allCards: List<CharacterCard>,
        activeNames: List<String>,
        maxPresent: Int
    ): List<CharacterCard> {
        val selected = linkedMapOf<String, CharacterCard>()
        activeNames.forEach { rawName ->
            val card = CharacterReferencePolicy.resolve(allCards, rawName) ?: return@forEach
            val key = card.id.ifBlank { card.name }
            if (key.isNotBlank()) selected.putIfAbsent(key, card)
        }
        return selected.values.take(maxPresent)
    }

    private fun renderWorldbookLine(entry: WorldbookEntry): String {
        val desc = entry.description.trim().take(200)
        return if (entry.isSecret || !entry.knownByPlayer) {
            "${entry.name}（作者备注，角色不会主动透露）：$desc"
        } else {
            "${entry.name}：$desc"
        }
    }

    private fun selectChapters(
        chapters: List<ChapterSummary>,
        budgetChars: Int,
        trimmedItems: MutableList<String>
    ): Pair<String, Int> {
        if (chapters.isEmpty() || budgetChars <= 0) return "" to 0
        val overview = chapters.firstOrNull { it.isOverview }
        val coveredByOverview = overview?.toTurn ?: 0
        val regular = chapters
            .filterNot { it.isOverview }
            .filter { it.toTurn > coveredByOverview }
            .sortedBy { it.index }
        val lastToTurn = chapters.maxOfOrNull { it.toTurn } ?: 0

        val chosen = mutableListOf<String>()
        var remaining = budgetChars
        overview?.text?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            val line = "早先梗概（至第${overview.toTurn}轮）：$text"
            if (line.length <= remaining) {
                chosen += line
                remaining -= line.length + 1
            } else {
                val clipped = line.take(remaining.coerceAtLeast(0))
                if (clipped.length >= 24) {
                    chosen += clipped
                    remaining = 0
                }
            }
        }

        val selectedRegular = mutableListOf<ChapterSummary>()
        for (chapter in regular.asReversed()) {
            val line = "第${chapter.fromTurn}-${chapter.toTurn}轮：${chapter.text.trim()}"
            if (line.length <= remaining) {
                selectedRegular.add(0, chapter)
                remaining -= line.length + 1
            } else {
                trimmedItems += "chapter:${chapter.index}"
            }
        }
        selectedRegular.forEach { chapter ->
            chosen += "第${chapter.fromTurn}-${chapter.toTurn}轮：${chapter.text.trim()}"
        }
        return chosen.joinToString("\n") to lastToTurn
    }

    private data class HistorySelection(
        val messages: List<LlmMessage>,
        val totalChars: Int,
        val turnsIncluded: Int,
        val oldestIncludedTurn: Int
    )

    private fun selectHistory(
        narratives: List<com.novelchat.data.model.NarrativeEntry>,
        budgetChars: Int,
        latestFloorChars: Int
    ): HistorySelection {
        if (narratives.isEmpty() || budgetChars <= 0) {
            return HistorySelection(emptyList(), 0, 0, Int.MAX_VALUE)
        }
        val sorted = narratives.sortedBy { it.turn }
        val selectedPairs = mutableListOf<Pair<Int, Pair<LlmMessage, LlmMessage>>>()
        var totalChars = 0

        for (index in sorted.indices.reversed()) {
            val entry = sorted[index]
            val userMsg = LlmMessage("user", NarratorPromptTemplate.formatHistoryUser(entry.userInput))
            val assistantText = entry.narrative.trim()
            val pairChars = userMsg.content.length + assistantText.length

            if (totalChars + pairChars <= budgetChars) {
                selectedPairs.add(0, entry.turn to (userMsg to LlmMessage("assistant", assistantText)))
                totalChars += pairChars
            } else if (selectedPairs.isEmpty()) {
                // 最近一轮必须保留；超出预算时截留尾部
                val allowedNarrative = minOf(
                    latestFloorChars,
                    (budgetChars - userMsg.content.length).coerceAtLeast(300)
                )
                val trimmedAssistant = if (assistantText.length > allowedNarrative) {
                    "…" + assistantText.takeLast(allowedNarrative)
                } else {
                    assistantText
                }
                selectedPairs.add(0, entry.turn to (userMsg to LlmMessage("assistant", trimmedAssistant)))
                totalChars += userMsg.content.length + trimmedAssistant.length
                break
            } else {
                break
            }
        }

        val messages = selectedPairs.flatMap { listOf(it.second.first, it.second.second) }
        val oldestTurn = selectedPairs.firstOrNull()?.first ?: Int.MAX_VALUE
        return HistorySelection(
            messages = messages,
            totalChars = totalChars,
            turnsIncluded = selectedPairs.size,
            oldestIncludedTurn = oldestTurn
        )
    }
}
