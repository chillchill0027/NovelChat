package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterIdentityPolicy
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.CharacterReferencePolicy
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NewCharacter
import com.novelchat.data.model.RetiredCharacter
import com.novelchat.data.model.TaskAssignment

internal object TaskAssignmentRosterPolicy {

    fun relationshipStatusFor(relationshipToPlayer: String): String {
        val relationship = relationshipToPlayer.trim()
        return when {
            relationship.contains("妻") || relationship.contains("丈夫") ||
                relationship.contains("老婆") || relationship.contains("老公") ||
                relationship.contains("配偶") -> "已婚"
            relationship.contains("女朋友") || relationship.contains("女友") ||
                relationship.contains("男朋友") || relationship.contains("男友") ||
                relationship.contains("恋人") || relationship.contains("伴侣") -> "恋爱中"
            else -> "未知"
        }
    }

    fun reconcile(
        assignment: TaskAssignment,
        knownCharacters: List<CharacterCard>,
        introducedCharacters: List<IntroducedCharacter>,
        playerName: String,
        narrativeContext: String = "",
        currentNarrative: String = ""
    ): TaskAssignment {
        val knownNames = knownCharacters.map { it.name.trim() }.filter { it.isNotBlank() }.toSet()
        val normalizedIntroductions = introducedCharacters
            .map { normalizeIntroduction(it, knownCharacters) }
            .filter { it.name.isNotBlank() && it.name != playerName }
            .distinctBy { it.name }
        val newCharacters = linkedMapOf<String, NewCharacter>()
        val aliasRetirements = linkedMapOf<String, RetiredCharacter>()

        fun registerAliasReplacement(candidate: NewCharacter) {
            val probe = CharacterCard(
                name = candidate.name,
                occupation = candidate.occupation,
                background = candidate.background,
                appearance = candidate.appearance
            )
            CharacterIdentityPolicy.findHighConfidenceAlias(knownCharacters, probe)
                ?.takeIf { known -> known.name != candidate.name }
                ?.let { known ->
                    aliasRetirements.putIfAbsent(
                        known.name,
                        RetiredCharacter(
                            name = known.name,
                            reason = "同一角色的实名或称呼已在后续剧情中确认"
                        )
                    )
                }
        }

        assignment.newCharacters.forEach { character ->
            if (CharacterReferencePolicy.resolve(knownCharacters, character.name) != null) {
                return@forEach
            }
            val normalized = normalizeNewCharacter(character)
            if (narrativeContext.isNotBlank() && !narrativeContext.contains(normalized.name)) {
                return@forEach
            }
            if (normalized.name.isNotBlank() && normalized.name != playerName && normalized.name !in knownNames) {
                registerAliasReplacement(normalized)
                newCharacters.putIfAbsent(normalized.name, normalized)
            }
        }
        normalizedIntroductions.forEach { character ->
            if (character.name in knownNames) return@forEach
            val existing = newCharacters[character.name]
            if (existing == null) {
                val candidate = character.toNewCharacter(playerName)
                registerAliasReplacement(candidate)
                newCharacters[character.name] = candidate
            } else {
                val anchoredOccupation = character.occupation
                    .takeUnless(::isUncertainValue)
                    .orEmpty()
                    .ifBlank {
                        character.relationshipToPlayer.takeIf { it.isNotBlank() }
                            ?.let { relationship -> "${playerName.ifBlank { "主角" }}的$relationship" }
                            .orEmpty()
                    }
                newCharacters[character.name] = existing.copy(
                    occupation = existing.occupation
                        .takeUnless(::isUncertainValue)
                        .orEmpty()
                        .ifBlank { anchoredOccupation.ifBlank { "剧情人物" } },
                    background = existing.background.ifBlank { character.firstAppearance },
                    relationshipToPlayer = existing.relationshipToPlayer
                        .takeUnless { it.isBlank() || it in genericRelationships }
                        ?: character.relationshipToPlayer.ifBlank { existing.relationshipToPlayer },
                    firstAppearance = existing.firstAppearance.ifBlank { character.firstAppearance }
                )
            }
        }

        val storyUpdate = assignment.storyUpdate?.let { update ->
            update.activeCharacters?.let { requestedCharacters ->
                val finalSceneText = currentNarrative.takeLast(FINAL_SCENE_WINDOW)
                val sceneEvidence = finalSceneText.ifBlank { narrativeContext }
                val resolvedCharacters = requestedCharacters.mapNotNull { requested ->
                    resolveSceneCharacter(
                        requested = requested,
                        playerName = playerName,
                        knownCharacters = knownCharacters,
                        introductions = normalizedIntroductions,
                        newCharacters = newCharacters
                    )?.takeIf { resolvedName ->
                        sceneNameIsGrounded(
                            narrativeContext = narrativeContext,
                            resolvedName = resolvedName,
                            knownCharacters = knownCharacters
                        ) && sceneNameIsPhysicallyPresent(
                            narrativeContext = sceneEvidence,
                            resolvedName = resolvedName,
                            knownCharacters = knownCharacters
                        )
                    }
                }
                val groundedKnownCharacters = knownCharacters
                    .filter { character ->
                        finalSceneText.isNotBlank() &&
                            sceneNameIsPhysicallyPresent(finalSceneText, character.name, knownCharacters)
                    }
                    .map { it.name }
                val groundedNewCharacters = newCharacters.keys.filter { name ->
                    finalSceneText.isNotBlank() &&
                        sceneNameIsPhysicallyPresent(finalSceneText, name, knownCharacters)
                }
                update.copy(
                    activeCharacters = (
                        resolvedCharacters +
                            groundedKnownCharacters +
                            groundedNewCharacters
                        ).distinct()
                )
            } ?: update
        }
        if (narrativeContext.isNotBlank()) {
            newCharacters.entries.removeAll { (name, _) -> !narrativeContext.contains(name) }
        }
        val groundedCharacterUpdates = assignment.characterUpdates.mapNotNull { update ->
            val reference = update.name.trim()
            if (reference.isBlank() || isPlayerReference(reference, playerName, knownCharacters)) {
                return@mapNotNull null
            }
            val resolvedName = CharacterReferencePolicy.resolve(knownCharacters, reference)?.name
                ?: newCharacters.keys.filter { name ->
                    name == reference || name.contains(reference) || reference.contains(name)
                }.distinct().singleOrNull()
                ?: return@mapNotNull null
            val isGrounded = narrativeContext.isBlank() ||
                narrativeContext.contains(resolvedName) ||
                (resolvedName.length > 2 && narrativeContext.contains(resolvedName.takeLast(2)))
            update.copy(name = resolvedName).takeIf { isGrounded }
        }

        return assignment.copy(
            storyUpdate = storyUpdate,
            newCharacters = newCharacters.values.toList(),
            retiredCharacters = (assignment.retiredCharacters + aliasRetirements.values)
                .distinctBy { character -> character.name },
            characterUpdates = groundedCharacterUpdates
        )
    }

    private fun resolveSceneCharacter(
        requested: String,
        playerName: String,
        knownCharacters: List<CharacterCard>,
        introductions: List<IntroducedCharacter>,
        newCharacters: MutableMap<String, NewCharacter>
    ): String? {
        val reference = requested.trim()
        if (reference.isBlank() || reference == playerName) return null

        val rosterNames = knownCharacters.map { it.name } + newCharacters.keys
        rosterNames.firstOrNull { it == reference }?.let { return it }
        rosterNames.filter { it.contains(reference) || reference.contains(it) }
            .distinct()
            .singleOrNull()
            ?.let { return it }

        val role = roleCategory(reference)
        if (role != null) {
            val roleMatches = buildList {
                introductions.filterTo(this) { roleCategory(it.occupation + it.relationshipToPlayer) == role }
                newCharacters.values.forEach { character ->
                    if (roleCategory(character.occupation + character.relationshipToPlayer) == role) {
                        add(IntroducedCharacter(name = character.name, occupation = character.occupation))
                    }
                }
                knownCharacters.forEach { character ->
                    val roleText = buildString {
                        append(character.occupation)
                        append(character.background)
                        character.relationships.values.forEach { append(it.stage) }
                    }
                    if (roleCategory(roleText) == role) {
                        add(IntroducedCharacter(name = character.name, occupation = character.occupation))
                    }
                }
            }.map { it.name }.distinct()
            roleMatches.singleOrNull()?.let { return it }
        }

        val fallbackCard = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = reference, occupation = reference.takeIf(CharacterNamingPolicy::isGenericNpcName).orEmpty())
        )
        val fallbackName = fallbackCard.name.trim()
        if (fallbackName.isBlank() || fallbackName == playerName) return null
        if (fallbackName !in newCharacters && knownCharacters.none { it.name == fallbackName }) {
            newCharacters[fallbackName] = NewCharacter(
                name = fallbackName,
                occupation = fallbackCard.occupation.ifBlank { "剧情人物" },
                personality = "待观察",
                background = fallbackCard.background.ifBlank { "$fallbackName 在本轮剧情中首次与主角发生交互。" },
                appearance = "尚待进一步了解",
                relationshipToPlayer = "初识",
                firstAppearance = "本轮正文结尾仍与主角同场"
            )
        }
        return fallbackName
    }

    private fun sceneNameIsGrounded(
        narrativeContext: String,
        resolvedName: String,
        knownCharacters: List<CharacterCard>
    ): Boolean {
        if (narrativeContext.isBlank() || narrativeContext.contains(resolvedName)) return true
        return knownCharacters.any { it.name == resolvedName } &&
            resolvedName.length > 2 && narrativeContext.contains(resolvedName.takeLast(2))
    }

    private fun sceneNameIsPhysicallyPresent(
        narrativeContext: String,
        resolvedName: String,
        knownCharacters: List<CharacterCard>
    ): Boolean {
        if (narrativeContext.isBlank()) return true
        val aliases = buildList {
            add(resolvedName)
            if (knownCharacters.any { it.name == resolvedName } && resolvedName.length > 2) {
                add(resolvedName.takeLast(2))
            }
        }.distinct()
        val mentionSentences = narrativeContext
            .split(sceneSentenceBoundary)
            .filter { sentence -> aliases.any(sentence::contains) }
        if (mentionSentences.isEmpty()) return false
        return mentionSentences.any { sentence ->
            aliases.any { alias -> hasPhysicalPresenceEvidence(sentence, alias) }
        }
    }

    private fun hasPhysicalPresenceEvidence(sentence: String, name: String): Boolean {
        val escapedName = Regex.escape(name)
        val futureArrival = Regex(
            "$escapedName[^。！？!?\\n]{0,12}" +
                "(?:马上|很快|稍后|待会|一会儿|将要|将会|正(?:在)?赶|还没|尚未|未)" +
                "[^。！？!?\\n]{0,10}(?:来|到|抵达|出现)"
        )
        val remoteOrRecalled = Regex(
            "(?:想起|想到|惦记|回忆起|提到|谈到|说起|等待?|等着)[^。！？!?\\n]{0,14}$escapedName|" +
                "$escapedName[^。！？!?\\n]{0,14}" +
                "(?:发来|打来|在电话|在微信|在消息|在视频|电话那头|电话另一端|" +
                "不在(?:这里|现场|身边)|已经离开|先走了)"
        )
        val hasNonPresentSignal = futureArrival.containsMatchIn(sentence) ||
            remoteOrRecalled.containsMatchIn(sentence)
        if (!hasNonPresentSignal) return true

        val explicitArrivalOrInteraction = Regex(
            "$escapedName\\s*(?:(?:终于|随即|已经|此时|随后|也|则|便)[，,]?\\s*)?" +
                "(?:推开|走进|来到|赶到|抵达|出现|站在|坐在|靠在|递给|接过|" +
                "拿起|放下|开口|说道|问道|回答|点头|摇头|看向|望向)"
        )
        return explicitArrivalOrInteraction.containsMatchIn(sentence)
    }

    private fun isPlayerReference(
        rawReference: String,
        playerName: String,
        knownCharacters: List<CharacterCard>
    ): Boolean {
        val reference = rawReference.trim()
        val canonicalPlayerName = playerName.trim()
        if (reference.equals(canonicalPlayerName, ignoreCase = true)) return true
        if (reference.lowercase() in playerAliases) return true
        if (knownCharacters.any { it.name == reference || it.id == reference }) return false
        if (canonicalPlayerName.isBlank()) return false

        if (playerHonorifics.any { suffix -> reference == canonicalPlayerName + suffix }) return true
        val familyName = canonicalPlayerName.firstOrNull()?.toString().orEmpty()
        return canonicalPlayerName.length in 2..4 &&
            playerHonorifics.any { suffix -> reference == familyName + suffix }
    }

    private fun normalizeIntroduction(
        character: IntroducedCharacter,
        knownCharacters: List<CharacterCard>
    ): IntroducedCharacter {
        CharacterReferencePolicy.resolve(knownCharacters, character.name)?.let { known ->
            return character.copy(
                name = known.name,
                occupation = character.occupation.ifBlank { known.occupation }
            )
        }
        val card = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = character.name, occupation = character.occupation)
        )
        return character.copy(
            name = card.name,
            occupation = character.occupation.ifBlank { card.occupation }
        )
    }

    private fun normalizeNewCharacter(character: NewCharacter): NewCharacter {
        val card = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = character.name, occupation = character.occupation)
        )
        return character.copy(
            name = card.name,
            occupation = character.occupation.ifBlank { card.occupation },
            background = character.background.ifBlank { card.background }
        )
    }

    private fun IntroducedCharacter.toNewCharacter(playerName: String): NewCharacter = NewCharacter(
        name = name,
        occupation = occupation
            .takeUnless(::isUncertainValue)
            .orEmpty()
            .ifBlank {
                relationshipToPlayer.takeIf { it.isNotBlank() }
                    ?.let { relationship -> "${playerName.ifBlank { "主角" }}的$relationship" }
                    ?: "剧情人物"
            },
        personality = "待观察",
        background = firstAppearance.ifBlank { "$name 在本轮剧情中首次登场。" },
        appearance = "尚待进一步了解",
        relationshipToPlayer = relationshipToPlayer.ifBlank { "初识" },
        firstAppearance = firstAppearance
    )

    private fun isUncertainValue(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isBlank() || uncertainValue.containsMatchIn(normalized)
    }

    private fun roleCategory(value: String): String? {
        val text = value.lowercase()
        return roleCategories.firstOrNull { (_, aliases) -> aliases.any(text::contains) }?.first
    }

    private val roleCategories = listOf(
        "伴侣" to listOf("girlfriend", "boyfriend", "wife", "husband", "partner", "女朋友", "女友", "男朋友", "男友", "妻子", "丈夫", "老婆", "老公", "恋人", "伴侣"),
        "店主" to listOf("老板", "店主", "店老板"),
        "店员" to listOf("店员", "服务员", "工作人员", "前台", "接待"),
        "房东" to listOf("房东"),
        "邻居" to listOf("邻居", "居民"),
        "医护" to listOf("医生", "护士"),
        "安保" to listOf("保安"),
        "司机" to listOf("司机")
    )

    private const val FINAL_SCENE_WINDOW = 600

    private val sceneSentenceBoundary = Regex("[。！？!?\\n]+")
    private val uncertainValue = Regex("未知|不详|待定|可能|也许|或者|或(?=[^业])")
    private val genericRelationships = setOf("初识", "陌生人", "不熟", "未知")
    private val playerAliases = setOf("主角", "玩家", "我", "用户", "player", "protagonist", "user")
    private val playerHonorifics = setOf("先生", "女士", "小姐", "老师", "同志")
}
