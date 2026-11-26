package com.novelchat.data.remote.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class LlmSchedulerTest {

    @Test
    fun localEndpoint_serializesAndPrioritizesForegroundOverBackground() = runBlocking {
        val scheduler = LlmScheduler()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstBgStarted = CompletableDeferred<Unit>()
        val releaseFirstBg = CompletableDeferred<Unit>()

        coroutineScope {
            val bg1 = async {
                scheduler.run("http://localhost:1234", isLocal = true, LlmPriority.BACKGROUND) {
                    events += "bg1-start"
                    firstBgStarted.complete(Unit)
                    releaseFirstBg.await()
                    events += "bg1-end"
                }
            }
            firstBgStarted.await()

            val bg2 = async {
                scheduler.run("http://localhost:1234", isLocal = true, LlmPriority.BACKGROUND) {
                    events += "bg2"
                }
            }
            delay(30)
            val fg = async {
                scheduler.run("http://localhost:1234", isLocal = true, LlmPriority.FOREGROUND) {
                    events += "fg"
                }
            }
            delay(30)
            releaseFirstBg.complete(Unit)

            bg1.await()
            bg2.await()
            fg.await()
        }

        assertEquals("bg1-start", events[0])
        assertEquals("bg1-end", events[1])
        assertEquals("fg", events[2])
        assertEquals("bg2", events[3])
    }

    @Test
    fun remoteEndpoint_allowsConcurrentExecutionUpToLimit() = runBlocking {
        val scheduler = LlmScheduler(remoteMaxConcurrency = 4)
        val active = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)

        coroutineScope {
            (1..6).map {
                async {
                    scheduler.run("https://api.deepseek.com", isLocal = false, LlmPriority.BACKGROUND) {
                        val now = active.incrementAndGet()
                        maxSeen.updateAndGet { prev -> maxOf(prev, now) }
                        delay(40)
                        active.decrementAndGet()
                    }
                }
            }.forEach { it.await() }
        }

        assertTrue("Expected concurrency >= 2, saw ${maxSeen.get()}", maxSeen.get() in 2..4)
    }

    @Test
    fun localEndpoint_cancelledForegroundWaiterDoesNotStarveBackground() = runBlocking {
        val scheduler = LlmScheduler()
        val bgStarted = CompletableDeferred<Unit>()
        val releaseBg = CompletableDeferred<Unit>()

        coroutineScope {
            val blocker = async {
                scheduler.run("http://localhost:1234", isLocal = true, LlmPriority.BACKGROUND) {
                    bgStarted.complete(Unit)
                    releaseBg.await()
                }
            }
            bgStarted.await()

            val cancelledFg = async {
                scheduler.run("http://localhost:1234", isLocal = true, LlmPriority.FOREGROUND) {
                    "never"
                }
            }
            delay(20)
            cancelledFg.cancel()
            releaseBg.complete(Unit)
            blocker.await()

            val ran = scheduler.run("http://localhost:1234", isLocal = true, LlmPriority.BACKGROUND) {
                "ok"
            }
            assertEquals("ok", ran)
        }
    }
}
