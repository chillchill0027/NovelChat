package com.novelchat.data.model

object CharacterNamingPolicy {
    const val DEFAULT_PLAYER_NAME = "林克"

    val presetPlayerNames = mapOf(
        "renter" to DEFAULT_PLAYER_NAME,
        "clerk" to "周然",
        "freelancer" to "许知南",
        "resident" to "沈清禾",
        "returnee" to "陆远"
    )

    fun playerNameForPreset(key: String): String = presetPlayerNames[key] ?: DEFAULT_PLAYER_NAME

    fun isGenericPlayerName(name: String): Boolean {
        return name.trim().lowercase() in genericPlayerNames
    }

    fun concretePlayerName(name: String): String {
        return name.trim().takeUnless(::isGenericPlayerName) ?: DEFAULT_PLAYER_NAME
    }

    fun normalizeNpcCard(card: CharacterCard): CharacterCard {
        val name = card.name.trim()
        if (!isGenericNpcName(name)) {
            return card.copy(
                id = card.id.trim(),
                name = name,
                occupation = card.occupation.trim()
            )
        }

        val role = genericRole(name).ifBlank { card.occupation.trim().ifBlank { "邻居" } }
        val namingSeed = card.id.trim()
            .takeIf { it.isNotBlank() && !isGenericNpcName(it) }
            ?: name
        val concreteName = concreteNameForRole(role, namingSeed)
        val inferredOccupation = genericNameRegex.matchEntire(name)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .ifBlank { name }
        return card.copy(
            id = if (card.id.isBlank() || isGenericNpcName(card.id)) concreteName else card.id,
            name = concreteName,
            occupation = card.occupation.trim().ifBlank { inferredOccupation.ifBlank { role } },
            background = card.background.ifBlank { "在当前场景中登场的${role}，已经与主角发生过交互。" }
        )
    }

    fun normalizeNarrativeText(text: String): String {
        var normalized = text
        contextualNarrativeNameRules.forEach { (pattern, replacement) ->
            normalized = normalized.replace(pattern, replacement)
        }
        normalized = narrativePlaceholderNameRegex.replace(normalized) { match ->
            val role = match.groupValues[1]
            "$role${concreteNameForRole(role, match.value)}"
        }
        return normalized
    }

    fun isGenericNpcName(name: String): Boolean {
        val value = name.trim()
        if (value.isBlank()) return true
        return genericNameRegex.matches(value) ||
            contextualGenericNameRegex.matches(value) ||
            value in genericNames
    }

    private fun genericRole(name: String): String {
        val value = name.trim()
        genericNameRegex.matchEntire(value)?.let { match ->
            return match.groupValues.getOrNull(1).orEmpty()
        }
        return genericRoleSuffixes.firstOrNull(value::endsWith).orEmpty()
    }

    private fun concreteNameForRole(role: String, seed: String): String {
        val names = when {
            role.contains("邻居") || role.contains("居民") -> listOf("王常", "李雯", "周明远", "许秋")
            role.contains("同事") -> listOf("赵一鸣", "唐宁", "郑凯", "沈悦")
            role.contains("店员") -> listOf("阿远", "叶青", "何川", "吴桐")
            role.contains("前台") || role.contains("接待") -> listOf("顾宁", "韩雪", "陈可", "罗琪")
            role.contains("服务员") -> listOf("苏禾", "林夏", "方晴", "蒋晨")
            role.contains("工作人员") -> listOf("程安", "邵宁", "杜衡", "乔月")
            role.contains("顾客") -> listOf("梁小满", "曹悦", "余舟", "宋妍")
            role.contains("医生") -> listOf("周澄", "秦川", "陶然", "叶闻")
            role.contains("护士") -> listOf("何苗", "陈静", "许晴", "高岚")
            role.contains("保安") -> listOf("刘师傅", "赵师傅", "马师傅", "孙师傅")
            role.contains("司机") -> listOf("陈柏", "周海", "梁峰", "吴越")
            role.contains("老奶奶") || role.contains("老太太") || role.contains("老婆婆") ->
                listOf("陈玉兰", "周桂芬", "王秀英", "李春梅")
            role.contains("老爷爷") || role.contains("老大爷") ->
                listOf("周国平", "陈建国", "李志远", "王德明")
            role.contains("老板") || role.contains("店主") -> listOf("老周", "陈老板", "林姐", "许叔")
            role.contains("女朋友") || role.contains("女友") ||
                role.contains("妻子") || role.contains("老婆") -> listOf("苏晚", "林夏", "姜妍", "顾遥")
            role.contains("男朋友") || role.contains("男友") ||
                role.contains("丈夫") || role.contains("老公") -> listOf("江川", "陆衡", "沈嘉", "周野")
            role.contains("房东") -> listOf("陈姐", "吴姐", "赵叔", "周姨")
            role.contains("招聘") || role.contains("顾问") -> listOf("孟琪", "林菲", "顾言", "唐悦")
            else -> listOf("王常", "李雯", "周明远", "许秋")
        }
        return names[placeholderIndex(seed) % names.size]
    }

    private fun placeholderIndex(seed: String): Int {
        val normalizedSeed = seed.trim()
        val suffix = genericNameRegex.matchEntire(normalizedSeed)
            ?.groupValues
            ?.getOrNull(2)
            .orEmpty()
        if (suffix.isNotBlank()) {
            suffix.toIntOrNull()?.let { return (it - 1).coerceAtLeast(0) }
            val marker = suffix.first()
            if (marker in 'A'..'Z') return marker - 'A'
            val chineseIndex = placeholderMarkers.indexOf(marker)
            if (chineseIndex >= 0) return chineseIndex
        }
        trailingNumber.find(normalizedSeed)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return (it - 1).coerceAtLeast(0) }
        if (isGenericNpcName(normalizedSeed)) return 0
        return Math.floorMod(normalizedSeed.hashCode(), 4)
    }

    private val genericNames = setOf(
        "路人", "某人", "陌生人", "男子", "女子", "老人", "年轻人",
        "前台小姑娘", "前台女孩", "前台人员", "前台接待",
        "女朋友", "女友", "男朋友", "男友", "妻子", "丈夫", "老婆", "老公", "伴侣",
        "店主", "彩票店老板", "咖啡店店员", "便利店店员",
        "男人", "女人", "中年男人", "中年女人", "年轻男人", "年轻女人",
        "老奶奶", "老太太", "老婆婆", "老爷爷", "老大爷", "那人"
    )

    private val genericPlayerNames = setOf(
        "", "主角", "玩家", "主人公", "男主", "女主",
        "player", "protagonist", "user", "me", "我"
    )

    private val genericRoleSuffixes = listOf(
        "招聘顾问", "工作人员", "前台接待", "服务员", "女朋友", "男朋友",
        "年轻人", "陌生人", "店员", "店主", "老板", "邻居", "居民", "路人",
        "同事", "前台", "接待", "顾客", "医生", "护士", "保安", "司机", "房东",
        "顾问", "男人", "女人", "男子", "女子", "老人", "老奶奶", "老太太", "老婆婆", "老爷爷", "老大爷", "女友", "男友", "妻子", "丈夫", "老婆",
        "老公", "伴侣"
    )

    private val contextualGenericNameRegex = Regex(
        """^(?:彩票店|咖啡店|便利店|餐厅|酒店|公司|小区|楼下|隔壁|值班|当班|那位|一位|这位|年轻|中年)?(?:招聘顾问|工作人员|前台接待|服务员|女朋友|男朋友|年轻人|陌生人|店员|店主|老板|邻居|居民|路人|同事|前台|接待|顾客|医生|护士|保安|司机|房东|顾问|男人|女人|男子|女子|老人|老奶奶|老太太|老婆婆|老爷爷|老大爷|女友|男友|妻子|丈夫|老婆|老公|伴侣)$"""
    )

    private val genericNameRegex = Regex(
        """^(邻居|居民|路人|同事|店员|前台|接待|服务员|工作人员|顾客|医生|护士|保安|司机|老板|店主|房东|招聘顾问|顾问|男子|女子|老人|年轻人|陌生人)([A-Z甲乙丙丁一二三四五六七八九十0-9]*)$"""
    )

    private const val placeholderMarkers = "甲乙丙丁一二三四五六七八九十"
    private val trailingNumber = Regex("(\\d+)$")

    private val contextualNarrativeNameRules = listOf(
        Regex("前台(?:小姑娘|女孩|人员)") to "前台顾宁"
    )

    private val narrativePlaceholderNameRegex = Regex(
        """(邻居|居民|路人|同事|店员|前台|接待|服务员|工作人员|顾客|医生|护士|保安|司机|老板|店主|房东|招聘顾问|顾问)[A-Z甲乙丙丁0-9]+"""
    )
}
