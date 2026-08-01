package com.novelchat.ui.viewmodel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.CharacterNamingPolicy
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

    val template: String = """
{
  "schema_version": 1,
  "world_setting": {
    "name": "海临市",
    "genre": "当代都市现实主义",
    "tech_level": "2026 年现实科技，不存在超前黑科技",
    "magic_level": "无魔法、无超能力",
    "tone": "克制、生活化、重视现实流程与人物反应",
    "forbidden": ["无因果反转", "替主角做重大决定", "用身份泛称代替角色姓名"],
    "description": "海临市是一座沿海省会。故事围绕普通人的工作、关系、金钱和选择展开，所有结果都受现实法律、时间与资源约束。",
    "lore": {
      "era": "2026 年春季",
      "premise": "主角林克刚搬进云桥里，试图在新工作和旧关系之间重建稳定生活。",
      "history": "海临市由港口工业城市转型为金融与数字服务中心，旧城区更新造成明显的代际与阶层冲突。",
      "geography": "北部为旧港和云桥里，中心区聚集写字楼与市政机构，南湾是新开发的住宅和商业区。",
      "cosmology": "现实世界，不存在可验证的超自然现象。",
      "society": "人口流动快，租房、就业与公共服务构成普通居民的主要压力。",
      "culture": "重视体面和人情，但年轻人更强调边界、契约与个人选择。",
      "politics": "遵循现实城市行政体系，公共机构有明确权限和办理流程。",
      "economy": "服务业主导，住房和生活成本较高；大额资金变动会触发银行、税务和合规核验。",
      "law": "遵循中国现行法律常识。兑奖、转账、合同、就医等事件必须呈现可信流程。",
      "religion": "宗教不构成日常生活的主导力量。",
      "languages": ["普通话", "海临本地方言"],
      "calendar": "公历；工作日与法定节假日影响机构营业时间。",
      "power_system": "没有超自然力量；影响力来自身份、资源、信息、专业能力与关系网络。",
      "technology_rules": "手机、支付、监控、交通和网络服务符合 2026 年现实能力，技术不能无成本解决冲突。",
      "factions": ["海临市公共机构", "本地商业网络", "旧城区居民群体"],
      "conflicts": ["旧城更新与居民利益", "职业机会与生活稳定", "亲密关系中的承诺与边界"],
      "rules": ["时间连续推进", "信息必须有可信来源", "行为产生现实成本", "角色只知道自己能够接触到的信息"],
      "themes": ["选择的代价", "普通人的尊严", "信任如何建立"],
      "narrative_style": "第三人称限知；自然现代中文；以具体动作、对话和后果推进，不写清单式剧情。",
      "custom": {
        "货币": "人民币",
        "叙事尺度": "以街区、工作场所和家庭关系为主"
      }
    },
    "character_fields": [
      {
        "key": "stamina",
        "label": "精力",
        "description": "继续工作、通勤和处理事务的可用精力",
        "group": "status",
        "value_type": "number",
        "min_value": 0,
        "max_value": 120,
        "default_value": "100",
        "unit": "点",
        "applies_to": "all",
        "display": "primary",
        "primary_for": "player",
        "update_mode": "dynamic",
        "order": 30
      },
      {
        "key": "personality",
        "label": "性格与行为特征",
        "description": "只在反复行为或重大选择形成稳定倾向后刷新",
        "group": "profile",
        "value_type": "text",
        "applies_to": "all",
        "display": "detail",
        "update_mode": "event",
        "order": 11
      },
      {
        "key": "background",
        "label": "重要经历",
        "description": "已经发生并会持续影响后续判断的重要经历",
        "group": "profile",
        "value_type": "text",
        "applies_to": "all",
        "display": "detail",
        "update_mode": "event",
        "order": 12
      },
      {
        "key": "life_stability",
        "label": "生活稳定度",
        "description": "由住处、工作、财务和关系的长期稳定情况共同决定",
        "group": "ability",
        "value_type": "number",
        "min_value": 0,
        "max_value": 100,
        "default_value": "35",
        "unit": "%",
        "applies_to": "all",
        "display": "detail",
        "update_mode": "event",
        "order": 28
      }
    ]
  },
  "player": {
    "name": "林克",
    "age": 27,
    "gender": "男",
    "race": "人类",
    "class": "产品经理",
    "personality": "观察细致，遇事先确认事实；对亲近的人有责任感，但不擅长直接表达压力。",
    "background": "林克在海临市工作三年，最近换岗并搬进云桥里。他有稳定收入，但储蓄有限，正在处理职业变化和长期关系的现实安排。",
    "appearance": "身高约一米八，短黑发，日常穿深色衬衫和轻便外套。",
    "skills": ["信息整理", "沟通协商", "基础财务常识"],
    "level": 3,
    "charm": 46,
    "action_skill": 41,
    "social_rank": "普通职员",
    "luck": 52,
    "willpower": 58,
    "resilience": 49,
    "custom_attributes": {
      "life_stability": "35"
    },
    "lore": {
      "aliases": ["阿克"],
      "role": "故事主角",
      "pronouns": "他",
      "birth_date": "1999-08-17",
      "nationality": "中国",
      "residence": "海临市云桥里 6 栋 1203",
      "education": "海临大学信息管理本科",
      "values": ["尊重承诺", "先核实再行动", "不牺牲他人换取便利"],
      "goals": ["在新岗位站稳", "与顾宁建立可执行的共同生活计划"],
      "motivations": ["获得可掌控的生活", "保护重要关系中的信任"],
      "fears": ["在压力下失去判断", "让亲近的人承担自己的后果"],
      "weaknesses": ["容易把情绪压成待办", "面对突然的巨额利益时缺少经验"],
      "secrets": ["尚未告诉顾宁自己曾拒绝一次外地晋升机会"],
      "habits": ["重要决定前记下事实和风险", "紧张时会反复确认手机通知"],
      "speech_style": "句子简短，先问关键事实；对熟人语气温和，不说空泛承诺。",
      "abilities": ["快速梳理复杂信息", "在正式场合保持冷静"],
      "knowledge": ["互联网产品流程", "基础合同和支付常识"],
      "boundaries": ["未经确认不替伴侣承诺", "拒绝违法或明显伤害他人的方案"],
      "character_arc": "从依赖控制感，逐步学会在不确定中诚实沟通并承担选择。",
      "relationship_notes": {
        "顾宁": "交往三年；双方认真讨论过同居，但对城市和职业安排仍有分歧。"
      },
      "custom": {
        "常用交通": "地铁与步行",
        "紧急联系人": "顾宁"
      }
    },
    "physical_state": {
      "stamina": 88,
      "health": "良好",
      "injuries": [],
      "fatigue": "轻微",
      "hunger": "不饿",
      "pain": "无"
    },
    "mental_state": {
      "mood": "谨慎期待",
      "stress": "中等",
      "focus": "完成搬家后的生活安排",
      "current_thoughts": "需要先把住处和工作节奏稳定下来。"
    },
    "inventory": [
      {
        "name": "手机",
        "quantity": 1,
        "type": "通讯工具",
        "description": "常用智能手机，已安装银行和城市服务应用。",
        "origin": "个人购买",
        "acquired_at": "故事开始前"
      }
    ]
  },
  "npcs": [
    {
      "name": "顾宁",
      "age": 26,
      "gender": "女",
      "race": "人类",
      "occupation": "建筑设计师",
      "personality": "清醒直接，愿意照顾人，但反感别人以保护为名替她决定。",
      "background": "顾宁在南湾一家设计事务所工作，近期项目进入高压交付阶段。",
      "appearance": "齐肩黑发，常穿便于通勤的浅色风衣。",
      "relationship_to_player": "交往三年的女友",
      "custom_attributes": {
        "life_stability": "44"
      },
      "lore": {
        "aliases": ["宁宁"],
        "role": "核心关系角色",
        "pronouns": "她",
        "residence": "南湾合租公寓",
        "education": "建筑学硕士",
        "values": ["坦诚", "职业自主", "承诺必须可执行"],
        "goals": ["完成文化中心项目", "确认两人是否适合共同生活"],
        "motivations": ["不重复父母长期回避冲突的关系模式"],
        "fears": ["被迫在事业和关系之间二选一"],
        "weaknesses": ["工作压力大时说话过于直接"],
        "secrets": ["事务所可能安排她去外地驻场三个月，她尚未决定是否接受"],
        "habits": ["思考时转动自动铅笔", "重要谈话前先确认对方是否有时间"],
        "speech_style": "表达直接、具体，不用反问逼迫对方；不满意时会要求给出时间和方案。",
        "abilities": ["空间设计", "项目协调", "识别含糊承诺"],
        "knowledge": ["建筑项目流程", "南湾开发区情况"],
        "boundaries": ["不接受替她辞职或拒绝项目", "不在工作现场讨论私人争执"],
        "character_arc": "学习在坚持自主的同时，把脆弱和需求说清楚。",
        "relationship_notes": {
          "林克": "信任基础良好；当前矛盾不是感情真假，而是共同生活计划缺少具体时间表。"
        },
        "custom": {
          "近期压力源": "文化中心项目交付"
        }
      },
      "physical_state": {
        "stamina": 72,
        "health": "良好",
        "injuries": [],
        "fatigue": "中等",
        "hunger": "未明确",
        "pain": "无"
      },
      "mental_state": {
        "mood": "克制",
        "stress": "较高",
        "focus": "项目交付和搬家安排",
        "current_thoughts": "希望林克给出具体计划，而不是安慰。"
      },
      "inventory": []
    }
  ],
  "worldbook": [
    {
      "type": "location",
      "name": "云桥里",
      "description": "建于二十年前的混合住宅小区，靠近旧港地铁站。6 栋一层有物业值班室，街对面是咖啡馆“折页”。",
      "keywords": ["云桥里", "6 栋", "旧港地铁站", "折页咖啡"],
      "related_characters": ["林克"],
      "related_events": ["林克搬家"],
      "related_locations": ["旧港地铁站", "折页咖啡"],
      "is_secret": false,
      "known_by_player": true,
      "status": "正常"
    },
    {
      "type": "organization",
      "name": "景序设计事务所",
      "description": "顾宁所在的建筑设计事务所，近期负责南湾文化中心项目，实行项目经理负责制。",
      "keywords": ["景序", "设计事务所", "顾宁公司", "文化中心项目"],
      "related_characters": ["顾宁"],
      "related_events": ["文化中心交付"],
      "related_locations": ["南湾"],
      "is_secret": false,
      "known_by_player": true,
      "status": "项目高压期"
    },
    {
      "type": "event",
      "name": "共同生活计划",
      "description": "林克与顾宁已经确认认真考虑同居，但尚未决定入住日期、费用分担和通勤方案。",
      "keywords": ["同居", "搬家", "共同生活", "入住日期", "费用分担"],
      "related_characters": ["林克", "顾宁"],
      "related_events": [],
      "related_locations": ["云桥里"],
      "is_secret": false,
      "known_by_player": true,
      "status": "待讨论"
    }
  ],
  "opening": {
    "current_location": "云桥里 6 栋 1203",
    "time_of_day": "2026-03-16 09:20",
    "weather": "小雨，气温 12℃",
    "mood": "搬家刚结束后的安静与现实压力并存",
    "scene_description": "纸箱沿客厅墙边排开，水电已经接通。顾宁站在窗边查看去事务所的路线，林克刚把最后一把钥匙放到餐桌上。",
    "active_characters": ["顾宁"],
    "pending_events": ["林克需要和顾宁确认今天的安排，以及她是否留在云桥里过夜。"],
    "global_flags": {
      "搬家完成": "true",
      "共同生活计划": "尚未确认"
    }
  }
}
""".trimIndent()
}
