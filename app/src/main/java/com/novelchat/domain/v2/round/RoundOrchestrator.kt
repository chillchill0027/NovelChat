package com.novelchat.domain.v2.round

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.remote.llm.LlmMetrics
import com.novelchat.data.remote.llm.RoundPerfSummary
import com.novelchat.data.repo.StoryRepository
import com.novelchat.domain.v2.context.ContextAssembler
import com.novelchat.domain.v2.context.ContextBudget
import com.novelchat.domain.v2.context.PlayerInput
import com.novelchat.domain.v2.narrator.GuardReason
import com.novelchat.domain.v2.narrator.GuardVerdict
import com.novelchat.domain.v2.narrator.NarrativeGuard
import com.novelchat.domain.v2.narrator.NarrativeTextCleaner
import com.novelchat.domain.v2.narrator.NarratorAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RoundOrchestrator(
    private val repo: StoryRepository,
    private val narrator: NarratorAgent,
    private val metrics: LlmMetrics,
    private val clockNow: () -> String = {
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }
) {

    fun run(
        storyId: Long,
        turn: Int,
        input: PlayerInput,
        isLocalEndpoint: Boolean
    ): Flow<RoundEvent> = flow {
        val roundStartMs = System.currentTimeMillis()
        var llmCalls = 0
        emit(RoundEvent.Loading("正在构建剧情上下文..."))

        val snapshot = repo.snapshot(storyId)
        val budget = ContextBudget.forEndpoint(isLocalEndpoint)
        val promptContext = ContextAssembler.assemble(snapshot, input, budget)
        val previousNarrative = snapshot.narratives.lastOrNull()?.narrative.orEmpty()
        val currentGoal = snapshot.player.storyProgress.currentGoal
            .ifBlank { snapshot.story.pendingEvents.firstOrNull().orEmpty() }

        emit(RoundEvent.Loading("正在生成剧情..."))
        var attempt = 0
        var retryForRepetition = false
        var extraHint: String? = null
        var finalNarrative: String? = null

        while (attempt < 2) {
            llmCalls += 1
            val streamResult = try {
                narrator.collectStream(
                    narrator.stream(
                        promptContext = promptContext,
                        baseTemperature = input.creativity.temperature,
                        isLocalEndpoint = isLocalEndpoint,
                        retryForRepetition = retryForRepetition,
                        extraTrailingHint = extraHint
                    )
                ) { delta ->
                    emit(RoundEvent.NarrativeDelta(delta))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(RoundEvent.Failed(e.message ?: "剧情生成请求失败"))
                return@flow
            }

            val cleaned = NarrativeTextCleaner.clean(
                raw = streamResult.rawText,
                finishReason = streamResult.finishReason
            )
            when (
                val verdict = NarrativeGuard.evaluate(
                    cleanedText = cleaned,
                    previousNarrative = previousNarrative,
                    attempt = attempt,
                    playerName = snapshot.player.name,
                    rawInput = input.raw,
                    currentGoal = currentGoal
                )
            ) {
                is GuardVerdict.Pass -> {
                    finalNarrative = verdict.cleanedText
                    break
                }
                is GuardVerdict.Retry -> {
                    attempt += 1
                    retryForRepetition = verdict.reason == GuardReason.REPETITION
                    extraHint = if (retryForRepetition) {
                        "上一段已经写过的内容不要重复，从它结束的地方接着写。"
                    } else {
                        null
                    }
                    emit(RoundEvent.NarrativeReset)
                    emit(RoundEvent.Loading("重新生成中..."))
                }
                is GuardVerdict.Fail -> {
                    val reasonText = when (verdict.reason) {
                        GuardReason.EMPTY -> "AI 未返回可用剧情正文，请重试。"
                        GuardReason.TOO_SHORT -> "AI 返回正文过短，请重试。"
                        GuardReason.REPETITION -> "AI 重复了上一轮内容，请重试。"
                        GuardReason.META_LEAK -> "AI 输出包含无效元信息，请重试。"
                    }
                    emit(RoundEvent.Failed(reasonText))
                    return@flow
                }
            }
        }

        val textToCommit = finalNarrative
        if (textToCommit.isNullOrBlank()) {
            emit(RoundEvent.Failed("AI 未返回可用剧情正文，请重试。"))
            return@flow
        }

        emit(RoundEvent.Loading("正在保存本轮剧情..."))
        val entry = NarrativeEntry(
            turn = turn,
            narrative = textToCommit,
            userInput = input.raw.trim(),
            createdAt = clockNow()
        )
        repo.commitNarrative(storyId, entry)

        val blockingMs = System.currentTimeMillis() - roundStartMs
        metrics.recordRoundSummary(
            RoundPerfSummary(
                timestampMs = System.currentTimeMillis(),
                round = turn,
                blockingMs = blockingMs,
                calls = llmCalls,
                syncMs = null
            )
        )
        emit(
            RoundEvent.NarrativeCommitted(
                turn = turn,
                narrative = textToCommit,
                debugInfo = promptContext.debug
            )
        )
    }
}
