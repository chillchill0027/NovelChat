package com.novelchat.domain.engine

import com.novelchat.data.model.*

/**
 * 游戏事件（对齐设计文档 13.1 节 UI刷新时机）
 */
sealed class GameEvent {
    data class Loading(val message: String) : GameEvent()
    data class NarrativeChunk(val chunk: String) : GameEvent()
    data class NarrativeComplete(val narrative: String) : GameEvent()
    data class Saved(val message: String) : GameEvent()
    data class TaskComplete(val taskAssignment: TaskAssignment) : GameEvent()

    data class PlayerUpdated(val player: PlayerState) : GameEvent()
    data class CharacterCreated(val card: CharacterCard) : GameEvent()
    data class CharacterRetired(val cardName: String) : GameEvent()
    data class CharacterUpdated(val card: CharacterCard) : GameEvent()

    data class WorldbookUpdated(val entry: WorldbookEntry) : GameEvent()
    data class WorldbookDeleted(val entryId: String) : GameEvent()
    data class ItemTransferred(val transfer: ItemTransfer) : GameEvent()
    data class EventRecorded(val eventType: String, val target: String) : GameEvent()

    data class RoundComplete(val round: Int) : GameEvent()
    data class Warning(val message: String) : GameEvent()
    data class Error(val message: String) : GameEvent()
}
