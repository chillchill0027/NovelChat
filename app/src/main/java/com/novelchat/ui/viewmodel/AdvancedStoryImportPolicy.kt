package com.novelchat.ui.viewmodel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.defaultStoryTemplateJson
import com.novelchat.data.remote.model.StoryParseResult
import com.novelchat.domain.parser.JsonResponseParser

enum class StoryImportMode {
    AI_RECOGNITION,
    ADVANCED_JSON
}

internal object AdvancedStoryImportPolicy {
    const val SCHEMA_VERSION = 1
    const val MAX_JSON_LENGTH = 80_000
    const val MAX_NPCS = StoryParseContentPolicy.MAX_NPCS
    const val MAX_WORLDBOOK_ENTRIES = StoryParseContentPolicy.MAX_WORLDBOOK_ENTRIES

    private val parser = JsonResponseParser()

    fun normalizeDraft(value: String): String = value.take(MAX_JSON_LENGTH)

    fun parse(raw: String): StoryParseResult {
        if (raw.isBlank()) throw IllegalArgumentException("请粘贴 JSON，或先载入模板")
        if (raw.length > MAX_JSON_LENGTH) {
            throw IllegalArgumentException("高级设定不能超过 $MAX_JSON_LENGTH 个字符")
        }

        val root = try {
            val element = JsonParser.parseString(raw.trim())
            if (!element.isJsonObject) {
                throw IllegalArgumentException("JSON 根节点必须是对象")
            }
            element.asJsonObject
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON 语法错误：${e.message.orEmpty()}", e)
        }

        validateVersion(root)
        requireObject(root, "world_setting", "worldSetting")
        requireObject(root, "player")
        optionalObject(root, "opening")
        optionalArray(root, "npcs")
        optionalArray(root, "worldbook")
        validateCharacterFieldShape(root)

        val npcCount = root.get("npcs")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        val worldbookCount = root.get("worldbook")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        if (npcCount > MAX_NPCS) {
            throw IllegalArgumentException("npcs 最多支持 $MAX_NPCS 个角色")
        }
        if (worldbookCount > MAX_WORLDBOOK_ENTRIES) {
            throw IllegalArgumentException(
                "worldbook 最多支持 $MAX_WORLDBOOK_ENTRIES 个条目"
            )
        }

        val parsed = try {
            parser.parseStoryParseResultStrict(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("字段解析失败：${e.message.orEmpty()}", e)
        }
        if (parsed.worldSetting.name.isBlank()) {
            throw IllegalArgumentException("world_setting.name 不能为空")
        }
        if (parsed.worldSetting.description.isBlank() && !parsed.worldSetting.lore.hasContent()) {
            throw IllegalArgumentException("请填写 world_setting.description 或 world_setting.lore")
        }
        if (parsed.player.name.isBlank()) {
            throw IllegalArgumentException("player.name 不能为空，且必须使用角色真实姓名")
        }
        if (CharacterNamingPolicy.isGenericPlayerName(parsed.player.name)) {
            throw IllegalArgumentException("player.name 必须是具体姓名，不能使用“${parsed.player.name}”")
        }
        parsed.npcs.firstOrNull { CharacterNamingPolicy.isGenericNpcName(it.name) }?.let { npc ->
            throw IllegalArgumentException(
                "npcs 中的“${npc.name.ifBlank { npc.occupation.ifBlank { "未命名角色" } }}”不是具体姓名"
            )
        }
        val playerName = parsed.player.name.trim()
        if (parsed.npcs.any { it.name.trim() == playerName }) {
            throw IllegalArgumentException("主角与 NPC 不能使用同一个姓名“$playerName”")
        }
        val duplicateNpc = parsed.npcs
            .map { it.name.trim() }
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicateNpc != null) {
            throw IllegalArgumentException("多个 NPC 使用了同一个姓名“$duplicateNpc”")
        }
        val npcNames = parsed.npcs.map { it.name.trim() }.toSet()
        parsed.opening.activeCharacters.firstOrNull { name ->
            name.trim() !in npcNames
        }?.let { unknownName ->
            throw IllegalArgumentException(
                "opening.active_characters 中的“$unknownName”未在 npcs 中定义"
            )
        }
        validateCharacterFields(parsed)
        return parsed
    }

    fun summary(result: StoryParseResult): String {
        val ruleCount = result.worldSetting.lore.rules.size
        return buildString {
            append(result.worldSetting.name)
            append(" · 1 位主角")
            append(" · ${result.npcs.size} 位 NPC")
            append(" · ${result.worldbook.size} 条世界书")
            append(" · ${CharacterFieldPolicy.effectiveDefinitions(result.worldSetting.characterFields).size} 个角色字段")
            if (ruleCount > 0) append(" · $ruleCount 条世界规则")
        }
    }

    private fun validateCharacterFieldShape(root: JsonObject) {
        val world = root.get("world_setting") ?: root.get("worldSetting") ?: return
        if (!world.isJsonObject) return
        val fields = world.asJsonObject.get("character_fields")
            ?: world.asJsonObject.get("characterFields")
            ?: return
        if (!fields.isJsonArray) {
            throw IllegalArgumentException("world_setting.character_fields 必须是数组")
        }
        if (fields.asJsonArray.size() > CharacterFieldPolicy.MAX_FIELDS) {
            throw IllegalArgumentException(
                "character_fields 最多支持 ${CharacterFieldPolicy.MAX_FIELDS} 个字段"
            )
        }
        fields.asJsonArray.forEachIndexed { index, element ->
            if (!element.isJsonObject) {
                throw IllegalArgumentException("character_fields[$index] 必须是对象")
            }
            val field = element.asJsonObject
            val path = "character_fields[$index]"
            val key = field.get("key")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val label = field.get("label")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (!CharacterFieldPolicy.isValidKey(key)) {
                throw IllegalArgumentException("$path.key 只能使用小写字母、数字和下划线，且必须以字母开头")
            }
            if (label.isBlank()) throw IllegalArgumentException("$path.label 不能为空")
            validateEnum(field, path, "value_type", setOf("number", "text", "choice", "list"))
            validateEnum(field, path, "group", setOf("identity", "profile", "ability", "status", "relationship"))
            validateEnum(field, path, "applies_to", setOf("all", "player", "npc"))
            validateEnum(field, path, "display", setOf("primary", "detail", "hidden"))
            validateEnum(field, path, "primary_for", setOf("all", "player", "npc"))
            validateEnum(field, path, "update_mode", setOf("dynamic", "event", "manual"))
        }
    }

    private fun validateEnum(
        field: JsonObject,
        path: String,
        name: String,
        supported: Set<String>
    ) {
        val value = field.get(name) ?: return
        if (!value.isJsonPrimitive || value.asString !in supported) {
            throw IllegalArgumentException("$path.$name 不受支持，可选值：${supported.joinToString()}")
        }
    }

    private fun validateCharacterFields(result: StoryParseResult) {
        val definitions = result.worldSetting.characterFields
        val duplicate = definitions.map { it.key.trim().lowercase() }
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicate != null) {
            throw IllegalArgumentException("character_fields 中重复定义了 key=$duplicate")
        }
        definitions.forEachIndexed { index, field ->
            val path = "character_fields[$index]"
            if (!CharacterFieldPolicy.isValidKey(field.key)) {
                throw IllegalArgumentException("$path.key 只能使用小写字母、数字和下划线，且必须以字母开头")
            }
            if (field.label.isBlank()) throw IllegalArgumentException("$path.label 不能为空")
            if (field.valueType !in setOf(
                    CharacterFieldDefinition.TYPE_NUMBER,
                    CharacterFieldDefinition.TYPE_TEXT,
                    CharacterFieldDefinition.TYPE_CHOICE,
                    CharacterFieldDefinition.TYPE_LIST
                )
            ) {
                throw IllegalArgumentException("$path.value_type 不受支持")
            }
            if (field.valueType == CharacterFieldDefinition.TYPE_NUMBER && field.minValue >= field.maxValue) {
                throw IllegalArgumentException("$path 的 min_value 必须小于 max_value")
            }
            if (field.valueType == CharacterFieldDefinition.TYPE_CHOICE && field.options.isEmpty()) {
                throw IllegalArgumentException("$path 使用 choice 时必须提供 options")
            }
            if (field.key in numericBuiltIns && field.valueType != CharacterFieldDefinition.TYPE_NUMBER) {
                throw IllegalArgumentException("内置数值字段 ${field.key} 的 value_type 必须是 number")
            }
            if (field.key in CharacterFieldPolicy.builtInKeys || field.defaultValue.isBlank()) {
                return@forEachIndexed
            }
            if (CharacterFieldPolicy.sanitizeValue(field, field.defaultValue) == null) {
                throw IllegalArgumentException("$path.default_value 不符合字段类型或选项")
            }
        }

        val effective = CharacterFieldPolicy.effectiveDefinitions(definitions)
        val playerDefinitions = effective.filter {
            CharacterFieldPolicy.appliesTo(it, CharacterFieldDefinition.TARGET_PLAYER) &&
                CharacterFieldPolicy.usesCustomStorage(it.key, CharacterFieldDefinition.TARGET_PLAYER)
        }.associateBy { it.key }
        val npcDefinitions = effective.filter {
            CharacterFieldPolicy.appliesTo(it, CharacterFieldDefinition.TARGET_NPC) &&
                CharacterFieldPolicy.usesCustomStorage(it.key, CharacterFieldDefinition.TARGET_NPC)
        }.associateBy { it.key }
        validateAttributeValues("player.custom_attributes", result.player.customAttributes, playerDefinitions)
        result.npcs.forEachIndexed { index, npc ->
            validateAttributeValues("npcs[$index].custom_attributes", npc.customAttributes, npcDefinitions)
        }
    }

    private fun validateAttributeValues(
        path: String,
        values: Map<String, String>,
        definitions: Map<String, CharacterFieldDefinition>
    ) {
        values.forEach { (key, value) ->
            val field = definitions[key]
                ?: throw IllegalArgumentException("$path.$key 没有对应的 character_fields 定义")
            if (CharacterFieldPolicy.sanitizeValue(field, value) == null) {
                throw IllegalArgumentException("$path.$key 的值不符合字段类型、范围或 options")
            }
        }
    }

    private val numericBuiltIns = setOf(
        "age", "level", "charm", "action_skill", "luck", "willpower", "resilience",
        "action_tendency", "defense_threshold", "stamina", "risk", "favor", "trust"
    )

    private fun validateVersion(root: JsonObject) {
        val element = root.get("schema_version") ?: root.get("schemaVersion") ?: return
        val version = runCatching { element.asString.toInt() }.getOrNull()
            ?: throw IllegalArgumentException("schema_version 必须是整数")
        if (version != SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "不支持 schema_version=$version，当前仅支持 $SCHEMA_VERSION"
            )
        }
    }

    private fun requireObject(root: JsonObject, canonical: String, alias: String? = null) {
        val element = root.get(canonical) ?: alias?.let(root::get)
            ?: throw IllegalArgumentException("缺少必填对象：$canonical")
        if (!element.isJsonObject) {
            throw IllegalArgumentException("$canonical 必须是 JSON 对象")
        }
    }

    private fun optionalObject(root: JsonObject, name: String) {
        val element = root.get(name) ?: return
        if (!element.isJsonObject) throw IllegalArgumentException("$name 必须是 JSON 对象")
    }

    private fun optionalArray(root: JsonObject, name: String) {
        val element = root.get(name) ?: return
        if (!element.isJsonArray) throw IllegalArgumentException("$name 必须是 JSON 数组")
    }

    val template: String
        get() = defaultStoryTemplateJson
}
