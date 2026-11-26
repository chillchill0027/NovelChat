package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

/** A story-owned definition for one player or NPC card field. */
data class CharacterFieldDefinition(
    @SerializedName("key") val key: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("group") val group: String = GROUP_STATUS,
    @SerializedName("value_type") val valueType: String = TYPE_TEXT,
    @SerializedName("min_value") val minValue: Int = 0,
    @SerializedName("max_value") val maxValue: Int = 100,
    @SerializedName("options") val options: List<String> = emptyList(),
    @SerializedName("default_value") val defaultValue: String = "",
    @SerializedName("unit") val unit: String = "",
    @SerializedName("applies_to") val appliesTo: String = TARGET_ALL,
    @SerializedName("display") val display: String = DISPLAY_DETAIL,
    @SerializedName("primary_for") val primaryFor: String = TARGET_ALL,
    @SerializedName("update_mode") val updateMode: String = UPDATE_EVENT,
    @SerializedName("order") val order: Int = 100
) {
    companion object {
        const val TYPE_NUMBER = "number"
        const val TYPE_TEXT = "text"
        const val TYPE_CHOICE = "choice"
        const val TYPE_LIST = "list"

        const val GROUP_IDENTITY = "identity"
        const val GROUP_PROFILE = "profile"
        const val GROUP_ABILITY = "ability"
        const val GROUP_STATUS = "status"
        const val GROUP_RELATIONSHIP = "relationship"

        const val TARGET_ALL = "all"
        const val TARGET_PLAYER = "player"
        const val TARGET_NPC = "npc"

        const val DISPLAY_PRIMARY = "primary"
        const val DISPLAY_DETAIL = "detail"
        const val DISPLAY_HIDDEN = "hidden"

        const val UPDATE_DYNAMIC = "dynamic"
        const val UPDATE_EVENT = "event"
        const val UPDATE_MANUAL = "manual"
    }
}

object CharacterFieldPolicy {
    const val MAX_FIELDS = 40
    const val MAX_VALUE_LENGTH = 6_000
    const val MAX_SHORT_VALUE_LENGTH = 120
    const val MAX_NUMERIC_VALUE = 1_000_000

    private const val TYPE_NUMBER = CharacterFieldDefinition.TYPE_NUMBER
    private const val TYPE_TEXT = CharacterFieldDefinition.TYPE_TEXT
    private const val TYPE_CHOICE = CharacterFieldDefinition.TYPE_CHOICE
    private const val TYPE_LIST = CharacterFieldDefinition.TYPE_LIST
    private const val GROUP_IDENTITY = CharacterFieldDefinition.GROUP_IDENTITY
    private const val GROUP_PROFILE = CharacterFieldDefinition.GROUP_PROFILE
    private const val GROUP_ABILITY = CharacterFieldDefinition.GROUP_ABILITY
    private const val GROUP_STATUS = CharacterFieldDefinition.GROUP_STATUS
    private const val GROUP_RELATIONSHIP = CharacterFieldDefinition.GROUP_RELATIONSHIP
    private const val TARGET_ALL = CharacterFieldDefinition.TARGET_ALL
    private const val TARGET_PLAYER = CharacterFieldDefinition.TARGET_PLAYER
    private const val TARGET_NPC = CharacterFieldDefinition.TARGET_NPC
    private const val DISPLAY_PRIMARY = CharacterFieldDefinition.DISPLAY_PRIMARY
    private const val DISPLAY_DETAIL = CharacterFieldDefinition.DISPLAY_DETAIL
    private const val DISPLAY_HIDDEN = CharacterFieldDefinition.DISPLAY_HIDDEN
    private const val UPDATE_DYNAMIC = CharacterFieldDefinition.UPDATE_DYNAMIC
    private const val UPDATE_EVENT = CharacterFieldDefinition.UPDATE_EVENT
    private const val UPDATE_MANUAL = CharacterFieldDefinition.UPDATE_MANUAL

    private val keyPattern = Regex("^[a-z][a-z0-9_]{0,39}$")

    val builtInKeys: Set<String> = setOf(
        "name", "age", "gender", "race", "occupation", "appearance", "personality",
        "background", "skills", "level", "charm", "action_skill", "social_rank", "luck",
        "willpower", "resilience", "rarity", "action_tendency", "defense_threshold",
        "relationship_status", "hidden_clue", "stamina", "health", "fatigue", "hunger",
        "pain", "mood", "stress", "focus", "current_thoughts", "risk",
        "relationship_stage", "favor", "trust"
    )

    val defaultDefinitions: List<CharacterFieldDefinition> = listOf(
        field("name", "姓名", "角色在故事和对话中使用的真实姓名", GROUP_IDENTITY, update = UPDATE_MANUAL, display = DISPLAY_HIDDEN, order = 1),
        field("occupation", "身份", "职业、阶层、角色职能或故事身份", GROUP_IDENTITY, update = UPDATE_EVENT, order = 2),
        field("race", "种族", "角色所属种族或物种", GROUP_IDENTITY, update = UPDATE_EVENT, order = 3),
        number("age", "年龄", "角色当前年龄", GROUP_IDENTITY, 0, 120, "0", update = UPDATE_EVENT, order = 4),
        field("gender", "性别", "角色的性别描述", GROUP_IDENTITY, update = UPDATE_EVENT, order = 5),
        field("appearance", "外貌", "当前稳定外貌、衣着或明显身体特征", GROUP_PROFILE, update = UPDATE_EVENT, order = 10),
        field("personality", "人物特征", "由长期选择和行为逐步形成的性格特征", GROUP_PROFILE, update = UPDATE_EVENT, order = 11),
        field("background", "经历", "已经发生并持续影响角色的背景经历", GROUP_PROFILE, update = UPDATE_EVENT, order = 12),
        field("skills", "技能", "角色已经掌握的技能列表", GROUP_ABILITY, type = TYPE_LIST, update = UPDATE_EVENT, order = 20),
        number("level", "等级", "故事定义的综合成长等级", GROUP_ABILITY, 0, 9999, "1", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 21),
        number("charm", "魅力", "影响说服、第一印象与社交反馈的能力", GROUP_ABILITY, 0, 100, "25", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 22),
        number("action_skill", "行动技巧", "执行复杂、体力或技术行动的综合能力", GROUP_ABILITY, 0, 100, "10", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 23),
        field("social_rank", "社交等级", "角色在当前社会体系中的位置", GROUP_ABILITY, default = "E", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 24),
        number("willpower", "意志", "抵抗诱惑、压力与精神冲击的能力", GROUP_ABILITY, 0, 100, "15", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 25),
        number("resilience", "体魄抗性", "承受伤病、疲劳与环境影响的能力", GROUP_ABILITY, 0, 100, "20", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 26),
        number("luck", "运气", "故事规则允许的偶然性倾向", GROUP_ABILITY, 0, 100, "50", applies = TARGET_PLAYER, update = UPDATE_EVENT, order = 27),
        number("stamina", "体力", "角色继续行动所需的可用精力", GROUP_STATUS, 0, 100, "100", display = DISPLAY_PRIMARY, primary = TARGET_PLAYER, update = UPDATE_DYNAMIC, order = 30),
        field("stress", "压力", "当前心理负荷", GROUP_STATUS, default = "低", display = DISPLAY_PRIMARY, primary = TARGET_PLAYER, update = UPDATE_DYNAMIC, order = 31),
        number("risk", "风险", "主角当前处境的综合风险", GROUP_STATUS, 0, 100, "0", applies = TARGET_PLAYER, display = DISPLAY_PRIMARY, primary = TARGET_PLAYER, update = UPDATE_DYNAMIC, order = 32),
        field("health", "健康", "身体整体健康状况", GROUP_STATUS, default = "良好", update = UPDATE_DYNAMIC, order = 33),
        field("fatigue", "疲劳", "持续行动造成的疲劳程度", GROUP_STATUS, default = "无", update = UPDATE_DYNAMIC, order = 34),
        field("hunger", "饥饿", "进食需求", GROUP_STATUS, default = "不饿", update = UPDATE_DYNAMIC, order = 35),
        field("mood", "情绪", "当前可观察到的情绪状态", GROUP_STATUS, default = "平静", update = UPDATE_DYNAMIC, order = 36),
        field("pain", "疼痛", "身体疼痛程度", GROUP_STATUS, default = "无", update = UPDATE_DYNAMIC, order = 37),
        field("focus", "当前关注", "角色此刻优先关注的事情", GROUP_STATUS, update = UPDATE_DYNAMIC, order = 38),
        field("current_thoughts", "当前想法", "角色当前明确形成的想法", GROUP_STATUS, update = UPDATE_DYNAMIC, display = DISPLAY_HIDDEN, order = 39),
        field("relationship_stage", "关系阶段", "NPC 与主角当前的关系阶段", GROUP_RELATIONSHIP, applies = TARGET_NPC, update = UPDATE_DYNAMIC, order = 40),
        number("favor", "好感", "NPC 对主角的总体好感", GROUP_RELATIONSHIP, -100, 100, "0", applies = TARGET_NPC, update = UPDATE_DYNAMIC, order = 41),
        number("trust", "信任", "NPC 对主角的信任程度", GROUP_RELATIONSHIP, 0, 100, "0", applies = TARGET_NPC, update = UPDATE_DYNAMIC, order = 42),
        field("rarity", "稀有度", "故事规则中的角色稀有度", GROUP_PROFILE, default = "普通", applies = TARGET_NPC, update = UPDATE_EVENT, display = DISPLAY_HIDDEN, order = 80),
        number("action_tendency", "行动倾向", "故事定义的行动主动程度", GROUP_ABILITY, 0, 100, "50", applies = TARGET_NPC, update = UPDATE_EVENT, display = DISPLAY_HIDDEN, order = 81),
        number("defense_threshold", "防御阈值", "故事定义的防御或警戒倾向", GROUP_ABILITY, 0, 100, "50", applies = TARGET_NPC, update = UPDATE_EVENT, display = DISPLAY_HIDDEN, order = 82),
        field("relationship_status", "关系状态", "角色当前公开关系状态", GROUP_PROFILE, default = "单身", applies = TARGET_NPC, update = UPDATE_EVENT, display = DISPLAY_HIDDEN, order = 83),
        field("hidden_clue", "隐藏线索", "仅供故事状态使用的角色线索", GROUP_PROFILE, applies = TARGET_NPC, update = UPDATE_EVENT, display = DISPLAY_HIDDEN, order = 84)
    )

    fun isValidKey(value: String): Boolean = keyPattern.matches(value.trim())

    fun normalizeDefinitions(configured: List<CharacterFieldDefinition>): List<CharacterFieldDefinition> {
        return configured.asSequence()
            .map(::normalizeDefinition)
            .filter { it.key.isNotBlank() && it.label.isNotBlank() }
            .distinctBy { it.key }
            .take(MAX_FIELDS)
            .toList()
    }

    fun effectiveDefinitions(configured: List<CharacterFieldDefinition>): List<CharacterFieldDefinition> {
        if (configured.isEmpty()) return defaultDefinitions
        val normalized = normalizeDefinitions(configured)
        val overrides = normalized.associateBy { it.key }
        val defaults = defaultDefinitions.map { default -> overrides[default.key] ?: default }
        val additions = normalized.filter { it.key !in builtInKeys }
        return (defaults + additions).sortedWith(compareBy(CharacterFieldDefinition::order, CharacterFieldDefinition::key))
    }

    fun definitionFor(configured: List<CharacterFieldDefinition>, key: String): CharacterFieldDefinition? {
        return effectiveDefinitions(configured).firstOrNull { it.key == key }
    }

    fun appliesTo(field: CharacterFieldDefinition, target: String): Boolean {
        return field.appliesTo == CharacterFieldDefinition.TARGET_ALL || field.appliesTo == target
    }

    fun isPrimaryFor(field: CharacterFieldDefinition, target: String): Boolean {
        return field.display == CharacterFieldDefinition.DISPLAY_PRIMARY &&
            (field.primaryFor == CharacterFieldDefinition.TARGET_ALL || field.primaryFor == target)
    }

    fun normalizeImportedAttributes(
        values: Map<String, String>,
        configured: List<CharacterFieldDefinition>,
        target: String
    ): Map<String, String> {
        val definitions = effectiveDefinitions(configured)
            .filter { usesCustomStorage(it.key, target) && appliesTo(it, target) }
        return buildMap {
            definitions.forEach { field ->
                sanitizeValue(field, values[field.key] ?: field.defaultValue)?.let { put(field.key, it) }
            }
        }
    }

    fun mergeCustomAttributes(
        current: Map<String, String>,
        patch: Map<String, String>,
        configured: List<CharacterFieldDefinition>,
        target: String
    ): Map<String, String> {
        if (patch.isEmpty()) return current
        val allowed = effectiveDefinitions(configured)
            .filter { usesCustomStorage(it.key, target) && appliesTo(it, target) }
            .associateBy { it.key }
        return current.toMutableMap().apply {
            patch.forEach { (rawKey, rawValue) ->
                val key = rawKey.trim()
                val field = allowed[key] ?: return@forEach
                sanitizeValue(field, rawValue)?.let { put(key, it) }
            }
        }
    }

    fun sanitizeValue(field: CharacterFieldDefinition, rawValue: String): String? {
        val value = rawValue.trim().take(MAX_VALUE_LENGTH)
        if (value.isBlank()) return null
        return when (field.valueType) {
            CharacterFieldDefinition.TYPE_NUMBER -> {
                val number = value.toDoubleOrNull()?.toInt() ?: return null
                number.coerceIn(field.minValue, field.maxValue).toString()
            }
            CharacterFieldDefinition.TYPE_CHOICE -> value.takeIf {
                field.options.isEmpty() || it in field.options
            }
            CharacterFieldDefinition.TYPE_LIST -> value
                .split(',', '，', '、', ';', '；', '|')
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(30)
                .joinToString("、")
                .takeIf(String::isNotBlank)
            else -> value
        }
    }

    fun valueFor(field: CharacterFieldDefinition, player: PlayerState): String? {
        if (!appliesTo(field, CharacterFieldDefinition.TARGET_PLAYER)) return null
        return when (field.key) {
            "name" -> player.name
            "age" -> player.age.toString()
            "gender" -> player.gender
            "race" -> player.race
            "occupation" -> player.class_
            "appearance" -> player.appearance
            "personality" -> player.personality
            "background" -> player.background
            "skills" -> player.skills.joinToString("、") { it.name }
            "level" -> player.level.toString()
            "charm" -> player.charm.toString()
            "action_skill" -> player.huntingSkill.toString()
            "social_rank" -> player.socialRank
            "luck" -> player.luck.toString()
            "willpower" -> player.alpha.toString()
            "resilience" -> player.resistance.toString()
            "stamina" -> player.physicalState.stamina.toString()
            "health" -> player.physicalState.health
            "fatigue" -> player.physicalState.fatigue
            "hunger" -> player.physicalState.hunger
            "pain" -> player.physicalState.pain
            "mood" -> player.mentalState.mood.ifBlank { field.defaultValue }
            "stress" -> player.mentalState.stress
            "focus" -> player.mentalState.focus
            "current_thoughts" -> player.mentalState.currentThoughts
            "risk" -> player.cv.toString()
            else -> player.customAttributes[field.key] ?: field.defaultValue
        }.takeIf { it.isNotBlank() }
    }

    fun valueFor(
        field: CharacterFieldDefinition,
        card: CharacterCard,
        playerName: String = ""
    ): String? {
        if (!appliesTo(field, CharacterFieldDefinition.TARGET_NPC)) return null
        val relationship = card.relationships[playerName]
        return when (field.key) {
            "name" -> card.name
            "age" -> card.age.takeIf { it > 0 }?.toString().orEmpty()
            "gender" -> card.gender
            "race" -> card.race
            "occupation" -> card.occupation
            "appearance" -> card.appearance
            "personality" -> card.personality
            "background" -> card.background
            "rarity" -> card.rarity
            "action_tendency" -> card.libido.toString()
            "defense_threshold" -> card.threshold.toString()
            "relationship_status" -> card.relationshipStatus
            "hidden_clue" -> card.kink
            "stamina" -> card.physicalState.stamina.toString()
            "health" -> card.physicalState.health
            "fatigue" -> card.physicalState.fatigue
            "hunger" -> card.physicalState.hunger
            "pain" -> card.physicalState.pain
            "mood" -> card.emotion.ifBlank { card.mentalState.mood.ifBlank { field.defaultValue } }
            "stress" -> card.mentalState.stress
            "focus" -> card.mentalState.focus
            "current_thoughts" -> card.mentalState.currentThoughts
            "relationship_stage" -> relationship?.stage ?: card.relationshipStatus
            "favor" -> (relationship?.favor ?: card.favor).toString()
            "trust" -> relationship?.trust?.toString()
            "risk", "skills", "level", "charm", "action_skill", "social_rank", "luck", "willpower", "resilience" ->
                card.customAttributes[field.key] ?: field.defaultValue
            else -> card.customAttributes[field.key] ?: field.defaultValue
        }?.takeIf { it.isNotBlank() }
    }

    fun formatValue(field: CharacterFieldDefinition, value: String, compact: Boolean): String {
        if (field.valueType != CharacterFieldDefinition.TYPE_NUMBER) {
            return value + field.unit
        }
        if (field.key == "age") {
            return if (field.unit.isNotBlank()) value + field.unit else "${value}岁"
        }
        if (field.key == "level" || field.maxValue > 1_000) {
            return value + field.unit
        }
        if (field.unit == "%") {
            return "$value%"
        }
        return if (compact) {
            value + field.unit
        } else {
            "$value/${field.maxValue}${field.unit}"
        }
    }

    private fun normalizeDefinition(field: CharacterFieldDefinition): CharacterFieldDefinition {
        val key = field.key.trim().lowercase().take(40).takeIf(::isValidKey).orEmpty()
        val valueType = field.valueType.trim().lowercase().takeIf {
            it in setOf(TYPE_NUMBER, TYPE_TEXT, TYPE_CHOICE, TYPE_LIST)
        } ?: TYPE_TEXT
        val min = field.minValue.coerceIn(-MAX_NUMERIC_VALUE, MAX_NUMERIC_VALUE - 1)
        val max = field.maxValue.coerceIn(min + 1, MAX_NUMERIC_VALUE)
        val options = field.options.asSequence()
            .map { it.trim().take(MAX_SHORT_VALUE_LENGTH) }
            .filter(String::isNotBlank)
            .distinct()
            .take(30)
            .toList()
        return field.copy(
            key = key,
            label = field.label.trim().take(24),
            description = field.description.trim().take(240),
            group = field.group.trim().lowercase().takeIf {
                it in setOf(GROUP_IDENTITY, GROUP_PROFILE, GROUP_ABILITY, GROUP_STATUS, GROUP_RELATIONSHIP)
            } ?: GROUP_STATUS,
            valueType = valueType,
            minValue = min,
            maxValue = max,
            options = options,
            defaultValue = field.defaultValue.trim().take(MAX_VALUE_LENGTH),
            unit = field.unit.trim().take(12),
            appliesTo = field.appliesTo.trim().lowercase().takeIf {
                it in setOf(TARGET_ALL, TARGET_PLAYER, TARGET_NPC)
            } ?: TARGET_ALL,
            display = field.display.trim().lowercase().takeIf {
                it in setOf(DISPLAY_PRIMARY, DISPLAY_DETAIL, DISPLAY_HIDDEN)
            } ?: DISPLAY_DETAIL,
            primaryFor = field.primaryFor.trim().lowercase().takeIf {
                it in setOf(TARGET_ALL, TARGET_PLAYER, TARGET_NPC)
            } ?: TARGET_ALL,
            updateMode = field.updateMode.trim().lowercase().takeIf {
                it in setOf(UPDATE_DYNAMIC, UPDATE_EVENT, UPDATE_MANUAL)
            } ?: UPDATE_EVENT,
            order = field.order.coerceIn(0, 999)
        )
    }

    fun usesCustomStorage(key: String, target: String): Boolean {
        return when (target) {
            CharacterFieldDefinition.TARGET_PLAYER -> key !in playerNativeKeys
            CharacterFieldDefinition.TARGET_NPC -> key !in npcNativeKeys
            else -> key !in builtInKeys
        }
    }

    private val playerNativeKeys = setOf(
        "name", "age", "gender", "race", "occupation", "appearance", "personality",
        "background", "skills", "level", "charm", "action_skill", "social_rank", "luck",
        "willpower", "resilience", "stamina", "health", "fatigue", "hunger", "pain", "mood",
        "stress", "focus", "current_thoughts", "risk"
    )

    private val npcNativeKeys = setOf(
        "name", "age", "gender", "race", "occupation", "appearance", "personality", "background",
        "rarity", "action_tendency", "defense_threshold", "relationship_status", "hidden_clue",
        "stamina", "health", "fatigue", "hunger", "pain", "mood", "stress", "focus",
        "current_thoughts", "relationship_stage", "favor", "trust"
    )

    private fun field(
        key: String,
        label: String,
        description: String,
        group: String,
        type: String = TYPE_TEXT,
        default: String = "",
        applies: String = TARGET_ALL,
        display: String = DISPLAY_DETAIL,
        primary: String = TARGET_ALL,
        update: String,
        order: Int
    ) = CharacterFieldDefinition(
        key = key,
        label = label,
        description = description,
        group = group,
        valueType = type,
        defaultValue = default,
        appliesTo = applies,
        display = display,
        primaryFor = primary,
        updateMode = update,
        order = order
    )

    private fun number(
        key: String,
        label: String,
        description: String,
        group: String,
        min: Int,
        max: Int,
        default: String,
        applies: String = TARGET_ALL,
        display: String = DISPLAY_DETAIL,
        primary: String = TARGET_ALL,
        update: String,
        order: Int
    ) = field(key, label, description, group, TYPE_NUMBER, default, applies, display, primary, update, order)
        .copy(minValue = min, maxValue = max)

    private fun choice(
        key: String,
        label: String,
        description: String,
        group: String,
        options: List<String>,
        default: String,
        applies: String = TARGET_ALL,
        display: String = DISPLAY_DETAIL,
        primary: String = TARGET_ALL,
        update: String,
        order: Int
    ) = field(key, label, description, group, TYPE_CHOICE, default, applies, display, primary, update, order)
        .copy(options = options)
}
