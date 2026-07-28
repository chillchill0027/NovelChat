package com.novelchat.data.storage

import com.google.gson.Gson
import com.novelchat.data.model.StoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class StoredJsonCodecTest {
    private val codec = StoredJsonCodec(Gson())
    private val file = File("states.json")

    @Test
    fun decode_returnsValidStoredValue() {
        val state = codec.decode(
            json = """{"current_round":4,"current_location":"咖啡厅"}""",
            file = file,
            type = StoryState::class.java,
            label = "故事状态"
        )

        assertEquals(4, state.currentRound)
        assertEquals("咖啡厅", state.currentLocation)
    }

    @Test
    fun decode_rejectsMalformedJsonWithoutIncludingContents() {
        val secretLikeContent = "{\"current_round\":[\"" + "sk-" + "sensitive-value"

        val error = assertThrows(CorruptStoredDataException::class.java) {
            codec.decode(secretLikeContent, file, StoryState::class.java, "故事状态")
        }

        assertEquals("故事状态损坏（states.json），已停止写入以避免覆盖原数据", error.message)
        assertFalse(error.message.orEmpty().contains(secretLikeContent))
    }

    @Test
    fun decode_rejectsJsonNull() {
        assertThrows(CorruptStoredDataException::class.java) {
            codec.decode("null", file, StoryState::class.java, "故事状态")
        }
    }

    @Test
    fun decode_rejectsLenientOnlyJson() {
        assertThrows(CorruptStoredDataException::class.java) {
            codec.decode("{current_round:4}", file, StoryState::class.java, "故事状态")
        }
    }
}
