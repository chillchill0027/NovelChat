package com.novelchat.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicTextFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `write creates and replaces a file without leftovers`() {
        val file = File(temporaryFolder.root, "nested/state.json")

        AtomicTextFile.write(file, "first")
        AtomicTextFile.write(file, "second")

        assertEquals("second", AtomicTextFile.read(file))
        assertFalse(File(file.parentFile, "state.json.tmp").exists())
        assertFalse(File(file.parentFile, "state.json.bak").exists())
    }

    @Test
    fun `read restores backup left by an interrupted replacement`() {
        val file = File(temporaryFolder.root, "state.json")
        val backup = File(temporaryFolder.root, "state.json.bak")
        backup.writeText("last-good")

        assertEquals("last-good", AtomicTextFile.readOrNull(file))
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun `read restores temporary file left by interrupted first write`() {
        val file = File(temporaryFolder.root, "new-state.json")
        val temporary = File(temporaryFolder.root, "new-state.json.tmp")
        temporary.writeText("first-complete-version")

        assertEquals("first-complete-version", AtomicTextFile.readOrNull(file))
        assertTrue(file.exists())
        assertFalse(temporary.exists())
    }

    @Test
    fun `read keeps current file and clears stale transaction files`() {
        val file = File(temporaryFolder.root, "state.json")
        file.writeText("current")
        File(temporaryFolder.root, "state.json.bak").writeText("old")
        File(temporaryFolder.root, "state.json.tmp").writeText("partial")

        assertEquals("current", AtomicTextFile.readOrNull(file))
        assertFalse(File(temporaryFolder.root, "state.json.bak").exists())
        assertFalse(File(temporaryFolder.root, "state.json.tmp").exists())
    }

    @Test
    fun `readOrNull returns null for a missing file`() {
        assertNull(AtomicTextFile.readOrNull(File(temporaryFolder.root, "missing.json")))
    }

    @Test
    fun `directory recovery restores a json file before enumeration`() {
        File(temporaryFolder.root, "turn_007.json.bak").writeText("saved turn")

        AtomicTextFile.recoverDirectory(temporaryFolder.root)

        val restored = temporaryFolder.root.listFiles { file -> file.extension == "json" }.orEmpty()
        assertEquals(listOf("turn_007.json"), restored.map { it.name })
        assertEquals("saved turn", restored.single().readText())
    }
}
