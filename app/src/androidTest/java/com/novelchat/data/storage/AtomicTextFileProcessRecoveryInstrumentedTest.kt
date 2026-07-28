package com.novelchat.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldSetting
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AtomicTextFileProcessRecoveryInstrumentedTest {
    @Test
    fun processBoundary_1_prepareCrashPointFixtures() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val gson = Gson()

        crashIds.forEach { storage.deleteConversation(it) }

        storage.initialize(BACKUP_WINS_ID)
        storage.saveStoryState(
            BACKUP_WINS_ID,
            StoryState(currentRound = 4, currentLocation = "写入前地点")
        )
        val backupWinsState = stateFile(context, BACKUP_WINS_ID)
        assertTrue(backupWinsState.renameTo(File(backupWinsState.path + ".bak")))
        writeSynced(
            File(backupWinsState.path + ".tmp"),
            gson.toJson(StoryState(currentRound = 5, currentLocation = "未提交地点"))
        )

        storage.initialize(TARGET_WINS_ID)
        storage.saveStoryState(
            TARGET_WINS_ID,
            StoryState(currentRound = 6, currentLocation = "旧地点")
        )
        val targetWinsState = stateFile(context, TARGET_WINS_ID)
        writeSynced(
            File(targetWinsState.path + ".bak"),
            gson.toJson(StoryState(currentRound = 6, currentLocation = "旧地点"))
        )
        writeSynced(
            targetWinsState,
            gson.toJson(StoryState(currentRound = 7, currentLocation = "已提交地点"))
        )

        storage.initialize(TEMP_PROMOTES_ID)
        val world = worldFile(context, TEMP_PROMOTES_ID)
        assertFalse(world.exists())
        writeSynced(
            File(world.path + ".tmp"),
            gson.toJson(WorldSetting(name = "临时文件恢复世界", description = "跨进程恢复"))
        )
    }

    @Test
    fun processBoundary_2_verifyRecoveryAfterRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)

        try {
            val backupWins = storage.loadStoryState(BACKUP_WINS_ID)
            assertEquals(4, backupWins.currentRound)
            assertEquals("写入前地点", backupWins.currentLocation)
            assertTransactionFilesCleared(stateFile(context, BACKUP_WINS_ID))

            val targetWins = storage.loadStoryState(TARGET_WINS_ID)
            assertEquals(7, targetWins.currentRound)
            assertEquals("已提交地点", targetWins.currentLocation)
            assertTransactionFilesCleared(stateFile(context, TARGET_WINS_ID))

            val promotedWorld = storage.loadWorldSetting(TEMP_PROMOTES_ID)
            assertEquals("临时文件恢复世界", promotedWorld.name)
            assertEquals("跨进程恢复", promotedWorld.description)
            assertTransactionFilesCleared(worldFile(context, TEMP_PROMOTES_ID))
        } finally {
            crashIds.forEach { storage.deleteConversation(it) }
        }
    }

    private fun assertTransactionFilesCleared(target: File) {
        assertTrue(target.isFile)
        assertFalse(File(target.path + ".bak").exists())
        assertFalse(File(target.path + ".tmp").exists())
    }

    private fun stateFile(context: Context, storyId: Long): File {
        return File(context.filesDir, "rpg/$storyId/states.json")
    }

    private fun worldFile(context: Context, storyId: Long): File {
        return File(context.filesDir, "rpg/$storyId/world_setting.json")
    }

    private fun writeSynced(file: File, content: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    private companion object {
        const val BACKUP_WINS_ID = 9_000_000_101L
        const val TARGET_WINS_ID = 9_000_000_102L
        const val TEMP_PROMOTES_ID = 9_000_000_103L
        val crashIds = listOf(BACKUP_WINS_ID, TARGET_WINS_ID, TEMP_PROMOTES_ID)
    }
}
