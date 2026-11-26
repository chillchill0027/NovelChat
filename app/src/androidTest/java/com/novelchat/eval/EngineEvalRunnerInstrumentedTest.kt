package com.novelchat.eval

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.PlayerSkill
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.model.defaultModernCharacters
import com.novelchat.data.model.defaultModernStoryState
import com.novelchat.data.model.defaultModernWorldSetting
import com.novelchat.data.model.defaultModernWorldbookEntries
import com.novelchat.data.remote.AiService
import com.novelchat.data.remote.llm.LlmMetrics
import com.novelchat.data.remote.llm.LlmScheduler
import com.novelchat.data.remote.llm.OpenAiCompatibleLlmClient
import com.novelchat.data.repo.StoryRepository
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.data.storage.SettingsRepository
import com.novelchat.domain.v2.context.PlayerInput
import com.novelchat.domain.v2.round.RoundEvent
import com.novelchat.session.StorySessionManager
import com.novelchat.ui.viewmodel.AdvancedStoryImportPolicy
import com.novelchat.ui.viewmodel.ImportedCharacterPolicy
import com.novelchat.ui.viewmodel.ImportedOpeningPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 端到端多轮基准评测运行器（T0.1 / T4.1）。
 * 默认跳过；仅当显式传入 -e novelchat.runEval true 时执行真实 API 评测，
 * 并在独立临时 storyId 目录中运行与清理，绝不触碰用户既有存档。
 */
@RunWith(AndroidJUnit4::class)
class EngineEvalRunnerInstrumentedTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun runEvalSuite(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val runEval = args.getString("novelchat.runEval").equals("true", ignoreCase = true)
        assumeTrue("仅在显式传递 novelchat.runEval=true 时运行真实 API 评测", runEval)

        val baseUrl = args.getString("novelchat.evalBaseUrl").orEmpty().trim()
            .ifBlank { SettingsRepository.DEFAULT_API_BASE_URL }
        val apiKey = args.getString("novelchat.evalApiKey").orEmpty().trim()
        val model = args.getString("novelchat.evalModel").orEmpty().trim()
            .ifBlank { SettingsRepository.DEFAULT_MODEL }
        val engineMode = args.getString("novelchat.evalEngine").orEmpty().trim()
            .lowercase()
            .ifBlank { "v2" }
        val scenarioFilter = args.getString("novelchat.evalScenario").orEmpty().trim()

        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val storage = FileStorageManager(targetContext)
        val settings = SettingsRepository(targetContext)
        settings.saveApiConnection(
            url = baseUrl,
            key = apiKey,
            model = model,
            currentProfileName = "Eval-$engineMode"
        )

        val scenarioFiles = testContext.assets.list("eval")
            ?.filter { it.endsWith(".json") }
            ?.sorted()
            .orEmpty()
        assertTrue("未找到 eval/*.json 评测场景", scenarioFiles.isNotEmpty())

        val scenarios = scenarioFiles.map { fileName ->
            val raw = testContext.assets.open("eval/$fileName").bufferedReader().use { it.readText() }
            gson.fromJson(raw, EvalScenario::class.java)
        }.filter { scenarioFilter.isBlank() || it.id == scenarioFilter }
        assertTrue("没有匹配的评测场景：$scenarioFilter", scenarios.isNotEmpty())

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outputRoot = File(targetContext.getExternalFilesDir(null) ?: targetContext.filesDir, "eval/$timestamp")
        outputRoot.mkdirs()

        val reports = mutableListOf<EvalScenarioReport>()
        val transcriptBuilder = StringBuilder()
        transcriptBuilder.appendLine("# NovelChat Engine Eval Transcript ($engineMode)")
        transcriptBuilder.appendLine("- Model: `$model`")
        transcriptBuilder.appendLine("- Endpoint: `$baseUrl`")
        transcriptBuilder.appendLine()

        for (scenario in scenarios) {
            val report = executeScenario(
                scenario = scenario,
                engineMode = engineMode,
                model = model,
                targetContext = targetContext,
                testContext = testContext,
                storage = storage,
                transcriptOut = transcriptBuilder
            )
            reports += report
        }

        val summary = EvalSummaryReport(
            generatedAt = timestamp,
            engineMode = engineMode,
            model = model,
            baseUrl = baseUrl,
            scenarios = reports
        )
        File(outputRoot, "report.json").writeText(gson.toJson(summary))
        File(outputRoot, "transcript_$engineMode.md").writeText(transcriptBuilder.toString())
        Log.i(TAG, "Eval finished. Report saved under: ${outputRoot.absolutePath}")
    }

    private suspend fun executeScenario(
        scenario: EvalScenario,
        engineMode: String,
        model: String,
        targetContext: Context,
        testContext: Context,
        storage: FileStorageManager,
        transcriptOut: StringBuilder
    ): EvalScenarioReport {
        val storyId = seedScenarioStory(scenario, testContext, storage)
        val roundMetrics = mutableListOf<EvalRoundMetrics>()
        val checkpointResults = mutableListOf<EvalCheckpointResult>()
        val checkpointByRound = scenario.checkpoints.associateBy { it.afterRound }
        val aiService = AiService(targetContext)
        val v2Scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val metrics = LlmMetrics()
        val scheduler = LlmScheduler()
        val llmClient = OpenAiCompatibleLlmClient(aiService, scheduler, metrics)
        val repo = StoryRepository(storage)
        val v2Manager = StorySessionManager(v2Scope, aiService, llmClient, repo, metrics)
        val v2Session = v2Manager.session(storyId).also { it.open() }

        transcriptOut.appendLine("## ${scenario.id} — ${scenario.title}")
        transcriptOut.appendLine()

        try {
            scenario.inputs.forEachIndexed { index, input ->
                val turn = index + 1
                val startMs = System.currentTimeMillis()
                var ttftMs: Long? = null
                var narrativeMs: Long? = null
                var roundError: String? = null
                var sawRoundComplete = false
                val streamedText = StringBuilder()

                withTimeout(300_000L) {
                    val done = CompletableDeferred<Unit>()
                    val collector = launch {
                        v2Session.roundEvents.collect { event ->
                            val elapsed = System.currentTimeMillis() - startMs
                            when (event) {
                                is RoundEvent.NarrativeDelta -> {
                                    if (ttftMs == null) ttftMs = elapsed
                                    streamedText.append(event.text)
                                }
                                is RoundEvent.NarrativeReset -> streamedText.clear()
                                is RoundEvent.NarrativeCommitted -> {
                                    if (event.turn == turn) {
                                        narrativeMs = elapsed
                                        sawRoundComplete = true
                                        done.complete(Unit)
                                    }
                                }
                                is RoundEvent.Failed -> {
                                    roundError = event.message
                                    done.complete(Unit)
                                }
                                else -> Unit
                            }
                        }
                    }
                    try {
                        v2Session.startRound(PlayerInput(input))
                        done.await()
                    } finally {
                        collector.cancel()
                    }
                }
                if (checkpointByRound.containsKey(turn)) {
                    v2Session.awaitSyncIdle(90_000L)
                }

                val blockingMs = System.currentTimeMillis() - startMs
                val storyAfter = storage.loadStoryState(storyId)
                val playerAfter = storage.loadPlayerState(storyId)
                val latestNarrative = storage.loadAllNarratives(storyId).lastOrNull { it.turn == turn }?.narrative.orEmpty()
                val syncSucceeded = sawRoundComplete && turn !in v2Session.state.value.sync.failedRounds

                roundMetrics += EvalRoundMetrics(
                    turn = turn,
                    input = input,
                    ttftMs = ttftMs,
                    narrativeCompleteMs = narrativeMs,
                    blockingTotalMs = blockingMs,
                    narrativeChars = latestNarrative.count { !it.isWhitespace() },
                    syncSucceeded = syncSucceeded,
                    blockedByPendingSync = false,
                    error = roundError,
                    warning = null,
                    locationAfter = storyAfter.currentLocation,
                    presentAfter = storyAfter.activeCharacters
                )

                transcriptOut.appendLine("### 第 $turn 轮")
                transcriptOut.appendLine("**输入**：$input")
                transcriptOut.appendLine()
                if (latestNarrative.isNotBlank()) {
                    transcriptOut.appendLine(latestNarrative)
                } else {
                    transcriptOut.appendLine("> [生成失败] $roundError")
                }
                transcriptOut.appendLine()
                transcriptOut.appendLine(
                    "_metrics: ttft=${ttftMs ?: -1}ms, total=${blockingMs}ms, sync=$syncSucceeded, " +
                        "loc=${storyAfter.currentLocation}, present=${storyAfter.activeCharacters}_"
                )
                transcriptOut.appendLine()

                checkpointByRound[turn]?.let { cp ->
                    val failures = evaluateCheckpoint(cp, storyAfter, playerAfter)
                    checkpointResults += EvalCheckpointResult(
                        afterRound = turn,
                        passed = failures.isEmpty(),
                        failures = failures
                    )
                }
            }
            v2Session.awaitSyncIdle(30_000L)
        } finally {
            v2Manager.closeSession(storyId)
            v2Scope.cancel()
            storage.deleteConversation(storyId)
        }

        val ttftValues = roundMetrics.mapNotNull { it.ttftMs }.sorted()
        val blockingValues = roundMetrics.map { it.blockingTotalMs }.sorted()
        return EvalScenarioReport(
            scenarioId = scenario.id,
            title = scenario.title,
            engineMode = engineMode,
            model = model,
            roundsAttempted = roundMetrics.size,
            roundsSucceeded = roundMetrics.count { it.error == null && it.narrativeChars > 0 },
            syncSuccessCount = roundMetrics.count { it.syncSucceeded },
            pendingSyncBlockedCount = roundMetrics.count { it.blockedByPendingSync },
            ttftP50Ms = ttftValues.getOrNull(ttftValues.size / 2),
            blockingP50Ms = blockingValues.getOrNull(blockingValues.size / 2),
            checkpointPassCount = checkpointResults.count { it.passed },
            checkpointTotalCount = checkpointResults.size,
            checkpointResults = checkpointResults,
            rounds = roundMetrics
        )
    }

    private suspend fun seedScenarioStory(
        scenario: EvalScenario,
        testContext: Context,
        storage: FileStorageManager
    ): Long {
        val storyId = storage.createConversationStorage()
        val createdAt = storage.getCurrentTimeString()
        try {
            if (scenario.storySource == "default_modern") {
                storage.saveWorldSetting(storyId, defaultModernWorldSetting)
                storage.savePlayerState(
                    storyId,
                    PlayerState(
                        name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME,
                        class_ = "城市租客",
                        background = "在海临市云桥里老小区租房的普通打工人。"
                    )
                )
                defaultModernCharacters(createdAt).forEach { storage.saveCharacterCard(storyId, it) }
                defaultModernWorldbookEntries(createdAt).forEach { storage.saveWorldbookEntry(storyId, it) }
                storage.saveStoryState(storyId, defaultModernStoryState())
            } else if (scenario.storySource.startsWith("asset:")) {
                val assetPath = scenario.storySource.removePrefix("asset:")
                val raw = testContext.assets.open(assetPath).bufferedReader().use { it.readText() }
                val parsed = AdvancedStoryImportPolicy.parse(raw)
                val ws = parsed.worldSetting
                storage.saveWorldSetting(
                    storyId,
                    WorldSetting(
                        name = ws.name,
                        genre = ws.genre,
                        techLevel = ws.techLevel,
                        magicLevel = ws.magicLevel,
                        tone = ws.tone,
                        forbidden = ws.forbidden,
                        description = ws.description,
                        lore = ws.lore,
                        characterFields = ws.characterFields
                    )
                )
                val player = parsed.player
                val playerName = player.name.ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME }
                storage.savePlayerState(
                    storyId,
                    PlayerState(
                        name = playerName,
                        age = player.age,
                        gender = player.gender,
                        race = player.race,
                        class_ = player.class_,
                        personality = player.personality,
                        background = player.background,
                        appearance = player.appearance,
                        skills = player.skills.map { PlayerSkill(name = it, level = 1) },
                        level = player.level,
                        charm = player.charm,
                        huntingSkill = player.actionSkill,
                        socialRank = player.socialRank,
                        luck = player.luck,
                        alpha = player.willpower,
                        resistance = player.resilience,
                        lore = player.lore,
                        physicalState = player.physicalState,
                        mentalState = player.mentalState,
                        customAttributes = player.customAttributes,
                        inventory = player.inventory
                    )
                )
                parsed.npcs.forEach { npc ->
                    storage.saveCharacterCard(
                        storyId,
                        ImportedCharacterPolicy.createCard(
                            npc = npc,
                            playerName = playerName,
                            createdAt = createdAt,
                            characterFields = ws.characterFields
                        )
                    )
                }
                parsed.worldbook.forEachIndexed { idx, item ->
                    storage.saveWorldbookEntry(
                        storyId,
                        WorldbookEntry(
                            id = "wb_${idx + 1}",
                            type = item.type,
                            name = item.name,
                            description = item.description,
                            keywords = (item.keywords + item.name).distinct(),
                            relatedCharacters = item.relatedCharacters,
                            relatedEvents = item.relatedEvents,
                            relatedLocations = item.relatedLocations,
                            isSecret = item.isSecret,
                            knownByPlayer = item.knownByPlayer,
                            isActive = true,
                            createdBy = "user",
                            status = item.status,
                            createdAt = createdAt,
                            lastModified = createdAt
                        )
                    )
                }
                storage.saveStoryState(storyId, ImportedOpeningPolicy.createStoryState(parsed.opening))
            } else {
                error("不支持的 story_source: ${scenario.storySource}")
            }
            storage.completeConversationStorage(storyId)
            return storyId
        } catch (e: Exception) {
            storage.deleteConversation(storyId)
            throw e
        }
    }

    private fun evaluateCheckpoint(
        checkpoint: EvalCheckpoint,
        story: com.novelchat.data.model.StoryState,
        player: PlayerState?
    ): List<String> {
        val failures = mutableListOf<String>()
        if (checkpoint.expectedLocationKeywords.isNotEmpty()) {
            val matched = checkpoint.expectedLocationKeywords.any {
                story.currentLocation.contains(it, ignoreCase = true)
            }
            if (!matched) {
                failures += "R${checkpoint.afterRound} 地点(${story.currentLocation})未命中 ${checkpoint.expectedLocationKeywords}"
            }
        }
        if (checkpoint.expectedPresentAny.isNotEmpty()) {
            val matched = checkpoint.expectedPresentAny.any { expected ->
                story.activeCharacters.any { it.contains(expected, ignoreCase = true) }
            }
            if (!matched) {
                failures += "R${checkpoint.afterRound} 在场(${story.activeCharacters})未包含 ${checkpoint.expectedPresentAny}"
            }
        }
        val itemNames = player?.inventory.orEmpty().map { it.name }
        checkpoint.expectedInventoryContains.forEach { expected ->
            if (itemNames.none { it.contains(expected, ignoreCase = true) }) {
                failures += "R${checkpoint.afterRound} 背包($itemNames)缺少 $expected"
            }
        }
        checkpoint.expectedInventoryExcludes.forEach { excluded ->
            if (itemNames.any { it.contains(excluded, ignoreCase = true) }) {
                failures += "R${checkpoint.afterRound} 背包($itemNames)不应保留 $excluded"
            }
        }
        return failures
    }

    companion object {
        private const val TAG = "NovelChatEval"
    }
}
