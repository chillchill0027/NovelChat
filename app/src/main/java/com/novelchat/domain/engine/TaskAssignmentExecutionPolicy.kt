package com.novelchat.domain.engine

import com.novelchat.data.model.InventoryChange
import com.novelchat.data.model.ItemTransfer
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.MentalStatePatch
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.Quest
import com.novelchat.data.model.StoryProgressPatch
import com.novelchat.data.model.WorldbookContentPolicy
import com.novelchat.data.model.TaskAssignment

internal object TaskAssignmentExecutionPolicy {
    private const val MAX_CHARACTER_OPERATIONS = 24
    private const val MAX_WORLDBOOK_OPERATIONS = 32
    private const val MAX_TRANSFER_OPERATIONS = 32
    private const val MAX_EVENT_OPERATIONS = 32
    private const val MAX_SHORT_LENGTH = 120
    private const val MAX_REASON_LENGTH = 1_000
    private const val MAX_PROFILE_LENGTH = 2_000
    private const val MAX_BACKGROUND_LENGTH = 6_000
    private const val MAX_PLAYER_COLLECTION_ITEMS = 50
    private const val MAX_INJURIES = 20

    fun sanitize(assignment: TaskAssignment, playerName: String): TaskAssignment {
        val normalizedPlayerName = playerName.trim()
        val transfers = assignment.itemTransfers
            .map { transfer ->
                transfer.copy(
                    item = short(transfer.item),
                    quantity = transfer.quantity.coerceIn(1, 9999),
                    from = normalizeParticipant(transfer.from, normalizedPlayerName),
                    to = normalizeParticipant(transfer.to, normalizedPlayerName),
                    reason = transfer.reason.trim().take(MAX_REASON_LENGTH)
                )
            }
            .filter { transfer ->
                transfer.item.isNotBlank() && transfer.from.isNotBlank() && transfer.to.isNotBlank() &&
                    transfer.from != transfer.to
            }
            .distinctBy { transfer ->
                listOf(transfer.item.lowercase(), transfer.from, transfer.to)
            }
            .take(MAX_TRANSFER_OPERATIONS)

        val playerUpdate = assignment.playerUpdate?.let { update ->
            val changes = sanitizePlayerChanges(update.changes)
            update.copy(
                reason = update.reason.trim().take(MAX_REASON_LENGTH),
                changes = changes.copy(
                    inventory = changes.inventory.filterNot { change ->
                        isRepresentedByTransfer(change, transfers, normalizedPlayerName)
                    }
                )
            )
        }
        val events = assignment.events
            .map { event ->
                event.copy(
                    type = short(event.type),
                    target = short(event.target),
                    causedBy = short(event.causedBy),
                    time = short(event.time),
                    description = event.description.trim().take(MAX_REASON_LENGTH)
                )
            }
            .filter { event ->
                event.type.isNotBlank() && event.target.isNotBlank() && event.description.isNotBlank()
            }
            .distinctBy { event ->
                listOf(event.type.lowercase(), event.target.lowercase())
            }
            .take(MAX_EVENT_OPERATIONS)
        return assignment.copy(
            storyUpdate = assignment.storyUpdate?.let { update ->
                update.copy(
                    currentLocation = update.currentLocation?.let(::short),
                    timeOfDay = update.timeOfDay?.let(::short),
                    weather = update.weather?.let(::short),
                    mood = update.mood?.let { it.trim().take(MAX_PROFILE_LENGTH) },
                    activeCharacters = update.activeCharacters
                        ?.map(::short)
                        ?.filter(String::isNotBlank)
                        ?.distinct()
                        ?.take(MAX_CHARACTER_OPERATIONS),
                    pendingEvents = update.pendingEvents
                        ?.map { event -> event.trim().take(MAX_REASON_LENGTH) }
                        ?.filter(String::isNotBlank)
                        ?.distinct()
                        ?.take(MAX_EVENT_OPERATIONS)
                )
            },
            playerUpdate = playerUpdate,
            newCharacters = assignment.newCharacters
                .map { character ->
                    character.copy(
                        name = WorldbookContentPolicy.normalizeName(character.name),
                        age = character.age.coerceIn(0, CharacterFieldPolicy.MAX_NUMERIC_VALUE),
                        gender = short(character.gender),
                        race = short(character.race),
                        occupation = short(character.occupation),
                        personality = character.personality.trim().take(MAX_PROFILE_LENGTH),
                        background = character.background.trim().take(MAX_BACKGROUND_LENGTH),
                        appearance = character.appearance.trim().take(MAX_PROFILE_LENGTH),
                        relationshipToPlayer = short(character.relationshipToPlayer),
                        firstAppearance = character.firstAppearance.trim().take(MAX_REASON_LENGTH),
                        attributes = sanitizeAttributes(character.attributes),
                        initialStatus = null
                    )
                }
                .filter { character -> character.name.isNotBlank() }
                .distinctBy { character -> character.name }
                .take(MAX_CHARACTER_OPERATIONS),
            retiredCharacters = assignment.retiredCharacters
                .map { character ->
                    character.copy(
                        name = WorldbookContentPolicy.normalizeName(character.name),
                        reason = character.reason.trim().take(MAX_REASON_LENGTH),
                        retiredAt = short(character.retiredAt)
                    )
                }
                .filter { character -> character.name.isNotBlank() }
                .distinctBy { character -> character.name }
                .take(MAX_CHARACTER_OPERATIONS),
            characterUpdates = assignment.characterUpdates
                .map { update ->
                    update.copy(
                        name = WorldbookContentPolicy.normalizeName(update.name),
                        reason = update.reason.trim().take(MAX_REASON_LENGTH)
                    )
                }
                .filter { update -> update.name.isNotBlank() }
                .distinctBy { update -> update.name }
                .take(MAX_CHARACTER_OPERATIONS),
            worldbookUpdates = assignment.worldbookUpdates
                .map { update ->
                    update.copy(
                        action = update.action.trim().lowercase().take(16),
                        id = update.id?.trim()?.take(MAX_SHORT_LENGTH),
                        name = update.name?.let(WorldbookContentPolicy::normalizeName),
                        type = update.type?.trim()?.lowercase()?.take(32),
                        description = update.description
                            ?.let(WorldbookContentPolicy::normalizeDescription),
                        keywords = update.keywords?.let(WorldbookContentPolicy::normalizeKeywords),
                        changes = update.changes?.get("status")?.toString()?.let { status ->
                            mapOf("status" to WorldbookContentPolicy.normalizeStatus(status))
                        }
                    )
                }
                .filter { update ->
                    update.action in setOf("create", "update", "delete") &&
                        (!update.id.isNullOrBlank() || !update.name.isNullOrBlank())
                }
                .distinctBy { update ->
                    listOf(
                        update.action,
                        update.id.orEmpty().lowercase(),
                        update.name.orEmpty().lowercase()
                    )
                }
                .take(MAX_WORLDBOOK_OPERATIONS),
            itemTransfers = transfers,
            events = events,
            syncStatus = assignment.syncStatus.trim().lowercase().take(32),
            noChangeReason = assignment.noChangeReason.trim().take(MAX_REASON_LENGTH)
        )
    }

    private fun isRepresentedByTransfer(
        change: InventoryChange,
        transfers: List<ItemTransfer>,
        playerName: String
    ): Boolean {
        val action = change.action.trim().lowercase()
        val item = change.item.trim().lowercase()
        if (item.isBlank()) return false
        return transfers.any { transfer ->
            transfer.item.trim().lowercase() == item && when (action) {
                "add" -> transfer.to == playerName
                "remove" -> transfer.from == playerName
                else -> false
            }
        }
    }

    private fun sanitizePlayerChanges(changes: PlayerChanges): PlayerChanges {
        val relationships = changes.relationships.entries
            .asSequence()
            .mapNotNull { (rawName, patch) ->
                val name = short(rawName)
                name.takeIf(String::isNotBlank)?.let {
                    it to patch.copy(stage = patch.stage?.let(::short))
                }
            }
            .take(MAX_PLAYER_COLLECTION_ITEMS)
            .toMap()
        return changes.copy(
            inventory = changes.inventory
                .take(MAX_PLAYER_COLLECTION_ITEMS)
                .map { change ->
                    change.copy(
                        action = change.action.trim().lowercase().take(16),
                        item = short(change.item),
                        quantity = change.quantity.coerceIn(1, 9999)
                    )
                }
                .filter { change ->
                    change.action in setOf("add", "remove") && change.item.isNotBlank()
                },
            relationships = relationships,
            questLog = changes.questLog?.take(MAX_PLAYER_COLLECTION_ITEMS)?.map(::sanitizeQuest),
            storyProgress = changes.storyProgress?.let(::sanitizeStoryProgress),
            physicalState = changes.physicalState?.let(::sanitizePhysicalState),
            mentalState = changes.mentalState?.let(::sanitizeMentalState),
            attributes = sanitizeAttributes(changes.attributes),
            risk = changes.risk?.coerceIn(0, CharacterFieldPolicy.MAX_NUMERIC_VALUE)
        )
    }

    private fun sanitizeQuest(quest: Quest): Quest {
        return quest.copy(
            name = short(quest.name),
            status = short(quest.status),
            description = quest.description.trim().take(MAX_REASON_LENGTH),
            currentStep = quest.currentStep.trim().take(MAX_REASON_LENGTH)
        )
    }

    private fun sanitizeStoryProgress(progress: StoryProgressPatch): StoryProgressPatch {
        return progress.copy(
            currentArc = progress.currentArc?.let { it.trim().take(MAX_REASON_LENGTH) },
            currentChapter = progress.currentChapter?.let { it.trim().take(MAX_REASON_LENGTH) },
            currentScene = progress.currentScene?.let { it.trim().take(MAX_REASON_LENGTH) },
            currentGoal = progress.currentGoal?.let { it.trim().take(MAX_REASON_LENGTH) }
        )
    }

    private fun sanitizePhysicalState(state: PhysicalStatePatch): PhysicalStatePatch {
        return state.copy(
            health = state.health?.let(::short),
            injuries = state.injuries
                ?.take(MAX_INJURIES)
                ?.map { injury -> injury.trim().take(MAX_REASON_LENGTH) }
                ?.filter(String::isNotBlank),
            fatigue = state.fatigue?.let(::short),
            hunger = state.hunger?.let(::short),
            pain = state.pain?.let(::short)
        )
    }

    private fun sanitizeMentalState(state: MentalStatePatch): MentalStatePatch {
        return state.copy(
            mood = state.mood?.let { it.trim().take(MAX_PROFILE_LENGTH) },
            stress = state.stress?.let(::short),
            focus = state.focus?.let { it.trim().take(MAX_PROFILE_LENGTH) },
            currentThoughts = state.currentThoughts?.let { it.trim().take(MAX_PROFILE_LENGTH) }
        )
    }

    private fun sanitizeAttributes(values: Map<String, String>): Map<String, String> {
        return values.entries.asSequence()
            .mapNotNull { (rawKey, rawValue) ->
                val key = rawKey.trim().lowercase().take(40)
                val value = rawValue.trim().take(CharacterFieldPolicy.MAX_VALUE_LENGTH)
                if (!CharacterFieldPolicy.isValidKey(key) || value.isBlank()) null else key to value
            }
            .take(CharacterFieldPolicy.MAX_FIELDS)
            .toMap()
    }

    private fun normalizeParticipant(rawName: String, playerName: String): String {
        val name = short(rawName)
        return if (name.equals(playerName, ignoreCase = true) || name.lowercase() in playerAliases) {
            playerName
        } else {
            name
        }
    }

    private fun short(value: String): String = value.trim().take(MAX_SHORT_LENGTH)

    private val playerAliases = setOf("主角", "玩家", "我", "player", "protagonist", "user")
}
