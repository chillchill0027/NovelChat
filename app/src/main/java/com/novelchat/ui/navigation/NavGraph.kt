package com.novelchat.ui.navigation

object Routes {
    const val WELCOME = "welcome"
    const val API_SETUP = "api_setup"
    const val STORY_IMPORT = "story_import"
    const val STORY_CONFIRM = "story_confirm"
    const val CHARACTER_CONFIRM = "character_confirm/{characterId}"
    const val LOBBY = "lobby"
    const val PRESET_SELECT = "preset_select"
    const val CHARACTER_CUSTOM = "character_custom"
    const val CHAT = "chat/{characterId}"
    const val WORLDBOOK = "worldbook/{characterId}"
    const val CHARACTERS = "characters/{characterId}"
    const val CHARACTER_DETAIL = "character_detail/{characterId}/{cardName}"

    fun characterConfirm(characterId: Long) = "character_confirm/$characterId"
    fun chat(characterId: Long) = "chat/$characterId"
    fun worldbook(characterId: Long) = "worldbook/$characterId"
    fun characters(characterId: Long) = "characters/$characterId"
    fun characterDetail(characterId: Long, cardReference: String) =
        "character_detail/$characterId/$cardReference"
}
