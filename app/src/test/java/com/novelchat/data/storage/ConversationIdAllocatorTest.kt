package com.novelchat.data.storage

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationIdAllocatorTest {

    @Test
    fun reserve_createsSeedDirectory() = withTempDirectory { root ->
        val id = ConversationIdAllocator.reserve(root, 123L)

        assertEquals(123L, id)
        assertTrue(File(root, "123").isDirectory)
    }

    @Test
    fun reserve_skipsExistingIds() = withTempDirectory { root ->
        File(root, "123").mkdirs()
        File(root, "124").mkdirs()

        val id = ConversationIdAllocator.reserve(root, 123L)

        assertEquals(125L, id)
        assertTrue(File(root, "125").isDirectory)
    }

    @Test
    fun reserve_isCollisionSafeAcrossConcurrentCallers() = withTempDirectory { root ->
        val pool = Executors.newFixedThreadPool(8)
        try {
            val ids = pool.invokeAll(
                List(20) { Callable { ConversationIdAllocator.reserve(root, 500L) } }
            ).map { future -> future.get() }

            assertEquals(20, ids.distinct().size)
            assertEquals((500L..519L).toList(), ids.sorted())
        } finally {
            pool.shutdownNow()
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("novelchat-conversation-id").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
