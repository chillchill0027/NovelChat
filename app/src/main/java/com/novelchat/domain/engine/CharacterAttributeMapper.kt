package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.MentalStatePatch
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.PlayerSkill
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.PlayerUpdate
import com.novelchat.data.model.Relationship

/**
 * V1 与 V2 共用的纯函数角色属性、状态与库存映射工具。
 */
internal object CharacterAttributeMapper {

    fun applyPlayerUpdate(
        player: PlayerState,
        update: PlayerUpdate,
        characterFields: List<CharacterFieldDefinition>
    ): PlayerState {
        val changes = update.changes
        var updatedInventory = player.inventory
        changes.inventory.forEach { invChange ->
            updatedInventory = when (invChange.action.trim().lowercase()) {
                "add" -> addItems(updatedInventory, invChange.item, invChange.quantity)
                "remove" -> removeItems(updatedInventory, invChange.item, invChange.quantity)
                else -> updatedInventory
            }
        }
        val updatedRelationships = PlayerStatePatchPolicy.mergeRelationships(
            player.relationships,
            changes.relationships
        )
        val updated = player.copy(
            inventory = updatedInventory,
            relationships = updatedRelationships,
            questLog = changes.questLog ?: player.questLog,
            storyProgress = PlayerStatePatchPolicy.mergeStoryProgress(
                player.storyProgress,
                changes.storyProgress
            ),
            physicalState = changes.physicalState?.let {
                applyPhysicalPatch(player.physicalState, it, characterFields)
            } ?: player.physicalState,
            mentalState = changes.mentalState?.let {
                applyMentalPatch(player.mentalState, it, characterFields)
            } ?: player.mentalState,
            cv = changes.risk?.let {
                numberFieldValue(characterFields, "risk", it, player.cv)
            } ?: player.cv
        )
        return applyPlayerAttributes(updated, changes.attributes, characterFields)
    }

    fun applyPhysicalPatch(
        current: PhysicalState,
        patch: PhysicalStatePatch,
        characterFields: List<CharacterFieldDefinition>
    ): PhysicalState {
        return current.copy(
            stamina = patch.stamina?.let {
                numberFieldValue(characterFields, "stamina", it, current.stamina)
            } ?: current.stamina,
            health = textFieldValue(characterFields, "health", patch.health, current.health),
            injuries = patch.injuries ?: current.injuries,
            fatigue = textFieldValue(characterFields, "fatigue", patch.fatigue, current.fatigue),
            hunger = textFieldValue(characterFields, "hunger", patch.hunger, current.hunger),
            pain = textFieldValue(characterFields, "pain", patch.pain, current.pain)
        )
    }

    fun applyMentalPatch(
        current: MentalState,
        patch: MentalStatePatch,
        characterFields: List<CharacterFieldDefinition>
    ): MentalState {
        return current.copy(
            mood = textFieldValue(characterFields, "mood", patch.mood, current.mood),
            stress = textFieldValue(characterFields, "stress", patch.stress, current.stress),
            focus = textFieldValue(characterFields, "focus", patch.focus, current.focus),
            currentThoughts = textFieldValue(
                characterFields,
                "current_thoughts",
                patch.currentThoughts,
                current.currentThoughts
            )
        )
    }

    fun validateCharacterCard(
        card: CharacterCard,
        characterFields: List<CharacterFieldDefinition>
    ): CharacterCard {
        val namedCard = CharacterNamingPolicy.normalizeNpcCard(card)
        return namedCard.copy(
            age = numberFieldValue(characterFields, "age", card.age, 0),
            physicalState = namedCard.physicalState.copy(
                stamina = numberFieldValue(
                    characterFields,
                    "stamina",
                    namedCard.physicalState.stamina,
                    100
                ),
                health = textFieldValue(
                    characterFields,
                    "health",
                    namedCard.physicalState.health,
                    "良好"
                ),
                fatigue = textFieldValue(
                    characterFields,
                    "fatigue",
                    namedCard.physicalState.fatigue,
                    "无"
                ),
                hunger = textFieldValue(
                    characterFields,
                    "hunger",
                    namedCard.physicalState.hunger,
                    "不饿"
                ),
                pain = textFieldValue(
                    characterFields,
                    "pain",
                    namedCard.physicalState.pain,
                    "无"
                )
            ),
            mentalState = namedCard.mentalState.copy(
                stress = textFieldValue(
                    characterFields,
                    "stress",
                    namedCard.mentalState.stress,
                    "低"
                )
            ),
            customAttributes = CharacterFieldPolicy.normalizeImportedAttributes(
                namedCard.customAttributes,
                characterFields,
                CharacterFieldDefinition.TARGET_NPC
            ),
            relationships = namedCard.relationships.mapValues { (_, rel) ->
                rel.copy(favor = rel.favor.coerceIn(-100, 100), trust = rel.trust.coerceIn(0, 100))
            },
            inventory = namedCard.inventory.map { it.copy(quantity = it.quantity.coerceAtLeast(1)) }
        )
    }

    fun applyPlayerAttributes(
        player: PlayerState,
        attributes: Map<String, String>,
        characterFields: List<CharacterFieldDefinition>
    ): PlayerState {
        val values = mutableAttributeUpdates(
            attributes,
            characterFields,
            CharacterFieldDefinition.TARGET_PLAYER
        )
        if (values.isEmpty()) return player

        var updated = player
        values.forEach { (field, value) ->
            updated = when (field.key) {
                "age" -> updated.copy(age = value.toIntOrNull() ?: updated.age)
                "gender" -> updated.copy(gender = value)
                "race" -> updated.copy(race = value)
                "occupation" -> updated.copy(class_ = value)
                "appearance" -> updated.copy(appearance = value)
                "personality" -> updated.copy(personality = value)
                "background" -> updated.copy(background = value)
                "skills" -> {
                    val existing = updated.skills.associateBy { it.name }
                    val skills = value.split('、').map(String::trim).filter(String::isNotBlank)
                        .map { name -> existing[name] ?: PlayerSkill(name = name, level = 1) }
                    updated.copy(skills = skills)
                }
                "level" -> updated.copy(level = value.toIntOrNull() ?: updated.level)
                "charm" -> updated.copy(charm = value.toIntOrNull() ?: updated.charm)
                "action_skill" -> updated.copy(
                    huntingSkill = value.toIntOrNull() ?: updated.huntingSkill
                )
                "social_rank" -> updated.copy(socialRank = value)
                "luck" -> updated.copy(luck = value.toIntOrNull() ?: updated.luck)
                "willpower" -> updated.copy(alpha = value.toIntOrNull() ?: updated.alpha)
                "resilience" -> updated.copy(
                    resistance = value.toIntOrNull() ?: updated.resistance
                )
                "stamina" -> updated.copy(
                    physicalState = updated.physicalState.copy(
                        stamina = value.toIntOrNull() ?: updated.physicalState.stamina
                    )
                )
                "health" -> updated.copy(physicalState = updated.physicalState.copy(health = value))
                "fatigue" -> updated.copy(physicalState = updated.physicalState.copy(fatigue = value))
                "hunger" -> updated.copy(physicalState = updated.physicalState.copy(hunger = value))
                "pain" -> updated.copy(physicalState = updated.physicalState.copy(pain = value))
                "mood" -> updated.copy(mentalState = updated.mentalState.copy(mood = value))
                "stress" -> updated.copy(mentalState = updated.mentalState.copy(stress = value))
                "focus" -> updated.copy(mentalState = updated.mentalState.copy(focus = value))
                "current_thoughts" -> updated.copy(
                    mentalState = updated.mentalState.copy(currentThoughts = value)
                )
                "risk" -> updated.copy(cv = value.toIntOrNull() ?: updated.cv)
                "name" -> updated
                else -> updated
            }
        }
        val filteredPatch = values.associate { (field, sanitized) -> field.key to sanitized }
        return updated.copy(
            customAttributes = CharacterFieldPolicy.mergeCustomAttributes(
                current = updated.customAttributes,
                patch = filteredPatch,
                configured = characterFields,
                target = CharacterFieldDefinition.TARGET_PLAYER
            )
        )
    }

    fun applyNpcAttributes(
        card: CharacterCard,
        attributes: Map<String, String>,
        characterFields: List<CharacterFieldDefinition>,
        playerName: String,
        includeManual: Boolean = false
    ): CharacterCard {
        val values = mutableAttributeUpdates(
            attributes,
            characterFields,
            CharacterFieldDefinition.TARGET_NPC,
            includeManual
        )
        if (values.isEmpty()) return card

        var updated = card
        values.forEach { (field, value) ->
            updated = when (field.key) {
                "age" -> updated.copy(age = value.toIntOrNull() ?: updated.age)
                "gender" -> updated.copy(gender = value)
                "race" -> updated.copy(race = value)
                "occupation" -> updated.copy(occupation = value)
                "appearance" -> updated.copy(appearance = value)
                "personality" -> updated.copy(personality = value)
                "background" -> updated.copy(background = value)
                "rarity" -> updated.copy(rarity = value)
                "action_tendency" -> updated.copy(libido = value.toIntOrNull() ?: updated.libido)
                "defense_threshold" -> updated.copy(
                    threshold = value.toIntOrNull() ?: updated.threshold
                )
                "relationship_status" -> updated.copy(relationshipStatus = value)
                "hidden_clue" -> updated.copy(kink = value)
                "stamina" -> updated.copy(
                    physicalState = updated.physicalState.copy(
                        stamina = value.toIntOrNull() ?: updated.physicalState.stamina
                    )
                )
                "health" -> updated.copy(physicalState = updated.physicalState.copy(health = value))
                "fatigue" -> updated.copy(physicalState = updated.physicalState.copy(fatigue = value))
                "hunger" -> updated.copy(physicalState = updated.physicalState.copy(hunger = value))
                "pain" -> updated.copy(physicalState = updated.physicalState.copy(pain = value))
                "mood" -> updated.copy(
                    emotion = value,
                    mentalState = updated.mentalState.copy(mood = value)
                )
                "stress" -> updated.copy(mentalState = updated.mentalState.copy(stress = value))
                "focus" -> updated.copy(mentalState = updated.mentalState.copy(focus = value))
                "current_thoughts" -> updated.copy(
                    mentalState = updated.mentalState.copy(currentThoughts = value)
                )
                "relationship_stage", "favor", "trust" -> {
                    val current = updated.relationships[playerName]
                        ?: Relationship(favor = updated.favor, stage = updated.relationshipStatus)
                    val relationship = when (field.key) {
                        "relationship_stage" -> current.copy(stage = value)
                        "favor" -> current.copy(favor = value.toIntOrNull() ?: current.favor)
                        else -> current.copy(trust = value.toIntOrNull() ?: current.trust)
                    }
                    updated.copy(
                        favor = relationship.favor,
                        relationships = updated.relationships + (playerName to relationship)
                    )
                }
                "name" -> updated
                else -> updated
            }
        }
        val filteredPatch = values.associate { (field, sanitized) -> field.key to sanitized }
        return updated.copy(
            customAttributes = CharacterFieldPolicy.mergeCustomAttributes(
                current = updated.customAttributes,
                patch = filteredPatch,
                configured = characterFields,
                target = CharacterFieldDefinition.TARGET_NPC
            )
        )
    }

    fun mutableAttributeUpdates(
        attributes: Map<String, String>,
        characterFields: List<CharacterFieldDefinition>,
        target: String,
        includeManual: Boolean = false
    ): List<Pair<CharacterFieldDefinition, String>> {
        if (attributes.isEmpty()) return emptyList()
        val definitions = CharacterFieldPolicy.effectiveDefinitions(characterFields)
            .filter { field ->
                (includeManual || field.updateMode != CharacterFieldDefinition.UPDATE_MANUAL) &&
                    CharacterFieldPolicy.appliesTo(field, target)
            }
            .associateBy { it.key }
        return attributes.mapNotNull { (rawKey, rawValue) ->
            val field = definitions[rawKey.trim()] ?: return@mapNotNull null
            CharacterFieldPolicy.sanitizeValue(field, rawValue)?.let { field to it }
        }
    }

    fun numberFieldValue(
        characterFields: List<CharacterFieldDefinition>,
        key: String,
        value: Int,
        fallback: Int
    ): Int {
        val field = CharacterFieldPolicy.definitionFor(characterFields, key) ?: return value
        return CharacterFieldPolicy.sanitizeValue(field, value.toString())?.toIntOrNull() ?: fallback
    }

    fun textFieldValue(
        characterFields: List<CharacterFieldDefinition>,
        key: String,
        value: String?,
        fallback: String
    ): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        val field = CharacterFieldPolicy.definitionFor(characterFields, key) ?: return raw
        return CharacterFieldPolicy.sanitizeValue(field, raw) ?: fallback
    }

    fun addItems(inventory: List<InventoryItem>, itemName: String, quantity: Int): List<InventoryItem> {
        val normalizedName = itemName.trim()
        if (normalizedName.isBlank()) return inventory
        val normalizedQuantity = quantity.coerceIn(1, 9999)
        val updated = inventory.toMutableList()
        val existing = updated.find { it.name == normalizedName }
        if (existing != null) {
            val idx = updated.indexOf(existing)
            updated[idx] = existing.copy(
                quantity = (existing.quantity.coerceAtLeast(0) + normalizedQuantity).coerceAtMost(9999)
            )
        } else {
            updated.add(InventoryItem(name = normalizedName, quantity = normalizedQuantity))
        }
        return updated
    }

    fun removeItems(inventory: List<InventoryItem>, itemName: String, quantity: Int): List<InventoryItem> {
        val normalizedName = itemName.trim()
        if (normalizedName.isBlank()) return inventory
        val normalizedQuantity = quantity.coerceIn(1, 9999)
        val updated = inventory.toMutableList()
        val idx = updated.indexOfFirst { it.name == normalizedName }
        if (idx >= 0) {
            val item = updated[idx]
            if (item.quantity > normalizedQuantity) {
                updated[idx] = item.copy(quantity = item.quantity - normalizedQuantity)
            } else {
                updated.removeAt(idx)
            }
        }
        return updated
    }
}
