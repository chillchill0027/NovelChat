package com.novelchat.domain.parser

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.CharacterStatePatch
import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.NarrativeResult
import com.novelchat.data.model.NarrativeResponseRecovery
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.model.TaskAssignment
import com.novelchat.data.model.WorldLore
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedOpening
import com.novelchat.data.remote.model.ParsedPlayer
import com.novelchat.data.remote.model.ParsedWorldSetting
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult

enum class TaskAssignmentParseFailure {
    NO_JSON,
    ROOT_SHAPE,
    MISSING_FIELDS,
    FIELD_SHAPE
}

class TaskAssignmentParseException(
    val failure: TaskAssignmentParseFailure,
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * AI 响应 JSON 解析器
 * 处理各种不稳定的 AI 输出格式，确保健壮性
 */
class JsonResponseParser {

    @PublishedApi internal val gson = Gson()

    /**
     * 从 AI 返回文本中提取并解析 NarrativeResult
     */
    fun parseNarrative(raw: String): NarrativeResult {
        val json = extractJson(raw)
        if (json != null) {
            try {
                val parsed = JsonParser.parseString(json)
                normalizeKnownAliases(parsed)
                removeExplicitNulls(parsed)
                return gson.fromJson(parsed, NarrativeResult::class.java)
            } catch (e: JsonSyntaxException) {
                // JSON 结构不对，尝试降级解析
            }
        }
        NarrativeResponseRecovery.recover(raw)?.let { return it }
        // 降级：无结构的纯正文
        return NarrativeResult(narrative = raw.trim(), summary = "")
    }

    /**
     * 从 AI 返回文本中提取并解析 TaskAssignment
     */
    fun parseTaskAssignment(raw: String): TaskAssignment {
        val json = extractJson(raw)
            ?: throw TaskAssignmentParseException(
                TaskAssignmentParseFailure.NO_JSON,
                "任务分配未返回有效 JSON"
            )
        return try {
            val parsed = JsonParser.parseString(json)
            normalizeKnownAliases(parsed)
            val root = taskPayload(parsed)
            removeExplicitNulls(root)
            if (taskFields.none(root::has)) {
                throw TaskAssignmentParseException(
                    TaskAssignmentParseFailure.MISSING_FIELDS,
                    "未包含任务分配字段"
                )
            }
            listOf(
                "new_characters",
                "retired_characters",
                "character_updates",
                "worldbook_updates",
                "item_transfers",
                "events"
            ).forEach { normalizeArray(root, it) }
            normalizeTaskFields(root)
            gson.fromJson(root, TaskAssignment::class.java) ?: TaskAssignment()
        } catch (e: TaskAssignmentParseException) {
            throw e
        } catch (e: Exception) {
            throw TaskAssignmentParseException(
                TaskAssignmentParseFailure.FIELD_SHAPE,
                "任务分配 JSON 字段结构无法归一化",
                e
            )
        }
    }

    fun parseCharacterStatePatch(raw: String): CharacterStatePatch {
        val json = extractJson(raw)
            ?: throw IllegalArgumentException("角色状态未返回有效 JSON")
        return try {
            val parsed = JsonParser.parseString(json)
            normalizeKnownAliases(parsed)
            val root = when {
                parsed.isJsonObject -> parsed.asJsonObject
                parsed.isJsonArray && parsed.asJsonArray.size() > 0 -> parsed.asJsonArray[0].asJsonObject
                else -> throw IllegalArgumentException("角色状态根节点不是对象")
            }
            val payload = root.get("character_update")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: root
            removeExplicitNulls(payload)
            normalizeArray(payload, "inventory_changes")
            payload.get("physical_state")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                normalizeInt(it, "stamina", 0, 100)
            }
            payload.get("relationship_to_player")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                normalizeInt(it, "favor_delta", -100, 100)
                normalizeInt(it, "trust_delta", -100, 100)
            }
            payload.get("inventory_changes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let {
                    normalizeInt(it, "quantity", 1, 9999)
                }
            }
            gson.fromJson(payload, CharacterStatePatch::class.java) ?: CharacterStatePatch()
        } catch (e: Exception) {
            throw IllegalArgumentException("角色状态 JSON 解析失败：${e.message}", e)
        }
    }

    fun parseStoryParseResult(raw: String): StoryParseResult {
        val json = extractJson(raw) ?: return StoryParseResult()
        return try {
            parseStoryParseResultStrict(json)
        } catch (_: Exception) {
            StoryParseResult()
        }
    }

    /**
     * Parses a user-authored story contract without silently falling back to an empty result.
     */
    fun parseStoryParseResultStrict(raw: String): StoryParseResult {
        val parsed = JsonParser.parseString(raw.trim())
        normalizeKnownAliases(parsed)
        if (!parsed.isJsonObject) {
            throw IllegalArgumentException("JSON 根节点必须是对象")
        }
        val root = parsed.asJsonObject
        return StoryParseResult(
            worldSetting = parseWorldSetting(root.obj("world_setting")),
            player = parsePlayer(root.obj("player")),
            npcs = root.array("npcs").mapNotNull { element ->
                element.asObjectOrNull()?.let { parseNpc(it) }
            },
            worldbook = root.array("worldbook").mapNotNull { element ->
                element.asObjectOrNull()?.let { parseWorldbookItem(it) }
            },
            opening = parseOpening(root.obj("opening"))
        )
    }

    /**
     * 从 AI 返回文本中提取并解析任意类型
     */
    @PublishedApi internal inline fun <reified T> parse(raw: String, default: T): T {
        val json = extractJson(raw) ?: return default
        return try {
            gson.fromJson(json, T::class.java) ?: default
        } catch (e: Exception) {
            default
        }
    }

    /**
     * 从 AI 返回文本中提取 JSON 字符串
     * 处理多种格式：
     * 1. 纯 JSON
     * 2. Markdown 代码块 ```json {...} ```
     * 3. 文本 + JSON 混合
     * 4. 不完整 JSON（尝试修复）
     */
    fun extractJson(response: String): String? {
        // 尝试直接解析
        val trimmed = response.trim()
        if (
            (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            parseableJson(trimmed)?.let { return it }
        }

        // 尝试从 Markdown 代码块提取
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\s*\\n?```")
        codeBlockPattern.findAll(response).toList().asReversed().forEach { match ->
            val candidate = match.groupValues[1].trim()
            parseableJson(candidate)?.let { return it }
        }

        // 模型偶尔会先输出示例、再输出最终对象。逐个扫描完整对象并优先取最后一个，
        // 避免把“第一个左括号到最后一个右括号”拼成无效 JSON。
        balancedJsonObjects(response).asReversed().forEach { candidate ->
            parseableJson(candidate)?.let { return it }
        }

        return null
    }

    private fun taskPayload(parsed: JsonElement): JsonObject {
        val root = when {
            parsed.isJsonObject -> parsed.asJsonObject
            parsed.isJsonArray && parsed.asJsonArray.firstObjectOrNull() != null ->
                parsed.asJsonArray.firstObjectOrNull()!!
            else -> throw TaskAssignmentParseException(
                TaskAssignmentParseFailure.ROOT_SHAPE,
                "任务分配根节点不是对象"
            )
        }
        return root.get("task_assignment")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: root
    }

    private fun removeExplicitNulls(element: JsonElement) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.entrySet().map { it.key }.forEach { key ->
                    val value = obj.get(key)
                    if (value == null || value.isJsonNull) {
                        obj.remove(key)
                    } else {
                        removeExplicitNulls(value)
                    }
                }
            }
            element.isJsonArray -> {
                val array = element.asJsonArray
                for (index in array.size() - 1 downTo 0) {
                    val value = array[index]
                    if (value.isJsonNull) {
                        array.remove(index)
                    } else {
                        removeExplicitNulls(value)
                    }
                }
            }
        }
    }

    private fun normalizeKnownAliases(element: JsonElement) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.entrySet().map { it.key }.forEach { key ->
                    val value = obj.get(key) ?: return@forEach
                    normalizeKnownAliases(value)
                    val canonical = jsonFieldAliases[key] ?: return@forEach
                    if (!obj.has(canonical)) obj.add(canonical, value)
                    obj.remove(key)
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach(::normalizeKnownAliases)
        }
    }

    private fun balancedJsonObjects(response: String): List<String> {
        val candidates = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        response.forEachIndexed { index, char ->
            if (start < 0) {
                if (char == '{') {
                    start = index
                    depth = 1
                    inString = false
                    escaped = false
                }
                return@forEachIndexed
            }

            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                return@forEachIndexed
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        candidates += response.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return candidates
    }

    private fun normalizeArray(root: JsonObject, field: String) {
        val value = root.get(field)
        when {
            value == null || value.isJsonNull -> root.add(field, JsonArray())
            value.isJsonObject -> root.add(field, JsonArray().apply { add(value) })
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                val normalized = JsonArray()
                normalizeStringArrayItem(field, value.asString)?.let(normalized::add)
                root.add(field, normalized)
            }
            value.isJsonArray -> {
                val normalized = JsonArray()
                value.asJsonArray.forEach { item ->
                    when {
                        item.isJsonObject -> normalized.add(item)
                        item.isJsonPrimitive && item.asJsonPrimitive.isString -> {
                            normalizeStringArrayItem(field, item.asString)?.let(normalized::add)
                        }
                    }
                }
                root.add(field, normalized)
            }
            else -> root.add(field, JsonArray())
        }
    }

    private fun normalizeStringArrayItem(field: String, raw: String): JsonObject? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return when (field) {
            "events" -> JsonObject().apply {
                addProperty("type", "剧情事件")
                addProperty("target", "")
                addProperty("caused_by", "")
                addProperty("time", "")
                addProperty("description", text)
            }
            "character_updates" -> {
                val (name, reason) = splitNameAndReason(text)
                name.takeIf { it.isNotBlank() }?.let {
                    JsonObject().apply {
                        addProperty("name", it)
                        addProperty("reason", reason)
                    }
                }
            }
            "retired_characters" -> JsonObject().apply {
                addProperty("name", text)
                addProperty("reason", "")
                addProperty("retired_at", "")
            }
            "quest_log" -> JsonObject().apply {
                addProperty("name", text)
                addProperty("status", "进行中")
                addProperty("description", text)
                addProperty("current_step", "")
            }
            else -> null
        }
    }

    private fun normalizeTaskFields(root: JsonObject) {
        normalizeText(root, "sync_status")
        normalizeText(root, "no_change_reason")

        objectField(root, "story_update")?.let { storyUpdate ->
            listOf("current_location", "time_of_day", "weather", "mood")
                .forEach { normalizeText(storyUpdate, it) }
            normalizeStringList(storyUpdate, "active_characters", "name")
            normalizeStringList(storyUpdate, "pending_events", "description")
        }

        objectField(root, "player_update")?.let { playerUpdate ->
            normalizeText(playerUpdate, "reason")
            val changes = objectField(playerUpdate, "changes")
                ?: moveFlatPlayerChanges(playerUpdate)
            changes?.let(::normalizePlayerChanges)
        }

        root.arrayObjects("new_characters").forEach { character ->
            normalizeInt(character, "age", 0, 999)
            listOf(
                "name", "gender", "race", "occupation", "personality", "background",
                "appearance", "relationship_to_player", "first_appearance"
            ).forEach { normalizeText(character, it) }
            // The task prompt explicitly forbids this large nested object. Dropping it also
            // prevents a single malformed optional card from invalidating the whole sync.
            character.remove("initial_status")
        }
        root.arrayObjects("retired_characters").forEach { retired ->
            listOf("name", "reason", "retired_at").forEach { normalizeText(retired, it) }
        }
        root.arrayObjects("character_updates").forEach { update ->
            listOf("name", "reason").forEach { normalizeText(update, it) }
        }
        root.arrayObjects("worldbook_updates").forEach { update ->
            listOf("action", "id", "name", "type", "description")
                .forEach { normalizeText(update, it) }
            normalizeStringList(update, "keywords")
            update.get("changes")?.takeUnless { it.isJsonObject }?.let { update.remove("changes") }
        }
        root.arrayObjects("item_transfers").forEach { transfer ->
            normalizeInt(transfer, "quantity", 1, 9999)
            listOf("item", "from", "to", "reason").forEach { normalizeText(transfer, it) }
        }
        root.arrayObjects("events").forEach { event ->
            listOf("type", "target", "caused_by", "time", "description")
                .forEach { normalizeText(event, it) }
        }
    }

    private fun normalizePlayerChanges(changes: JsonObject) {
        normalizeInt(changes, "risk", 0, 100)
        objectField(changes, "physical_state")?.let { physical ->
            normalizeInt(physical, "stamina", 0, 100)
            listOf("health", "fatigue", "hunger", "pain").forEach { normalizeText(physical, it) }
            normalizeStringList(physical, "injuries", "description")
        }
        objectField(changes, "mental_state")?.let { mental ->
            listOf("mood", "stress", "focus", "current_thoughts")
                .forEach { normalizeText(mental, it) }
        }
        objectField(changes, "story_progress")?.let { progress ->
            listOf("current_arc", "current_chapter", "current_scene", "current_goal")
                .forEach { normalizeText(progress, it) }
        }
        if (changes.has("inventory")) {
            normalizeArray(changes, "inventory")
            changes.arrayObjects("inventory").forEach { item ->
                normalizeInt(item, "quantity", 1, 9999)
                listOf("action", "item").forEach { normalizeText(item, it) }
            }
        }
        if (changes.has("quest_log")) {
            normalizeArray(changes, "quest_log")
            changes.arrayObjects("quest_log").forEach { quest ->
                listOf("name", "status", "description", "current_step")
                    .forEach { normalizeText(quest, it) }
            }
        }
        normalizeRelationships(changes)
    }

    private fun moveFlatPlayerChanges(playerUpdate: JsonObject): JsonObject? {
        val flatFields = listOf(
            "physical_state", "mental_state", "risk", "inventory", "relationships",
            "quest_log", "story_progress"
        )
        val present = flatFields.filter(playerUpdate::has)
        if (present.isEmpty()) return null
        return JsonObject().also { changes ->
            present.forEach { field ->
                changes.add(field, playerUpdate.remove(field))
            }
            playerUpdate.add("changes", changes)
        }
    }

    private fun objectField(root: JsonObject, field: String): JsonObject? {
        val value = root.get(field) ?: return null
        val normalized = when {
            value.isJsonObject -> value.asJsonObject
            value.isJsonArray -> value.asJsonArray.firstObjectOrNull()
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> runCatching {
                JsonParser.parseString(value.asString)
            }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            else -> null
        }
        if (normalized == null) {
            root.remove(field)
        } else if (normalized !== value) {
            root.add(field, normalized)
        }
        return normalized
    }

    private fun normalizeText(root: JsonObject, field: String) {
        val value = root.get(field) ?: return
        if (value.isJsonPrimitive) {
            root.addProperty(field, value.asJsonPrimitive.asString.trim())
        } else {
            root.remove(field)
        }
    }

    private fun normalizeStringList(root: JsonObject, field: String, objectField: String? = null) {
        val value = root.get(field) ?: return
        val values = when {
            value.isJsonArray -> value.asJsonArray.flatMap { item ->
                when {
                    item.isJsonPrimitive -> splitListText(item.asString)
                    item.isJsonObject && objectField != null -> item.asJsonObject.get(objectField)
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.let(::splitListText)
                        .orEmpty()
                    else -> emptyList()
                }
            }
            value.isJsonPrimitive -> splitListText(value.asString)
            value.isJsonObject && objectField != null -> value.asJsonObject.get(objectField)
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.let(::splitListText)
                .orEmpty()
            else -> emptyList()
        }
        root.add(field, JsonArray().apply {
            values.map(String::trim).filter(String::isNotBlank).distinct().forEach(::add)
        })
    }

    private fun JsonObject.arrayObjects(field: String): List<JsonObject> {
        return get(field)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .orEmpty()
    }

    private fun JsonArray.firstObjectOrNull(): JsonObject? {
        return firstOrNull { it.isJsonObject }?.asJsonObject
    }

    private fun normalizeRelationships(changes: JsonObject) {
        val relationships = changes.get("relationships") ?: return
        if (!relationships.isJsonObject) {
            changes.remove("relationships")
            return
        }
        val normalized = JsonObject()
        relationships.asJsonObject.entrySet().forEach { (name, value) ->
            when {
                value.isJsonObject -> normalized.add(name, value.asJsonObject.apply {
                    normalizeInt(this, "favor", -100, 100)
                    normalizeInt(this, "trust", 0, 100)
                })
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> normalized.add(
                    name,
                    JsonObject().apply {
                        addProperty("stage", value.asString)
                    }
                )
            }
        }
        changes.add("relationships", normalized)
    }

    private fun normalizeInt(root: JsonObject, field: String, min: Int, max: Int) {
        val value = root.get(field) ?: return
        val parsed = when {
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> runCatching { value.asInt }.getOrNull()
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> Regex("-?\\d+")
                .find(value.asString)?.value?.toIntOrNull()
            else -> null
        }
        if (parsed == null) {
            root.remove(field)
        } else {
            root.addProperty(field, parsed.coerceIn(min, max))
        }
    }

    private fun splitNameAndReason(text: String): Pair<String, String> {
        val parts = text.split(Regex("[:：—-]"), limit = 2)
        val rawName = parts.firstOrNull().orEmpty().trim()
        val name = rawName
            .removePrefix("更新")
            .removeSuffix("状态")
            .trim()
        val reason = parts.getOrNull(1)?.trim().orEmpty().ifBlank { text }
        return name to reason
    }

    private fun parseableJson(candidate: String): String? {
        if (candidate.isBlank()) return null
        if (runCatching { JsonParser.parseString(candidate) }.isSuccess) return candidate

        val cleaned = candidate
            .replace(Regex(""",\s*([}\]])""")) { it.groupValues[1] }
            .replace(Regex("//[^\\n]*"), "")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        return cleaned.takeIf { runCatching { JsonParser.parseString(it) }.isSuccess }
    }

    private companion object {
        val taskFields = setOf(
            "story_update",
            "player_update",
            "new_characters",
            "retired_characters",
            "character_updates",
            "worldbook_updates",
            "item_transfers",
            "events",
            "sync_status",
            "no_change_reason"
        )

        val jsonFieldAliases = mapOf(
            "taskAssignment" to "task_assignment",
            "characterUpdate" to "character_update",
            "storyUpdate" to "story_update",
            "playerUpdate" to "player_update",
            "newCharacters" to "new_characters",
            "retiredCharacters" to "retired_characters",
            "characterUpdates" to "character_updates",
            "worldbookUpdates" to "worldbook_updates",
            "itemTransfers" to "item_transfers",
            "syncStatus" to "sync_status",
            "noChangeReason" to "no_change_reason",
            "currentLocation" to "current_location",
            "timeOfDay" to "time_of_day",
            "activeCharacters" to "active_characters",
            "pendingEvents" to "pending_events",
            "physicalState" to "physical_state",
            "mentalState" to "mental_state",
            "questLog" to "quest_log",
            "storyProgress" to "story_progress",
            "currentArc" to "current_arc",
            "currentChapter" to "current_chapter",
            "currentScene" to "current_scene",
            "currentGoal" to "current_goal",
            "currentThoughts" to "current_thoughts",
            "relationshipToPlayer" to "relationship_to_player",
            "inventoryChanges" to "inventory_changes",
            "favorDelta" to "favor_delta",
            "trustDelta" to "trust_delta",
            "firstAppearance" to "first_appearance",
            "initialStatus" to "initial_status",
            "retiredAt" to "retired_at",
            "causedBy" to "caused_by",
            "actionCoverage" to "action_coverage",
            "forwardHook" to "forward_hook",
            "endingScene" to "ending_scene",
            "introducedCharacters" to "introduced_characters",
            "presentCharacters" to "present_characters",
            "worldSetting" to "world_setting",
            "techLevel" to "tech_level",
            "magicLevel" to "magic_level",
            "schemaVersion" to "schema_version",
            "sceneDescription" to "scene_description",
            "globalFlags" to "global_flags",
            "birthDate" to "birth_date",
            "speechStyle" to "speech_style",
            "characterArc" to "character_arc",
            "relationshipNotes" to "relationship_notes",
            "powerSystem" to "power_system",
            "technologyRules" to "technology_rules",
            "narrativeStyle" to "narrative_style",
            "relatedCharacters" to "related_characters",
            "relatedEvents" to "related_events",
            "relatedLocations" to "related_locations",
            "isSecret" to "is_secret",
            "knownByPlayer" to "known_by_player"
        )
    }

    private fun parseWorldSetting(obj: JsonObject): ParsedWorldSetting {
        return ParsedWorldSetting(
            name = obj.string("name"),
            genre = obj.string("genre"),
            techLevel = obj.string("tech_level"),
            magicLevel = obj.string("magic_level"),
            tone = obj.string("tone"),
            forbidden = obj.stringList("forbidden"),
            description = obj.string("description"),
            lore = parseWorldLore(obj.obj("lore"))
        )
    }

    private fun parsePlayer(obj: JsonObject): ParsedPlayer {
        return ParsedPlayer(
            name = obj.string("name"),
            age = obj.int("age", 20),
            gender = obj.string("gender"),
            race = obj.string("race"),
            class_ = obj.string("class"),
            personality = obj.string("personality"),
            background = obj.string("background"),
            appearance = obj.string("appearance"),
            skills = obj.stringList("skills"),
            lore = parseCharacterLore(obj.obj("lore")),
            physicalState = parsePhysicalState(obj.obj("physical_state")),
            mentalState = parseMentalState(obj.obj("mental_state")),
            inventory = parseInventory(obj.array("inventory"))
        )
    }

    private fun parseNpc(obj: JsonObject): ParsedNpc {
        return ParsedNpc(
            name = obj.string("name"),
            age = obj.int("age", 0),
            gender = obj.string("gender"),
            race = obj.string("race"),
            occupation = obj.string("occupation").ifBlank { obj.string("class") },
            personality = obj.string("personality"),
            background = obj.string("background").ifBlank { obj.string("description") },
            appearance = obj.string("appearance"),
            relationshipToPlayer = obj.string("relationship_to_player"),
            lore = parseCharacterLore(obj.obj("lore")),
            physicalState = parsePhysicalState(obj.obj("physical_state")),
            mentalState = parseMentalState(obj.obj("mental_state")),
            inventory = parseInventory(obj.array("inventory"))
        )
    }

    private fun parseWorldbookItem(obj: JsonObject): ParsedWorldbookItem {
        return ParsedWorldbookItem(
            type = obj.string("type"),
            name = obj.string("name"),
            description = obj.string("description"),
            keywords = obj.stringList("keywords"),
            relatedCharacters = obj.stringList("related_characters"),
            relatedEvents = obj.stringList("related_events"),
            relatedLocations = obj.stringList("related_locations"),
            isSecret = obj.bool("is_secret", false),
            knownByPlayer = obj.bool("known_by_player", true),
            status = obj.string("status").ifBlank { "正常" }
        )
    }

    private fun parseOpening(obj: JsonObject): ParsedOpening {
        return ParsedOpening(
            currentLocation = obj.string("current_location"),
            timeOfDay = obj.string("time_of_day"),
            weather = obj.string("weather"),
            mood = obj.string("mood"),
            sceneDescription = obj.string("scene_description"),
            activeCharacters = obj.stringList("active_characters"),
            pendingEvents = obj.stringList("pending_events"),
            globalFlags = obj.stringMap("global_flags")
        )
    }

    private fun parseWorldLore(obj: JsonObject): WorldLore {
        return WorldLore(
            era = obj.string("era"),
            premise = obj.string("premise"),
            history = obj.string("history"),
            geography = obj.string("geography"),
            cosmology = obj.string("cosmology"),
            society = obj.string("society"),
            culture = obj.string("culture"),
            politics = obj.string("politics"),
            economy = obj.string("economy"),
            law = obj.string("law"),
            religion = obj.string("religion"),
            languages = obj.stringList("languages"),
            calendar = obj.string("calendar"),
            powerSystem = obj.string("power_system"),
            technologyRules = obj.string("technology_rules"),
            factions = obj.stringList("factions"),
            conflicts = obj.stringList("conflicts"),
            rules = obj.stringList("rules"),
            themes = obj.stringList("themes"),
            narrativeStyle = obj.string("narrative_style"),
            custom = obj.stringMap("custom")
        )
    }

    private fun parseCharacterLore(obj: JsonObject): CharacterLore {
        return CharacterLore(
            aliases = obj.stringList("aliases"),
            role = obj.string("role"),
            pronouns = obj.string("pronouns"),
            birthDate = obj.string("birth_date"),
            nationality = obj.string("nationality"),
            residence = obj.string("residence"),
            education = obj.string("education"),
            values = obj.stringList("values"),
            goals = obj.stringList("goals"),
            motivations = obj.stringList("motivations"),
            fears = obj.stringList("fears"),
            weaknesses = obj.stringList("weaknesses"),
            secrets = obj.stringList("secrets"),
            habits = obj.stringList("habits"),
            speechStyle = obj.string("speech_style"),
            abilities = obj.stringList("abilities"),
            knowledge = obj.stringList("knowledge"),
            boundaries = obj.stringList("boundaries"),
            characterArc = obj.string("character_arc"),
            relationshipNotes = obj.stringMap("relationship_notes"),
            custom = obj.stringMap("custom")
        )
    }

    private fun parsePhysicalState(obj: JsonObject): PhysicalState {
        return PhysicalState(
            stamina = obj.int("stamina", 100).coerceIn(0, 100),
            health = obj.string("health").ifBlank { "良好" },
            injuries = obj.stringList("injuries"),
            fatigue = obj.string("fatigue").ifBlank { "无" },
            hunger = obj.string("hunger").ifBlank { "不饿" },
            pain = obj.string("pain").ifBlank { "无" }
        )
    }

    private fun parseMentalState(obj: JsonObject): MentalState {
        return MentalState(
            mood = obj.string("mood"),
            stress = obj.string("stress").ifBlank { "低" },
            focus = obj.string("focus"),
            currentThoughts = obj.string("current_thoughts")
        )
    }

    private fun parseInventory(elements: List<JsonElement>): List<InventoryItem> {
        return elements.mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            val name = obj.string("name")
            if (name.isBlank()) return@mapNotNull null
            InventoryItem(
                name = name,
                quantity = obj.int("quantity", 1).coerceIn(1, 9_999),
                type = obj.string("type"),
                description = obj.string("description"),
                origin = obj.string("origin"),
                acquiredAt = obj.string("acquired_at")
            )
        }
    }

    private fun JsonObject.obj(name: String): JsonObject {
        return get(name)?.asObjectOrNull() ?: JsonObject()
    }

    private fun JsonObject.array(name: String): List<JsonElement> {
        val element = get(name) ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.toList()
            element.isJsonObject -> listOf(element)
            else -> emptyList()
        }
    }

    private fun JsonObject.string(name: String): String {
        val element = get(name) ?: return ""
        return element.toPlainString()
    }

    private fun JsonObject.int(name: String, default: Int): Int {
        val raw = string(name)
        return raw.toIntOrNull() ?: Regex("""\d+""").find(raw)?.value?.toIntOrNull() ?: default
    }

    private fun JsonObject.bool(name: String, default: Boolean): Boolean {
        val raw = string(name).lowercase()
        return when (raw) {
            "true", "1", "yes", "是" -> true
            "false", "0", "no", "否" -> false
            else -> default
        }
    }

    private fun JsonObject.stringList(name: String): List<String> {
        val element = get(name) ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                item.toPlainString().takeIf { it.isNotBlank() }
            }
            element.isJsonPrimitive -> splitListText(element.asString)
            else -> emptyList()
        }
    }

    private fun JsonObject.stringMap(name: String): Map<String, String> {
        val obj = get(name)?.asObjectOrNull() ?: return emptyMap()
        return obj.entrySet()
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.toPlainString()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .toMap()
    }

    private fun splitListText(text: String): List<String> {
        return text.split(',', '，', '、', ';', '；', '/', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonElement.toPlainString(): String {
        return when {
            isJsonNull -> ""
            isJsonPrimitive -> asJsonPrimitive.asString.trim()
            else -> toString()
        }
    }
}
