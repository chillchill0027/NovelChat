package com.novelchat.data.model

/**
 * 玩家开局身份预设
 */
data class CharacterPreset(
    val key: String,
    val name: String,               // 身份名称
    val playerName: String,         // 角色姓名
    val description: String,        // 身份描述
    val charm: Int,                 // 魅力值
    val huntingSkill: Int,          // 行动技巧
    val socialRank: String,         // 社交等级 E~SSS
    val alpha: Int,                 // 意志气场
    val resistance: Int,            // 体魄抗性
    val cv: Int,                    // 风险值
    val luck: Int,                  // 运气值
    val systemEval: String          // 系统评价
)

val characterPresets = listOf(
    CharacterPreset(
        key = "renter", name = "城市租客", playerName = CharacterNamingPolicy.playerNameForPreset("renter"),
        description = "住在老小区，收入刚够覆盖房租和日常开销，每个选择都要考虑现金流",
        charm = 35, huntingSkill = 35, socialRank = "E",
        alpha = 30, resistance = 35, cv = 20, luck = 55,
        systemEval = "\"适合生存型开局。预算、通勤和人情往来会成为最真实的压力。\""
    ),
    CharacterPreset(
        key = "clerk", name = "职场新人", playerName = CharacterNamingPolicy.playerNameForPreset("clerk"),
        description = "刚进入近岸商务区的公司，熟悉流程但缺少话语权，机会和加班同时出现",
        charm = 45, huntingSkill = 40, socialRank = "D",
        alpha = 35, resistance = 30, cv = 12, luck = 50,
        systemEval = "\"适合职场型开局。你更容易接触同事、绩效和职业选择。\""
    ),
    CharacterPreset(
        key = "freelancer", name = "自由职业者", playerName = CharacterNamingPolicy.playerNameForPreset("freelancer"),
        description = "在共享办公室接项目，时间弹性更大，但收入、社保和客户关系都不稳定",
        charm = 65, huntingSkill = 55, socialRank = "C",
        alpha = 55, resistance = 40, cv = 8, luck = 55,
        systemEval = "\"适合社交型开局。你能自由安排路线，也要承担波动和失约成本。\""
    ),
    CharacterPreset(
        key = "resident", name = "社区医生", playerName = CharacterNamingPolicy.playerNameForPreset("resident"),
        description = "在社区医院轮班，能看到城市生活的细节，也经常被突发事件打断计划",
        charm = 70, huntingSkill = 75, socialRank = "B",
        alpha = 80, resistance = 65, cv = 6, luck = 45,
        systemEval = "\"适合责任型开局。你有专业能力，但时间、精力和情绪消耗很真实。\""
    ),
    CharacterPreset(
        key = "returnee", name = "返乡创业者", playerName = CharacterNamingPolicy.playerNameForPreset("returnee"),
        description = "带着积蓄和计划回到海临市，想做一件小生意，也要面对现实账本",
        charm = 85, huntingSkill = 65, socialRank = "A",
        alpha = 75, resistance = 55, cv = 15, luck = 70,
        systemEval = "\"适合经营型开局。资源更多，压力也更集中，关键决定会更快显现后果。\""
    )
)
