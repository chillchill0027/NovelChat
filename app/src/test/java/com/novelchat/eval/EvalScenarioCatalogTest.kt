package com.novelchat.eval

import com.google.gson.Gson
import com.novelchat.ui.viewmodel.AdvancedStoryImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EvalScenarioCatalogTest {

    private val gson = Gson()

    private fun evalDir(): File {
        return listOf(
            File("src/androidTest/assets/eval"),
            File("app/src/androidTest/assets/eval")
        ).first { it.isDirectory }
    }

    @Test
    fun allEvalScenariosAndReferencedStoriesAreValid() {
        val dir = evalDir()
        val files = dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assertEquals(
            listOf(
                "item_transfer_10.json",
                "long_horizon_30.json",
                "magic_academy_10.json",
                "modern_life_10.json",
                "multi_npc_social_10.json"
            ),
            files.map { it.name }
        )

        files.forEach { file ->
            val scenario = gson.fromJson(file.readText(), ScenarioDto::class.java)
            assertTrue("${file.name} id 不能为空", scenario.id.isNotBlank())
            assertTrue("${file.name} title 不能为空", scenario.title.isNotBlank())
            assertTrue("${file.name} inputs 不能为空", scenario.inputs.isNotEmpty())
            assertTrue("${file.name} 至少有一个检查点", scenario.checkpoints.isNotEmpty())
            scenario.checkpoints.forEach { cp ->
                assertTrue(
                    "${file.name} checkpoint after_round=${cp.after_round} 越界",
                    cp.after_round in 1..scenario.inputs.size
                )
            }
            if (scenario.story_source.startsWith("asset:")) {
                val relative = scenario.story_source.removePrefix("asset:eval/")
                val storyFile = File(dir, relative)
                assertTrue("缺少引用故事文件 ${storyFile.path}", storyFile.isFile)
                val parsed = AdvancedStoryImportPolicy.parse(storyFile.readText())
                assertTrue(parsed.worldSetting.name.isNotBlank())
            } else {
                assertEquals("default_modern", scenario.story_source)
            }
        }
    }

    private data class ScenarioDto(
        val id: String = "",
        val title: String = "",
        val story_source: String = "",
        val inputs: List<String> = emptyList(),
        val checkpoints: List<CheckpointDto> = emptyList()
    )

    private data class CheckpointDto(
        val after_round: Int = 0
    )
}
