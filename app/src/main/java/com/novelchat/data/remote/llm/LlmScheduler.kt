package com.novelchat.data.remote.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 按归一化端点控制 LLM 请求并发度：
 * - 远程端点允许最多 4 并发；
 * - 本地端点串行（Semaphore(1)），且前台请求严格优先于后台请求。
 */
class LlmScheduler(
    private val remoteMaxConcurrency: Int = DEFAULT_REMOTE_MAX_CONCURRENCY
) {

    private class EndpointGate(maxPermits: Int) {
        val semaphore = Semaphore(maxPermits)
        val foregroundCounter = AtomicInteger(0)
        val foregroundWaiters = MutableStateFlow(0)
    }

    private val remoteGates = ConcurrentHashMap<String, EndpointGate>()
    private val localGates = ConcurrentHashMap<String, EndpointGate>()

    suspend fun <T> run(
        endpointKey: String,
        isLocal: Boolean,
        priority: LlmPriority,
        block: suspend () -> T
    ): T {
        val key = endpointKey.trim().lowercase().ifBlank { "default" }
        if (!isLocal) {
            val gate = remoteGates.computeIfAbsent(key) {
                EndpointGate(remoteMaxConcurrency.coerceAtLeast(1))
            }
            return gate.semaphore.withPermit { block() }
        }

        val gate = localGates.computeIfAbsent(key) { EndpointGate(1) }
        if (priority == LlmPriority.FOREGROUND) {
            gate.foregroundWaiters.value = gate.foregroundCounter.incrementAndGet()
            try {
                return gate.semaphore.withPermit { block() }
            } finally {
                gate.foregroundWaiters.value = gate.foregroundCounter.decrementAndGet().coerceAtLeast(0)
            }
        }
        while (true) {
            gate.foregroundWaiters.first { it == 0 }
            gate.semaphore.acquire()
            if (gate.foregroundWaiters.value > 0) {
                gate.semaphore.release()
                yield()
                continue
            }
            return try {
                block()
            } finally {
                gate.semaphore.release()
            }
        }
    }

    companion object {
        const val DEFAULT_REMOTE_MAX_CONCURRENCY = 4
    }
}
