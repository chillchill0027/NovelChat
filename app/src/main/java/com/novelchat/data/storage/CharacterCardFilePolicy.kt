package com.novelchat.data.storage

import com.novelchat.data.model.CharacterCard

object CharacterCardFilePolicy {
    fun fileStem(card: CharacterCard): String {
        return safeFileName(card.id.ifBlank { card.name })
    }

    fun fileStem(raw: String): String {
        return safeFileName(raw)
    }

    fun safeFileName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "unnamed"
        val cleaned = trimmed
            .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .take(80)
        return if (cleaned == trimmed) cleaned else "${cleaned}_${trimmed.hashCode().toUInt().toString(16)}"
    }
}
