package com.novelchat.data.model

object CharacterReferencePolicy {

    fun resolve(cards: List<CharacterCard>, rawReference: String): CharacterCard? {
        val reference = rawReference.trim()
        if (reference.isBlank()) return null
        cards.singleOrNull { it.name == reference || it.id == reference }?.let { return it }

        if (CharacterNamingPolicy.isGenericNpcName(reference)) {
            val normalized = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = reference))
            val requestedRole = roleCategory(reference + normalized.occupation)
            if (requestedRole != null) {
                val roleMatches = cards.filter { card ->
                    roleCategory(roleText(card)) == requestedRole
                }
                roleMatches.singleOrNull()?.let { return it }
                if (roleMatches.size > 1) return null
            }
            cards.singleOrNull { it.name == normalized.name }?.let { return it }
        }

        if (reference.length < 2) return null
        return cards.filter { card ->
            card.name.contains(reference) || reference.contains(card.name)
        }.singleOrNull()
    }

    private fun roleText(card: CharacterCard): String = buildString {
        append(card.name)
        append(card.occupation)
        append(card.background)
        append(card.relationshipStatus)
        card.relationships.values.forEach { append(it.stage) }
    }

    private fun roleCategory(value: String): String? {
        val text = value.lowercase()
        return roleCategories.firstOrNull { (_, aliases) ->
            aliases.any(text::contains)
        }?.first
    }

    private val roleCategories = listOf(
        "伴侣" to listOf(
            "女朋友", "女友", "男朋友", "男友", "妻子", "丈夫", "老婆", "老公",
            "恋人", "伴侣", "girlfriend", "boyfriend", "wife", "husband", "partner"
        ),
        "店主" to listOf("老板", "店主", "店老板"),
        "店员" to listOf("店员", "服务员", "工作人员", "前台", "接待"),
        "房东" to listOf("房东"),
        "邻居" to listOf("邻居", "居民"),
        "医护" to listOf("医生", "护士"),
        "安保" to listOf("保安"),
        "司机" to listOf("司机"),
        "同事" to listOf("同事"),
        "顾问" to listOf("招聘顾问", "顾问")
    )
}
