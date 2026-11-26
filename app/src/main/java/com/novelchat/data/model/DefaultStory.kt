package com.novelchat.data.model

import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedOpening
import com.novelchat.data.remote.model.ParsedPlayer
import com.novelchat.data.remote.model.ParsedWorldSetting
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult

const val DEFAULT_MODERN_STORY_TEXT = """标题：城市生活模拟器

背景：故事发生在当代中国一座虚构一线城市“海临市”。这里有地铁、写字楼、老小区、社区医院、便利店、共享办公空间、城中村、商业区和临海新区。世界没有魔法、超能力或怪物，所有事件都遵守现实社会、法律、经济压力、人际关系和身体精力限制。

玩法方向：这是一个现代真实世界模拟器 RPG。玩家从普通人的一天开始，通过行动选择影响收入、健康、关系、职业路径、信用、声誉和生活质量。剧情关注现实问题：租房、通勤、工作绩效、邻里关系、家庭期待、职业转型、突发账单、城市机会与个人边界。

主角默认设定：主角名叫林克，是刚在海临市稳定下来的普通成年人，租住在老小区“云桥里”，拥有一份不算稳定但能维持生活的工作。主角没有预设性格，行为会在游戏中逐渐塑造。

关键地点：云桥里老小区、青桥地铁站、海临市民服务中心、近岸商务区、望潮路便利店、社区医院、自由职业共享办公室。

初始 NPC：房东陈姐、同事林越、社区医生周澄、便利店店员阿远、招聘顾问孟琪。

开局事件：周一早晨，主角醒来后发现手机里同时弹出三条消息：房东提醒本周交房租，同事请求临时顶一个会议，招聘顾问发来一份更高薪但风险更大的面试邀请。玩家需要安排今天的优先级。"""

val defaultModernWorldSetting = WorldSetting(
    name = "城市生活模拟器",
    genre = "现代真实世界模拟",
    techLevel = "当代城市生活",
    magicLevel = "无魔法、无超能力",
    tone = "现实、克制、细腻、可选择",
    forbidden = listOf("超自然力量", "违反现实规则的能力", "无后果暴力"),
    description = "当代海临市的真实世界模拟器 RPG，围绕工作、收入、健康、关系、居住和城市机会展开。"
)

fun defaultModernCharacters(createdAt: String): List<CharacterCard> = listOf(
    CharacterCard(
        id = "chen_jie",
        name = "房东陈姐",
        status = "active",
        age = 42,
        gender = "女",
        occupation = "房东兼小店经营者",
        personality = "精明直接，但并不刻薄，重视准时和信用",
        background = "在云桥里老小区有两套出租房，也经营一家小杂货店。",
        appearance = "常穿深色外套，说话利落，手机消息回复很快",
        lore = CharacterLore(
            speechStyle = "干脆利落，爱直入正题，谈到钱和期限时会留一句实话兜底",
            sampleDialogues = listOf(
                "小伙子，水电单我放门缝了，这周五下班前记得把房租转我。",
                "行了，先忙你的工作，真周转不开提前说一声，别一声不吭玩消失。"
            )
        ),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    ),
    CharacterCard(
        id = "lin_yue",
        name = "同事林越",
        status = "active",
        age = 29,
        gender = "男",
        occupation = "项目执行",
        personality = "外向、会协调资源，但遇到压力时容易把事情推给别人",
        background = "和主角在同一家公司，经常负责临时会议和客户沟通。",
        appearance = "背双肩包，常拿着咖啡，讲话速度很快",
        lore = CharacterLore(
            speechStyle = "语速快、套近乎，爱用“兄弟帮个忙”“很快就好”先铺垫再提要求",
            sampleDialogues = listOf(
                "哥们儿救个急，十点那个需求会我这边卡在客户现场了，你帮我顶二十分钟就行。",
                "材料我都扔群里了，回头请你喝咖啡，真没几个硬骨头！"
            )
        ),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    ),
    CharacterCard(
        id = "meng_qi",
        name = "招聘顾问孟琪",
        status = "active",
        age = 31,
        gender = "女",
        occupation = "招聘顾问",
        personality = "专业、现实，擅长判断候选人的市场价值",
        background = "手里有一份薪资更高但风险更大的岗位机会。",
        appearance = "穿着干练，消息简短清楚，习惯用语音补充细节",
        lore = CharacterLore(
            speechStyle = "客观直接、分条列重点，习惯先给利弊再催确认时间",
            sampleDialogues = listOf(
                "林克，这家给到你现在薪资上浮三十五，但前半年业务线在冲刺，你先想好能不能接受加班节奏。",
                "岗位今天下午五点前就要排初面名单，有意向的话两点前给我个准信。"
            )
        ),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    )
)

fun defaultModernWorldbookEntries(createdAt: String): List<WorldbookEntry> = listOf(
    WorldbookEntry(
        id = "yunqiaoli",
        type = "location",
        name = "云桥里老小区",
        description = "主角租住的老小区，楼道窄、邻里熟、生活成本相对可控，但设施老旧。",
        keywords = listOf("云桥里", "老小区", "出租屋", "房租", "邻里"),
        relatedCharacters = listOf("房东陈姐"),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    ),
    WorldbookEntry(
        id = "qingqiao_station",
        type = "location",
        name = "青桥地铁站",
        description = "连接老小区和近岸商务区的主要通勤节点，早晚高峰拥挤，换乘耗时。",
        keywords = listOf("青桥地铁站", "地铁", "通勤", "早高峰", "换乘"),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    ),
    WorldbookEntry(
        id = "offshore_business",
        type = "location",
        name = "近岸商务区",
        description = "写字楼密集的办公区，机会多、竞争强，餐饮和时间成本都偏高。",
        keywords = listOf("近岸商务区", "写字楼", "公司", "会议", "加班"),
        relatedCharacters = listOf("同事林越", "招聘顾问孟琪"),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    ),
    WorldbookEntry(
        id = "monday_messages",
        type = "event",
        name = "周一三条消息",
        description = "房东陈姐的房租提醒、同事林越的临时顶会请求和招聘顾问孟琪的高薪面试邀请同时到来，迫使林克安排优先级。",
        keywords = listOf("周一", "房租", "会议", "面试", "优先级"),
        relatedCharacters = listOf("房东陈姐", "同事林越", "招聘顾问孟琪"),
        relatedLocations = listOf("云桥里老小区", "近岸商务区"),
        createdBy = "system",
        createdAt = createdAt,
        lastModified = createdAt
    )
)

fun defaultModernStoryState(): StoryState = StoryState(
    currentLocation = "云桥里老小区",
    timeOfDay = "周一早晨",
    weather = "多云",
    mood = "现实压力与新机会并存",
    activeCharacters = emptyList(),
    pendingEvents = listOf(
        "本周向房东陈姐交房租",
        "回复同事林越的临时顶会请求",
        "回复招聘顾问孟琪的高薪面试邀请"
    )
)

fun isDefaultModernStoryText(text: String): Boolean {
    return text.trim() == DEFAULT_MODERN_STORY_TEXT.trim()
}

fun defaultModernStoryParseResult(): StoryParseResult = StoryParseResult(
    worldSetting = ParsedWorldSetting(
        name = defaultModernWorldSetting.name,
        genre = defaultModernWorldSetting.genre,
        techLevel = defaultModernWorldSetting.techLevel,
        magicLevel = defaultModernWorldSetting.magicLevel,
        tone = defaultModernWorldSetting.tone,
        forbidden = defaultModernWorldSetting.forbidden,
        description = defaultModernWorldSetting.description
    ),
    player = ParsedPlayer(
        name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME,
        age = 25,
        gender = "未明确",
        race = "人类",
        class_ = "城市居民",
        personality = "由玩家行动逐步塑造",
        background = "刚在海临市稳定下来，租住在云桥里老小区，正在工作、收入、关系和个人选择之间寻找平衡。",
        appearance = "普通城市通勤穿着，随身带着手机、钥匙和交通卡。",
        skills = listOf("观察", "沟通", "时间管理", "预算规划")
    ),
    npcs = listOf(
        ParsedNpc(
            name = "房东陈姐",
            age = 42,
            gender = "女",
            race = "人类",
            occupation = "房东兼小店经营者",
            personality = "精明直接，但并不刻薄，重视准时和信用",
            background = "在云桥里老小区有两套出租房，也经营一家小杂货店。",
            appearance = "常穿深色外套，说话利落，手机消息回复很快",
            relationshipToPlayer = "房东与租客"
        ),
        ParsedNpc(
            name = "同事林越",
            age = 29,
            gender = "男",
            race = "人类",
            occupation = "项目执行",
            personality = "外向、会协调资源，但遇到压力时容易把事情推给别人",
            background = "和主角在同一家公司，经常负责临时会议和客户沟通。",
            appearance = "背双肩包，常拿着咖啡，讲话速度很快",
            relationshipToPlayer = "同事"
        ),
        ParsedNpc(
            name = "社区医生周澄",
            age = 34,
            gender = "女",
            race = "人类",
            occupation = "社区医生",
            personality = "冷静细致，习惯从生活习惯里判断问题",
            background = "负责云桥里附近社区医院的日常门诊。",
            appearance = "白大褂外常套一件灰色针织衫，语气平稳",
            relationshipToPlayer = "社区医疗资源"
        ),
        ParsedNpc(
            name = "便利店店员阿远",
            age = 23,
            gender = "男",
            race = "人类",
            occupation = "便利店店员",
            personality = "观察力强，熟悉附近居民的作息",
            background = "在望潮路便利店上晚班，经常听到周边小道消息。",
            appearance = "穿便利店制服，笑起来有点疲惫",
            relationshipToPlayer = "邻里熟人"
        ),
        ParsedNpc(
            name = "招聘顾问孟琪",
            age = 31,
            gender = "女",
            race = "人类",
            occupation = "招聘顾问",
            personality = "专业、现实，擅长判断候选人的市场价值",
            background = "手里有一份薪资更高但风险更大的岗位机会。",
            appearance = "穿着干练，消息简短清楚，习惯用语音补充细节",
            relationshipToPlayer = "职业机会提供者"
        )
    ),
    worldbook = listOf(
        ParsedWorldbookItem(
            type = "location",
            name = "云桥里老小区",
            description = "主角租住的老小区，楼道窄、邻里熟、生活成本相对可控，但设施老旧。",
            keywords = listOf("云桥里", "老小区", "出租屋", "房租", "邻里")
        ),
        ParsedWorldbookItem(
            type = "location",
            name = "青桥地铁站",
            description = "连接老小区和近岸商务区的主要通勤节点，早晚高峰拥挤，换乘耗时。",
            keywords = listOf("青桥地铁站", "地铁", "通勤", "早高峰", "换乘")
        ),
        ParsedWorldbookItem(
            type = "location",
            name = "近岸商务区",
            description = "写字楼密集的办公区，机会多、竞争强，餐饮和时间成本都偏高。",
            keywords = listOf("近岸商务区", "写字楼", "公司", "会议", "加班")
        ),
        ParsedWorldbookItem(
            type = "location",
            name = "望潮路便利店",
            description = "老小区和地铁站之间的便利店，能解决早餐、快递和临时用品，也聚集附近消息。",
            keywords = listOf("望潮路", "便利店", "早餐", "快递", "阿远")
        ),
        ParsedWorldbookItem(
            type = "location",
            name = "社区医院",
            description = "云桥里附近的社区医疗点，处理小病小伤，也能反映居民生活压力。",
            keywords = listOf("社区医院", "周澄", "健康", "门诊")
        ),
        ParsedWorldbookItem(
            type = "event",
            name = "周一三条消息",
            description = "房东陈姐的房租提醒、同事林越的临时顶会请求和招聘顾问孟琪的高薪面试邀请同时到来，迫使林克安排优先级。",
            keywords = listOf("周一", "房租", "会议", "面试", "优先级")
        )
    ),
    opening = ParsedOpening(
        currentLocation = "云桥里老小区",
        timeOfDay = "周一早晨",
        weather = "多云",
        mood = "现实压力与新机会并存",
        pendingEvents = listOf(
            "本周向房东陈姐交房租",
            "回复同事林越的临时顶会请求",
            "回复招聘顾问孟琪的高薪面试邀请"
        )
    )
)
