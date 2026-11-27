package com.novelchat.data.model

import com.novelchat.data.remote.model.StoryParseResult
import com.novelchat.domain.parser.JsonResponseParser
import com.novelchat.ui.viewmodel.ImportedCharacterPolicy
import com.novelchat.ui.viewmodel.ImportedOpeningPolicy

const val DEFAULT_STORY_RESOURCE_PATH = "stories/shendiao_v1.json"

const val DEFAULT_STORY_TEXT = """标题：神雕侠侣 · 终南烟雨

背景：南宋端平元年（公元1234年）深秋，金国初灭，蒙古铁骑南指。终南山全真教执道门牛耳，后山活死人墓隐世独居，赤练仙子李莫愁横行江南，北侠郭靖与黄蓉心系襄阳防务。

主角设定：玩家扮演十四岁的孤儿杨过，刚随郭靖上终南山重阳宫拜赵志敬为师三月，受尽冷眼与师兄鹿清笃刁难。

核心命线机制：若杨过偏离神雕原著主线，系统在自然剧情中安排最多 3 次主动回归引导；若继续偏离则彻底解绑进入「自创江湖」，不再受原著束缚，而江湖八大时代大事仍按时间线在背景中独立发生。"""

val defaultStoryTemplateJson: String by lazy {
    val stream = DefaultStoryResourceAnchor::class.java.classLoader
        ?.getResourceAsStream(DEFAULT_STORY_RESOURCE_PATH)
        ?: error("缺少内置默认剧本资源：$DEFAULT_STORY_RESOURCE_PATH")
    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private object DefaultStoryResourceAnchor

private val cachedDefaultParseResult: StoryParseResult by lazy {
    val parsed = JsonResponseParser().parseStoryParseResultStrict(defaultStoryTemplateJson)
    val opening = ImportedOpeningPolicy.normalize(
        opening = parsed.opening,
        worldbook = parsed.worldbook,
        npcs = parsed.npcs
    )
    parsed.copy(opening = opening)
}

fun isDefaultStoryText(text: String): Boolean {
    return text.trim() == DEFAULT_STORY_TEXT.trim()
}

fun defaultStoryParseResult(): StoryParseResult = cachedDefaultParseResult

fun defaultStoryWorldSetting(): WorldSetting {
    val ws = defaultStoryParseResult().worldSetting
    return WorldSetting(
        name = ws.name,
        genre = ws.genre,
        techLevel = ws.techLevel,
        magicLevel = ws.magicLevel,
        tone = ws.tone,
        forbidden = ws.forbidden,
        description = ws.description,
        lore = ws.lore,
        characterFields = ws.characterFields
    )
}

fun defaultStoryPlayerState(): PlayerState {
    val parsed = defaultStoryParseResult()
    val p = parsed.player
    val playerName = p.name.ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME }
    return PlayerState(
        name = playerName,
        age = p.age,
        gender = p.gender,
        race = p.race,
        class_ = p.class_,
        personality = p.personality,
        background = p.background,
        appearance = p.appearance,
        skills = p.skills.map { PlayerSkill(name = it, level = 1) },
        level = p.level,
        charm = p.charm,
        huntingSkill = p.actionSkill,
        socialRank = p.socialRank,
        luck = p.luck,
        alpha = p.willpower,
        resistance = p.resilience,
        lore = p.lore,
        physicalState = p.physicalState,
        mentalState = p.mentalState,
        customAttributes = p.customAttributes,
        inventory = p.inventory,
        storyProgress = StoryProgress(
            currentGoal = parsed.opening.pendingEvents.firstOrNull().orEmpty()
        )
    )
}

fun defaultStoryCharacters(createdAt: String): List<CharacterCard> {
    val parsed = defaultStoryParseResult()
    val playerName = parsed.player.name.ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME }
    return parsed.npcs.map { npc ->
        ImportedCharacterPolicy.createCard(
            npc = npc,
            playerName = playerName,
            createdAt = createdAt,
            characterFields = parsed.worldSetting.characterFields
        )
    }
}

fun defaultStoryWorldbookEntries(createdAt: String): List<WorldbookEntry> {
    val parsed = defaultStoryParseResult()
    val worldName = parsed.worldSetting.name
    val playerName = parsed.player.name.ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME }
    return parsed.worldbook.mapIndexed { idx, item ->
        val keywords = (item.keywords + listOf(item.name, worldName, playerName))
            .flatMap { it.split(',', '，', '、', '/', '|') }
            .map(String::trim)
            .filter { it.length >= 2 }
            .distinct()
            .take(12)
        WorldbookEntry(
            id = "wb_${idx + 1}",
            type = item.type,
            name = item.name,
            description = item.description,
            keywords = keywords,
            relatedCharacters = item.relatedCharacters,
            relatedEvents = item.relatedEvents,
            relatedLocations = item.relatedLocations,
            isSecret = item.isSecret,
            knownByPlayer = item.knownByPlayer,
            isActive = true,
            createdBy = "system",
            status = item.status,
            createdAt = createdAt,
            lastModified = createdAt
        )
    }
}

fun defaultStoryState(): StoryState {
    return ImportedOpeningPolicy.createStoryState(defaultStoryParseResult().opening)
}
