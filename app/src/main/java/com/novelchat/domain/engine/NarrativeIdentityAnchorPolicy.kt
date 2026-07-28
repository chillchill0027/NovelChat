package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NarrativeResult

data class NarrativeIdentityAnchor(
    val reference: String,
    val name: String,
    val relationship: String,
    val aliases: List<String>,
    val isExistingCharacter: Boolean
)

internal object NarrativeIdentityAnchorPolicy {

    private data class RelationshipSpec(
        val reference: String,
        val relationship: String,
        val aliases: List<String>,
        val generatedNames: List<String>
    )

    private val relationshipSpecs = listOf(
        RelationshipSpec("女友", "女友", listOf("女朋友", "女友", "girlfriend"), listOf("苏晚", "沈清", "许岚")),
        RelationshipSpec("妻子", "妻子", listOf("妻子", "老婆", "wife"), listOf("苏晚", "沈清", "许岚")),
        RelationshipSpec("男友", "男友", listOf("男朋友", "男友", "boyfriend"), listOf("周衡", "陈屿", "许川")),
        RelationshipSpec("丈夫", "丈夫", listOf("丈夫", "老公", "husband"), listOf("周衡", "陈屿", "许川")),
        RelationshipSpec("伴侣", "伴侣", listOf("恋人", "伴侣", "partner"), listOf("顾宁", "程安", "林澈"))
    )
    private val invalidExplicitNames = setOf(
        "之后", "以后", "已经", "正在", "还在", "安置", "安顿", "照顾", "安全",
        "身体", "今天", "这次", "那个", "一个", "身边", "一起", "回家", "留下", "休息"
    )

    fun resolve(
        userMessage: String,
        knownCharacters: List<CharacterCard>,
        playerName: String
    ): List<NarrativeIdentityAnchor> {
        val occupiedNames = knownCharacters.map { it.name }.toMutableSet().apply { add(playerName) }
        return relationshipSpecs.mapNotNull { spec ->
            if (spec.aliases.none { userMessage.contains(it, ignoreCase = true) }) {
                return@mapNotNull null
            }
            val existing = knownCharacters.firstOrNull { card ->
                roleText(card).any { text ->
                    spec.aliases.any { alias -> text.contains(alias, ignoreCase = true) }
                } && !CharacterNamingPolicy.isGenericNpcName(card.name)
            }
            val explicitName = explicitName(userMessage, spec)
            val resolvedName = existing?.name
                ?: explicitName
                ?: spec.generatedNames.firstOrNull { it !in occupiedNames }
                ?: "${spec.generatedNames.first()}二"
            occupiedNames += resolvedName
            NarrativeIdentityAnchor(
                reference = spec.reference,
                name = resolvedName,
                relationship = spec.relationship,
                aliases = spec.aliases,
                isExistingCharacter = existing != null
            )
        }
    }

    fun apply(
        result: NarrativeResult,
        anchors: List<NarrativeIdentityAnchor>,
        playerName: String
    ): NarrativeResult {
        if (anchors.isEmpty()) return result
        var narrative = result.narrative
        val introduced = result.introducedCharacters
            .filterNot { character ->
                character.name == playerName ||
                    anchors.any { it.isExistingCharacter && sameName(it.name, character.name) }
            }
            .toMutableList()

        anchors.forEach { anchor ->
            if (!mentionsName(narrative, anchor.name)) {
                val alias = anchor.aliases
                    .sortedByDescending { it.length }
                    .firstOrNull { narrative.contains(it, ignoreCase = true) }
                if (alias != null) {
                    narrative = narrative.replaceFirst(
                        Regex(Regex.escape(alias), RegexOption.IGNORE_CASE),
                        "$alias${anchor.name}"
                    )
                }
            }
            if (
                !anchor.isExistingCharacter &&
                mentionsName(narrative, anchor.name) &&
                introduced.none { sameName(it.name, anchor.name) }
            ) {
                introduced += IntroducedCharacter(
                    name = anchor.name,
                    occupation = "职业未明确",
                    relationshipToPlayer = anchor.relationship,
                    firstAppearance = "${anchor.name}以${anchor.relationship}身份在本轮首次登场"
                )
            }
        }
        return result.copy(
            narrative = narrative,
            introducedCharacters = introduced.distinctBy { it.name.trim() }
        )
    }

    private fun explicitName(userMessage: String, spec: RelationshipSpec): String? {
        val chineseAliases = spec.aliases.filter { alias -> alias.any { it.code > 127 } }
        if (chineseAliases.isNotEmpty()) {
            val role = chineseAliases.joinToString("|") { Regex.escape(it) }
            val match = Regex("(?:$role)(?:叫|名叫|是)?\\s*([\\u4e00-\\u9fff]{2,3})")
                .find(userMessage)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            if (
                !match.isNullOrBlank() &&
                invalidExplicitNames.none { invalid -> match.startsWith(invalid) } &&
                !CharacterNamingPolicy.isGenericNpcName(match)
            ) {
                return match
            }
        }
        val englishAliases = spec.aliases.filter { alias -> alias.all { it.code < 128 } }
        val englishRole = englishAliases.joinToString("|") { Regex.escape(it) }
        return englishRole
            .takeIf { it.isNotBlank() }
            ?.let { Regex("(?:$it)\\s+(?:named\\s+)?([A-Z][a-z]{1,20})") }
            ?.find(userMessage)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun roleText(card: CharacterCard): List<String> = buildList {
        add(card.occupation)
        add(card.background)
        add(card.relationshipStatus)
        card.relationships.values.forEach { add(it.stage) }
    }.filter { it.isNotBlank() }

    private fun mentionsName(text: String, name: String): Boolean {
        return text.contains(name) || (name.length > 2 && text.contains(name.takeLast(2)))
    }

    private fun sameName(left: String, right: String): Boolean {
        val first = left.trim()
        val second = right.trim()
        return first == second || first.contains(second) || second.contains(first)
    }
}
