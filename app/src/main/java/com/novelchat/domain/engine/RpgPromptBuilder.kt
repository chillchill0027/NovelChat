package com.novelchat.domain.engine

import com.google.gson.Gson
import com.novelchat.data.model.*

/**
 * RPG Prompt 构建器
 * 负责构建三次 AI 调用的提示词（对齐设计文档 v1.0）
 */
class RpgPromptBuilder {

    private val gson = Gson()

    /**
     * 构建剧情调用的提示词（对齐设计文档 2.2 节）
     */
    fun buildNarrativePrompt(
        worldSetting: WorldSetting,
        playerState: PlayerState?,
        activeCharacters: List<CharacterCard>,
        worldbookEntries: List<WorldbookEntry>,
        currentWorldbook: WorldbookEntry?,
        relatedWorldbook: List<WorldbookEntry>,
        storyState: StoryState,
        allSummaries: List<SummaryEntry>,
        recentNarratives: List<NarrativeEntry>,
        userMessage: String,
        identityAnchors: List<NarrativeIdentityAnchor> = emptyList()
    ): String {
        val sb = StringBuilder()
        val currentGoal = playerState?.storyProgress?.currentGoal.orEmpty()
            .ifBlank { storyState.pendingEvents.firstOrNull().orEmpty() }
        val isContinuationRequest = NarrativeQualityPolicy.isContinuationRequest(userMessage)

        sb.appendLine("【叙事职责】")
        sb.appendLine("你是一名成熟的中文类型小说作者，以第三人称限知视角续写剧情。用户输入是本轮已经确定的行动与事实，你负责把它写成自然、可信、可继续互动的小说正文。")
        sb.appendLine("创作优先级依次为：既定事实与动作完整、因果和人物一致、正文可读性、结构化自检。先在内部完成整段小说正文，再从正文提取其余 JSON 字段；action_coverage 等字段不能反过来把正文写成答题或清单。")
        sb.appendLine()
        sb.appendLine("【硬性规则】")
        sb.appendLine("- 主角始终使用主角状态中的真实姓名；身份或职业不能代替姓名。")
        sb.appendLine("- 用户只用身份或关系词指代人物时，先与已知角色的姓名、身份和关系做唯一匹配；不得凭空升级亲密关系。英文 partner 若没有恋爱或婚姻语境，按搭档/同伴理解，不能擅自生成恋人。")
        sb.appendLine("- 新登场且与主角发生实质互动的人物必须有自然的真实姓名，禁止“邻居A、路人甲、店员A、陌生人、中年男人、那人”等占位称呼；纯背景中没有对白、递接、拦截或碰撞的人群不必建档。")
        sb.appendLine("- 严格遵守世界设定、既有事实、人物关系与时间线，不擅自取消或改写用户已经明确的行动结果。")
        sb.appendLine("- 角色 lore.secrets、世界书 is_secret=true 或 known_by_player=false 的内容属于作者侧事实：可用于保持因果一致，但在角色通过可信途径发现前，禁止让正文、对白或主角内心直接泄露。")
        sb.appendLine("- 用户输入新确认的人际关系、随身物品或行动前提，视为本轮成立的事实；即使旧状态尚未记录，也要自然承接并交给后续状态同步，禁止因资料中没有就静默删去。")
        sb.appendLine("- 用户输入无论是第一人称、第三人称还是混合视角，都转换成连贯的第三人称正文。")
        sb.appendLine("- 用户输入是动作骨架。每个动作都要落实为可见的过程、环境变化、他人即时反应和现实后果；多个动作按顺序自然衔接，不能漏项，也不能写成清单。")
        sb.appendLine("- 不要在开头换几个词复述用户输入，也不要用“先……然后……接着……最后……”逐项报幕。把原话转化成正在发生的场景，用离开、抵达、物件、对话或空间变化完成转场。")
        sb.appendLine("- 用户说某事已经完成，或使用“之后/after doing”作为前提时，正文必须先写清这个动作如何完成；不能把“安置好/照顾好某人”弱化成只发一条消息，也不能把完成态改成打算、通知或准备。")
        sb.appendLine("- 完整承接用户行动后，主动续写一至两个由当前因果自然产生的场景节拍，优先写他人反应、流程变化、信息到来或眼前阻力，为用户留下可回应的新局面；不要替主角做新的重大决定，也不要突然制造无关灾难。")
        sb.appendLine("- 每一段都必须产生至少一个新的外部事实、互动或流程变化。普通走路、乘车、等待和回想应压缩成一句；除非途中发生会改变选择的事件，否则不能用整轮只描写接近目的地。")
        sb.appendLine("- 先在内部把用户输入拆成连续的原子动作，再按原顺序逐项写进正文；每个动作至少要有具体执行过程和即时反馈。")
        sb.appendLine("- narrative 写 3-6 段，正文目标为 1000-1500 个中文字符；每段承担明确推进作用，不能靠重复动作、空泛心理或景物堆砌凑字数。summary 只用一句话记录正文中已经出现的事实与新变化，禁止把正文漏写的行动偷偷补进 summary。")
        sb.appendLine("- action_coverage 为动作覆盖自检：用户的每个原子动作各写一项；evidence 必须逐字摘录 narrative 中 4-20 个汉字的连续原句，不得概括或虚构，也不要添加引号或省略号。")
        sb.appendLine("- forward_hook 记录承接完全部用户动作后主动续写的新变化；cause 写它由何种既有因果触发，evidence 逐字摘录 narrative 中位于最后一个动作之后的连续原句，且不能复用 action_coverage 的证据。")
        sb.appendLine("- ending_scene.location 写正文最后一句发生时主角脚下的实际地点，不能写出发地或下一步准备前往的地点；present_characters 只列此刻与主角物理同场的 NPC 真实姓名，不含主角、电话或消息另一端的人。")
        sb.appendLine("- introduced_characters 列出本轮正文首次出现且不在【当前活跃角色】中的每个人：包括用户新提到的伴侣/亲属，以及与主角说话、递接物品或产生反应的店主、店员等。每人必须有真实姓名，occupation 写身份，relationship_to_player 写与主角关系，first_appearance 写正文中已经发生的首次登场事实；正文必须至少一次使用同一个真实姓名。没有新人时写 []。")
        sb.appendLine("- narrative 是一个 JSON 字符串；段落之间只能在这个字符串内部使用 \\n\\n，绝不能把多段正文写成多个无字段名的字符串。")
        sb.appendLine("- 只输出一个合法 JSON 对象，不要代码块、标题、前言或解释。")
        sb.appendLine()
        sb.appendLine("【文风与可读性】")
        sb.appendLine("- 使用自然、克制的现代中文。每句话只承担一种作用：具体动作、人物对白、当场可感知的事实，或动作造成的明确结果；四者都没有的句子删除。")
        sb.appendLine("- 每段 2-4 句，同一个动作只写一次。普通走路、抬手、按键、等待不要拆成连续镜头，段落必须尽快抵达新的互动或结果。")
        sb.appendLine("- 对话符合人物身份，允许停顿、潜台词和不完整表达。情绪最多选择一个可观察反应表现，不给情绪贴标签，也不替人物解释内心结论。")
        sb.appendLine("- 只写角色此刻能看到、听到、触到或说出的内容。不评价动作是否专业、高效、精准，不解释一个动作象征什么、宣告什么阶段或发出什么信号。")
        sb.appendLine("- 屏幕与业务流程只写会改变局面的最终反馈。一次操作最多一句，禁止逐帧描写按键、光标、进度条、数据流和系统如何接收指令。")
        sb.appendLine("- 正文中的人物不知道轮次、玩家、提示词或应用状态；禁止出现任何此类应用元信息。")
        sb.appendLine("- 长短句交替，用动作、声音或物件完成段落过渡；少用形容词和比喻，不以顺序连接词逐项报幕。")
        sb.appendLine("- 交稿前逐句删减：删掉后不损失新事实、对白、物件变化或用户可回应选择的句子，一律删掉。")
        sb.appendLine("- 对兑奖、面试、交易、就医等现实流程，写出关键步骤和可信反馈。若用户确定了巨大结果，例如中一亿元，必须保留该事实，并写出核验、震惊、手续或风险带来的后续压力。")
        sb.appendLine()

        sb.appendLine("【世界整体设定】")
        sb.appendLine(gson.toJson(LorePromptSnapshotPolicy.world(worldSetting)))
        sb.appendLine()

        if (playerState != null) {
            sb.appendLine("【主角状态】")
            sb.appendLine(gson.toJson(PlayerPromptSnapshotPolicy.create(playerState)))
            sb.appendLine()
        }

        val narrativeWorldbook = (listOfNotNull(currentWorldbook) + relatedWorldbook + worldbookEntries)
            .distinctBy { it.id.ifBlank { it.name } }
            .take(8)
        if (narrativeWorldbook.isNotEmpty()) {
            sb.appendLine("【本轮相关世界资料】")
            narrativeWorldbook.forEach { sb.appendLine(gson.toJson(worldbookNarrativeSnapshot(it))) }
            sb.appendLine()
        }

        if (activeCharacters.isNotEmpty()) {
            sb.appendLine("【当前活跃角色】")
            val charMap = mutableMapOf<String, Any>()
            activeCharacters.forEach { card -> charMap[card.name] = characterNarrativeSnapshot(card) }
            sb.appendLine(gson.toJson(charMap))
            sb.appendLine()
        }

        sb.appendLine("【当前剧情状态】")
        sb.appendLine(gson.toJson(storyState))
        sb.appendLine()

        if (allSummaries.isNotEmpty()) {
            sb.appendLine("【缩略剧情】")
            allSummaries.takeLast(12).forEach { summary -> sb.appendLine("第${summary.turn}轮：${summary.summary}") }
            sb.appendLine()
        }

        if (recentNarratives.isNotEmpty() && !isContinuationRequest) {
            sb.appendLine("【最近对话事实，只用于连续性，不模仿措辞】")
            recentNarratives.takeLast(2).forEach { narrative ->
                sb.appendLine("第${narrative.turn}轮：")
                sb.appendLine("用户：${narrative.userInput}")
                sb.appendLine("正文：${narrative.narrative.take(900)}")
                sb.appendLine()
            }
        }

        sb.appendLine("【用户输入】")
        sb.appendLine(userMessage)
        sb.appendLine()

        NarrativeQualityPolicy.majorOutcomeAmountAnchor(userMessage)?.let { amount ->
            sb.appendLine("【不可改写的数值事实】")
            sb.appendLine("- 本轮中奖金额必须保持为$amount。正文、summary 和 action_coverage 均不得改成一百万元、一千万元或任何其他金额。")
            sb.appendLine("- 最后一个用户动作完成后，必须由一位具名兑奖人员看清该金额并出现可观察的震惊反应，随后启动复核、主管确认或正式兑奖手续；主角自己的反应不能代替这两项现场反馈。")
            sb.appendLine()
        }

        if (identityAnchors.isNotEmpty()) {
            sb.appendLine("【本轮人物实名锚点】")
            identityAnchors.forEach { anchor ->
                sb.appendLine(
                    "- 用户所说的“${anchor.reference}”统一指向真实姓名“${anchor.name}”，" +
                        "与主角关系为“${anchor.relationship}”。正文第一次提及时必须写成" +
                        "“${anchor.reference}${anchor.name}”或直接使用“${anchor.name}”；" +
                        if (anchor.isExistingCharacter) {
                            "这是既有角色，不要重复写入 introduced_characters。"
                        } else {
                            "这是本轮新人物，必须写入 introduced_characters。"
                        }
                )
            }
            sb.appendLine("实名锚点是既定事实，不能改名、删去或退回身份泛称。")
            sb.appendLine()
        }

        if (isContinuationRequest) {
            sb.appendLine("【续写边界】")
            sb.appendLine("这是没有新增动作的继续请求。必须从上一轮最后一句之后的下一刻开始，禁止概括、改写或再次描写上一轮已经发生的出发、行走、来信、对话和人物反应。")
            sb.appendLine("若下一步出现兑现方式、资金去向、合同条款、关系承诺、法律处置或其他需要主角表态的分岔，只写NPC提出选项、表格停在面前或系统等待确认，并在主角回答之前收束。禁止替主角回答、点头、签字或执行选择。")
            if (currentGoal.isNotBlank()) {
                sb.appendLine("当前目标：$currentGoal")
                sb.appendLine("本轮必须让这个目标抵达下一个实际节点，例如抵达并进入、提交材料、开始核验、收到决定性回应或遇到必须处理的真实阻力；不能只写继续赶路、越来越近、思考或准备。")
            } else {
                sb.appendLine("本轮必须形成一个可观察的新事实或新的互动局面，不能只延长上一轮的动作。")
            }
            val previousBoundary = allSummaries.lastOrNull()?.summary?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: recentNarratives.lastOrNull()?.narrative
                    ?.replace(Regex("“[^”]*”|\"[^\"]*\""), "")
                    ?.trim()
                    ?.takeLast(240)
                    .orEmpty()
            if (previousBoundary.isNotBlank()) {
                sb.appendLine("上一轮已完成事实（只作为续写起点，不得复述原句）：$previousBoundary")
            }
            sb.appendLine()
        }

        sb.appendLine("【必须逐项落实的动作清单】")
        NarrativeQualityPolicy.atomicActionHints(userMessage, currentGoal).forEachIndexed { index, action ->
            sb.appendLine("${index + 1}. $action")
        }
        sb.appendLine("清单每一项都必须按上述顺序出现在 narrative，并各自对应一条 action_coverage；清单中的前提从第一项开始写，不能用后文概括代替。")
        sb.appendLine()

        sb.appendLine("【输出格式】")
        sb.appendLine("""{"narrative":"3-6段、1000-1500个中文字符的连贯小说正文","summary":"一句话记录本轮正文事实和新变化","action_coverage":[{"action":"用户的一个原子动作","evidence":"从 narrative 逐字摘录的连续原句"}],"forward_hook":{"cause":"由本轮既有因果触发的新变化","evidence":"最后一个用户动作之后的正文原句"},"ending_scene":{"location":"正文结尾的实际地点","present_characters":["同场NPC真实姓名"]},"introduced_characters":[{"name":"首次登场者真实姓名","occupation":"身份","relationship_to_player":"与主角关系","first_appearance":"正文已发生的首次登场事实"}]}""")

        return sb.toString()
    }

    /**
     * 构建任务分配调用的提示词（对齐设计文档 3.4 节）
     */
    fun buildTaskAssignmentPrompt(
        worldSetting: WorldSetting,
        playerState: PlayerState?,
        storyState: StoryState,
        activeCharacters: List<CharacterCard>,
        worldbookEntries: List<WorldbookEntry>,
        narrative: String,
        summary: String,
        recentNarratives: List<NarrativeEntry>,
        endingScene: EndingScene = EndingScene(),
        introducedCharacters: List<IntroducedCharacter> = emptyList()
    ): String {
        val sb = StringBuilder()

        sb.appendLine("【任务】")
        sb.appendLine("你是剧情状态同步器。只分析“本轮正文”已经明确造成的变化，并输出一个合法 JSON 对象。")
        sb.appendLine("- 没有变化的对象使用 null，没有项目的列表使用 []。禁止输出省略号、注释、Markdown 或 JSON 之外的文字。")
        sb.appendLine("- sync_status 必须写 updated 或 no_change。只要任一持久状态有变化就写 updated；只有地点、时间、在场人物、待办、主角、角色和世界资料均无需改变时，才写 no_change，并在 no_change_reason 中具体说明为什么无需同步。")
        sb.appendLine("- 所有列表中的每一项都必须是符合下述字段约定的 JSON 对象，绝不能把事件或人物变化写成纯字符串。")
        sb.appendLine("- 不要猜测未发生的变化，不要给所有在场角色例行刷新；character_updates 只列出本轮实际互动或状态明确改变的人物。")
        sb.appendLine("- 主角的身体、心理、风险、任务和关系发生变化时写入 player_update；数值要依据正文，变化幅度克制。")
        sb.appendLine("- 【世界整体设定.character_fields】是本故事角色卡的字段合同。player_update.changes.attributes 使用 key:value 更新主角字段；dynamic 字段在本轮有明确变化时更新，event 字段只有形成长期改变时更新，manual 字段禁止自动更新。")
        sb.appendLine("- 【强制动态字段核对】逐项阅读每个 dynamic 字段的 label、description 与世界 rules。正文一旦发生其描述的消耗、恢复、累积、缓解、暴露或受损条件，就必须写入对应最终值；不能因为主角行动成功、变化幅度较小或字段名称是故事自定义词而省略。")
        sb.appendLine("- 例如字段定义说明“施法消耗魔力”，正文实际完成施法时必须降低该字段；安全休息或引潮已完成时才可恢复。世界规则中的必然代价优先于模型自行判断。所有 number/choice/text 更新均写本轮结束后的最终值，不写增减量。")
        sb.appendLine("- 人物特征、经历、外貌、技能、魅力、行动技巧、社交等级、意志、体魄抗性、运气以及故事自定义字段都不是永久冻结项。正文若产生可持续的新事实，必须把对应 key 写入 attributes；不要因普通寒暄重写长期档案。")
        sb.appendLine("- 当首要待办的内容、阶段或完成状态改变时，同步更新 player_update.changes.story_progress.current_goal，使它与 pending_events 的第一项保持一致；不要留下已经完成的旧目标。")
        sb.appendLine("- 正文出现赶工或冲突、明确的截止承诺、休息或进食、受伤或恢复、获得或失去物品时，player_update 不得为 null；用 mental_state、physical_state 或 story_progress.current_goal 记录有判断价值的变化。")
        sb.appendLine("- 地点、时间、天气、氛围、在场人物或待处理事件改变时写入 story_update。")
        sb.appendLine("- story_update.pending_events 是更新后的完整待办列表：删除前文中已完成的事项，保留未完成事项，并把当前最紧迫事项放在最前。")
        sb.appendLine("- pending_events 每一项都必须是可独立理解的具体行动，包含对象或结果；禁止把“尚未执行、尚未安排时间、时间”等模板说明拆成待办。已经获得引导、已经抵达等完成的阶段不得保留为待办。")
        sb.appendLine("- 只有正文明确完成整个事项时才能从 pending_events 删除。开始处理、形成初稿、取得部分回复或约定未来时间都只是进展，必须保留核心待办并按最新事实改写描述。")
        sb.appendLine("- 回复面试/会议邀请只完成“回复邀请”，不等于未来的面试/会议已经完成；应把待办更新为具体赴约时间，而不是删除。")
        sb.appendLine("- new_characters 只包含首次出现且不在已知角色中的人物，name 必须是真实姓名，身份写入 occupation；occupation 必须是正文已知的单一具体身份，禁止“学生或职员、可能是店员、未知”等猜测，职业不明时写与主角的确定关系（如“林克的女友”）；attributes 填写可由正文确认的角色字段初值，不要输出 initial_status。")
        sb.appendLine("- 【本轮首次登场人物锚点】中的每个人，只要不在【已知角色状态】中，就必须逐人写入 new_characters；禁止遗漏用户新确认的伴侣/亲属，也禁止用“老板、店员、女朋友”等身份泛称代替 name。")
        sb.appendLine("- story_update.active_characters 中的每个名字必须逐字等于【已知角色状态】或 new_characters 中的一个真实姓名，不能引用不存在的角色卡。")
        sb.appendLine("- 人物只是暂时离开当前场景时，只修改 story_update.active_characters；retired_characters 仅用于明确永久离开故事或确定死亡的人物。")
        sb.appendLine("- 只记录本轮确实发生的物品转移、世界资料变化和关键事件。严格遵守世界设定。")
        sb.appendLine("- 同一件物品变化只能记录一次：角色之间转移写 item_transfers；从环境获得或向环境消耗写 player_update.changes.inventory，禁止两个字段重复记录同一变化。item_transfers 中的主角必须使用主角真实姓名。")
        StateSyncPolicy.pendingFromRound(storyState)?.let { pendingFrom ->
            sb.appendLine("- 第${pendingFrom}轮起存在尚未同步的状态；必须把下方【待补同步剧情】与本轮正文合并分析，输出截至本轮结尾的最终状态，已经结算过的物品或关系变化不得重复累计。")
        }
        sb.appendLine()

        sb.appendLine("【世界整体设定】")
        sb.appendLine(gson.toJson(LorePromptSnapshotPolicy.world(worldSetting)))
        sb.appendLine()

        sb.appendLine("【剧情内容】")
        sb.appendLine(narrative)
        sb.appendLine()

        sb.appendLine("【缩略剧情】")
        sb.appendLine(summary.ifBlank { narrative.take(300) })
        sb.appendLine()

        sb.appendLine("【正文结尾场景锚点】")
        sb.appendLine(gson.toJson(endingScene))
        sb.appendLine("current_location 与 active_characters 必须以这个结尾场景为准；电话或消息另一端的人不算在场。")
        sb.appendLine()

        sb.appendLine("【本轮首次登场人物锚点】")
        sb.appendLine(gson.toJson(introducedCharacters))
        sb.appendLine("逐项与【已知角色状态】核对；未存在者必须创建，已存在者不得重复创建。")
        sb.appendLine()

        val recoveryNarratives = StateSyncPolicy.recoveryNarratives(storyState, recentNarratives)
        if (recoveryNarratives.isNotEmpty()) {
            sb.appendLine("【待补同步剧情】")
            recoveryNarratives.forEach { entry ->
                sb.appendLine("第${entry.turn}轮：用户=${entry.userInput.take(320)}；正文=${entry.narrative.take(900)}")
            }
            sb.appendLine()
        } else if (recentNarratives.isNotEmpty()) {
            sb.appendLine("【前情边界，仅用于避免重复创建人物】")
            recentNarratives.takeLast(2).forEach { entry ->
                sb.appendLine("第${entry.turn}轮：用户=${entry.userInput.take(240)}；结果=${entry.narrative.take(500)}")
            }
            sb.appendLine()
        }

        sb.appendLine("【当前场景状态】")
        sb.appendLine(gson.toJson(storyState))
        sb.appendLine()

        if (playerState != null) {
            sb.appendLine("【主角当前状态】")
            sb.appendLine(gson.toJson(PlayerPromptSnapshotPolicy.create(playerState)))
            sb.appendLine()
        }

        if (activeCharacters.isNotEmpty()) {
            sb.appendLine("【已知角色状态】")
            val charMap = mutableMapOf<String, Any>()
            activeCharacters.forEach { card -> charMap[card.name] = characterTaskSnapshot(card) }
            sb.appendLine(gson.toJson(charMap))
            sb.appendLine()
        }

        if (worldbookEntries.isNotEmpty()) {
            sb.appendLine("【相关世界资料】")
            sb.appendLine(gson.toJson(worldbookEntries.take(10).map { worldbookTaskSnapshot(it) }))
            sb.appendLine()
        }

        sb.appendLine("【字段约定】")
        sb.appendLine("- story_update 可包含 current_location、time_of_day、weather、mood、scene_description、active_characters、pending_events；scene_description 用一句已发生的结尾场景事实概括当前画面。")
        sb.appendLine("- player_update.changes 可包含 physical_state、mental_state、attributes、risk、inventory、relationships、quest_log、story_progress；只写改变项。attributes 的 key 必须来自 character_fields。")
        val riskField = CharacterFieldPolicy.definitionFor(worldSetting.characterFields, "risk")
        val riskRange = riskField?.let { "${it.minValue}-${it.maxValue}" } ?: "0-100"
        sb.appendLine("- risk 必须是 $riskRange 的整数并遵守 character_fields 中 risk 的语义；风险原因写入 player_update.reason，绝不能把说明文字填进 risk。")
        sb.appendLine("- character_updates 项格式为 {\"name\":\"真实姓名\",\"reason\":\"本轮发生的明确变化\"}；name 必须逐字复制【已知角色状态】中的角色名，禁止省略身份前缀或自造别名。")
        sb.appendLine("- new_characters 项至少包含 name、occupation、personality、background、appearance、relationship_to_player、first_appearance、attributes；attributes 没有可确认值时写 {}。")
        sb.appendLine("- retired_characters 项格式为 {\"name\":\"已知角色真实姓名\",\"reason\":\"永久退场原因\"}；临时离场不得写入。")
        sb.appendLine("- worldbook_updates 项格式为 {\"action\":\"create|update|delete\",\"id\":\"已有条目ID或新建稳定ID\",\"name\":\"条目名称\",\"type\":\"location|item|organization|event|character\",\"description\":\"已确认事实\",\"keywords\":[\"关键词\"]}；update/delete 必须使用【相关世界资料】中可定位的 id 或 name。")
        sb.appendLine("- item_transfers 项格式为 {\"item\":\"物品名\",\"quantity\":1,\"from\":\"已知角色真实姓名\",\"to\":\"已知角色真实姓名\",\"reason\":\"正文中的转移原因\"}；双方都必须能在主角或已知角色中找到。")
        sb.appendLine("- events 项格式为 {\"type\":\"事件类型\",\"target\":\"地点或对象\",\"caused_by\":\"直接原因或人物\",\"time\":\"发生时间\",\"description\":\"本轮已发生的事实\"}；禁止写成字符串数组。")
        sb.appendLine()
        sb.appendLine("【输出模板】")
        sb.appendLine("""{"story_update":null,"player_update":null,"new_characters":[],"retired_characters":[],"character_updates":[],"worldbook_updates":[],"item_transfers":[],"events":[],"sync_status":"updated","no_change_reason":""}""")

        return sb.toString()
    }

    fun buildEssentialStateSyncPrompt(
        worldSetting: WorldSetting = WorldSetting(),
        playerState: PlayerState?,
        storyState: StoryState,
        activeCharacters: List<CharacterCard>,
        narrative: String,
        summary: String,
        recentNarratives: List<NarrativeEntry>,
        endingScene: EndingScene = EndingScene(),
        introducedCharacters: List<IntroducedCharacter> = emptyList()
    ): String {
        return buildString {
            appendLine("你是剧情状态同步器。前两次完整任务分配未能返回可用结构，现在只做必要状态同步。")
            appendLine("只输出一个合法 JSON 对象，不要 Markdown、解释、注释或省略号。所有列表元素必须是对象。")
            appendLine("sync_status 必须写 updated 或 no_change；空分配仅在确实没有任何持久状态变化时使用 no_change，并填写具体 no_change_reason。")
            appendLine("根据正文末尾的事实更新 story_update 与 player_update；首次登场人物写入 new_characters，实际互动或明确变化的已知人物写入 character_updates。其余列表保持 []。")
            appendLine("story_update.pending_events 必须是截至正文末尾的完整未完成事项，已完成事项删除，部分完成事项按最新阶段保留。")
            appendLine("pending_events 每一项必须是可独立执行的完整短句，写清动作与对象；禁止输出“尚未执行、尚未安排时间、时间”等无对象的模板碎片，也不要把已经抵达或已经获得引导写成待办。")
            appendLine("story_update.active_characters 只写真正在当前场景的人物真实姓名；身份不能代替姓名。scene_description 用一句已发生的结尾场景事实概括当前画面。")
            appendLine("【本轮首次登场人物锚点】中不在已知角色里的每个人都必须写入 new_characters；active_characters 的每个名字必须能在已知角色或 new_characters 中找到。")
            appendLine("player_update.changes 只写改变项，可含 physical_state、mental_state、attributes、risk、inventory、relationships、quest_log、story_progress；attributes 的 key 必须来自 character_fields；risk 只能是数值。")
            appendLine("逐项检查 character_fields 中 update_mode=dynamic 的字段。正文触发字段 description 或世界 rules 写明的消耗、恢复、累积、缓解、暴露或受损条件时，必须写入本轮结束后的最终值；例如施法消耗魔力时不可省略魔力字段。")
            appendLine("new_characters 的 name 必须是真实姓名，并包含 occupation、personality、background、appearance、relationship_to_player、first_appearance。")
            StateSyncPolicy.pendingFromRound(storyState)?.let { pendingFrom ->
                appendLine("第${pendingFrom}轮起有状态待补同步。综合【待补同步剧情】和【本轮正文】，给出截至本轮末尾的最终状态；不要重复累计已经体现在当前状态中的变化。")
            }
            appendLine()
            appendLine("【世界与角色字段合同】")
            appendLine(gson.toJson(LorePromptSnapshotPolicy.world(worldSetting)))
            appendLine("【当前场景状态】")
            appendLine(gson.toJson(storyState))
            playerState?.let {
                appendLine("【主角当前状态】")
                appendLine(gson.toJson(PlayerPromptSnapshotPolicy.create(it)))
            }
            if (activeCharacters.isNotEmpty()) {
                appendLine("【已知角色】")
                appendLine(gson.toJson(activeCharacters.associate { it.name to it.occupation }))
            }
            val recoveryNarratives = StateSyncPolicy.recoveryNarratives(storyState, recentNarratives)
            if (recoveryNarratives.isNotEmpty()) {
                appendLine("【待补同步剧情】")
                recoveryNarratives.forEach { entry ->
                    appendLine("第${entry.turn}轮：用户=${entry.userInput.take(320)}；正文=${entry.narrative.take(900)}")
                }
            }
            appendLine("【本轮正文】")
            appendLine(narrative)
            appendLine("【本轮摘要】")
            appendLine(summary.ifBlank { narrative.take(300) })
            appendLine("【正文结尾场景锚点】")
            appendLine(gson.toJson(endingScene))
            appendLine("story_update.current_location 与 active_characters 必须以该锚点为准。")
            appendLine("【本轮首次登场人物锚点】")
            appendLine(gson.toJson(introducedCharacters))
            appendLine("【输出模板】")
            appendLine("""{"story_update":null,"player_update":null,"new_characters":[],"retired_characters":[],"character_updates":[],"worldbook_updates":[],"item_transfers":[],"events":[],"sync_status":"updated","no_change_reason":""}""")
        }
    }

    fun buildTaskAssignmentRepairPrompt(rawResponse: String): String {
        return buildString {
            appendLine("把下面这次无效的任务分配输出修复为一个合法 JSON 对象。")
            appendLine("只能修复格式与字段形状，不要添加原输出没有表达的新事件。禁止省略号、注释、Markdown 和解释文字。")
            appendLine("缺失列表补为 []，没有变化的 story_update/player_update 补为 null。")
            appendLine("输出必须包含十个顶层字段：story_update、player_update、new_characters、retired_characters、character_updates、worldbook_updates、item_transfers、events、sync_status、no_change_reason。")
            appendLine("存在任何持久变化时 sync_status 写 updated；只有确认全部状态都无需改变时写 no_change，并给出具体 no_change_reason。")
            appendLine("所有列表元素必须是 JSON 对象，不能是纯字符串。若 events 中原本是字符串，把原文字放进 description，并补成 {\"type\":\"剧情事件\",\"target\":\"\",\"caused_by\":\"\",\"time\":\"\",\"description\":\"原文字\"}。")
            appendLine("若 character_updates 中原本是字符串，改成 {\"name\":\"明确的人名\",\"reason\":\"原文字表达的变化\"}；不能确定姓名时删除该项。")
            appendLine("player_update.changes.risk 只能是 0-100 整数；若原值是无法换算的说明文字，删除 risk，并把说明保留在 player_update.reason。")
            appendLine("【无效输出】")
            appendLine(rawResponse.take(8000))
            appendLine("【合法空模板】")
            appendLine("""{"story_update":null,"player_update":null,"new_characters":[],"retired_characters":[],"character_updates":[],"worldbook_updates":[],"item_transfers":[],"events":[],"sync_status":"updated","no_change_reason":""}""")
        }
    }

    fun buildNarrativeRepairPrompt(
        originalPrompt: String,
        rawResponse: String,
        issue: String
    ): String {
        return buildString {
            appendLine(originalPrompt)
            appendLine()
            appendLine("【必须重写】")
            appendLine("上次输出未通过正文完整性检查：$issue。")
            appendLine("重新输出完整 JSON，不要只修 summary。正文必须以【必须逐项落实的动作清单】为骨架，按顺序写完每一个动作，并为每项提供能在 narrative 中逐字找到的独立 evidence；forward_hook 还必须引用最后一个动作之后主动推进的新变化，ending_scene 必须记录正文最后一句的实际场景。")
            appendLine("重写后的 narrative 仍须保持 1000-1500 个中文字符；用过程、互动、反馈和自然后续补足内容，禁止用重复事实或空泛描写凑字数。")
            appendLine("先把 narrative 重写成自然连贯的小说场景，再从成稿提取自检字段；不要逐条复述动作清单，不要用“然后、接着、随后、最后”给每个动作报幕，也不要用抽象情绪标签代替人物的可见反应。")
            appendLine("只保留角色能看到、听到、触到或当场说出的内容；不要解释动作的意义、效率、阶段或信号。屏幕和流程只写一次会改变局面的最终反馈，不逐帧描写按键、光标、进度条、数据流或系统执行。")
            appendLine("再做一次删句检查：一句话若没有带来新事实、对白、物件变化或用户可回应的选择，就不要保留。")
            appendLine("所有首次登场且参与互动的人都要在正文中获得真实姓名并写入 introduced_characters；ending_scene.present_characters 只能引用这些真实姓名或既有角色姓名。")
            appendLine("【上次输出，仅用于识别遗漏，不得照抄其错误】")
            appendLine(rawResponse.take(8000))
            appendLine("【现在重写】")
            appendLine("从正确的续写起点重新创作，不得复用上次输出或上一轮中的整句、段落和比喻。压缩无事件的移动与心理说明，让每一段产生新的事实、互动或流程进展；完成正文后再提取 JSON 自检字段。只输出完整 JSON。")
        }
    }

    fun buildNarrativeFocusedRepairPrompt(
        rawResponse: String,
        issue: String,
        userMessage: String,
        currentGoal: String = "",
        playerName: String = "主角",
        identityAnchors: List<NarrativeIdentityAnchor> = emptyList()
    ): String {
        return buildString {
            appendLine("【任务】")
            appendLine("你是中文小说终审编辑。下方 JSON 已经过一次重写，但仍有关键遗漏。只做解决遗漏所需的最小改写，然后输出完整 JSON。")
            appendLine("未通过项：$issue。")
            appendLine("用户本轮确定的事实：$userMessage")
            appendLine("主角真实姓名：$playerName")
            NarrativeQualityPolicy.majorOutcomeAmountAnchor(userMessage)?.let { amount ->
                appendLine("不可改写的数值：$amount。必须在 narrative 中使用这个等价金额，禁止改成一百万元、一千万元或其他数值。")
            }
            identityAnchors.forEach { anchor ->
                appendLine(
                    "强制实名：用户所说的“${anchor.reference}”必须写为“${anchor.reference}${anchor.name}”或" +
                        "“${anchor.name}”，关系为“${anchor.relationship}”；" +
                        if (anchor.isExistingCharacter) {
                            "不得作为新人重复建档。"
                        } else {
                            "必须写入 introduced_characters。"
                        }
                )
            }
            appendLine()
            appendLine("【逐项验收】")
            NarrativeQualityPolicy.atomicActionHints(userMessage, currentGoal).forEachIndexed { index, action ->
                appendLine("${index + 1}. 保留并具体写完：$action")
            }
            appendLine("- narrative 必须按上面顺序保留全部动作，不得把完成态改成计划，也不得替主角增加新的重大决定。")
            appendLine("- narrative 必须保持 1000-1500 个中文字符；修复遗漏时不能把原有有效场景压缩成短摘要。")
            appendLine("- 完成最后一个用户动作后必须另起一段，写至少两句由现场因果触发的新变化；优先写 NPC 的可观察反应和紧接着启动的现实流程。该段必须发生在最后动作之后。")
            appendLine("- forward_hook.evidence 必须逐字引用这个新段落中的原句，不能复用任何 action_coverage.evidence。")
            if (issue.contains("高影响结果")) {
                appendLine("- 用户明确的巨额中奖是确定事实。最后一个动作完成后，必须在同一新段落中让一位具名兑奖人员看清正确金额，写出此人的具体可观察震惊反应，并紧接着启动至少一项可信核验或兑奖手续；不能只写主角的内心感受，也不能只写核验而省略他人反应。")
            }
            if (
                issue.contains("真实姓名") ||
                issue.contains("introduced_characters") ||
                issue.contains("首次登场")
            ) {
                appendLine("- 用户提到的伴侣/亲属和本轮发生互动的工作人员都必须获得自然的真实姓名；姓名必须出现在 narrative，并逐人写入 introduced_characters。身份词不能代替姓名。")
            }
            appendLine("- action_coverage 每个动作各一项，evidence 分别逐字摘自 narrative；ending_scene 记录最后一句的实际地点和同场 NPC 真实姓名。")
            appendLine("- 使用自然克制的第三人称中文。用停顿、手势、声音或流程动作表现情绪，不写“他感到震惊”这类标签，不写清单式报幕。")
            appendLine("- 不得原样返回待修复 narrative。提交前逐项核对未通过项，只有全部解决才能输出。")
            appendLine()
            appendLine("【待修复 JSON】")
            appendLine(rawResponse.take(8000))
            appendLine()
            appendLine("【输出格式】")
            appendLine("""{"narrative":"3-6段、1000-1500个中文字符的完整小说正文","summary":"一句话记录正文事实和新变化","action_coverage":[{"action":"用户的一个原子动作","evidence":"正文原句"}],"forward_hook":{"cause":"由既有因果触发的新变化","evidence":"最后动作之后的新段落原句"},"ending_scene":{"location":"正文结尾实际地点","present_characters":["同场NPC真实姓名"]},"introduced_characters":[{"name":"真实姓名","occupation":"身份","relationship_to_player":"与主角关系","first_appearance":"正文已发生的首次登场事实"}]}""")
            appendLine("只输出一个合法 JSON 对象。")
        }
    }

    fun buildNarrativeStyleRepairPrompt(
        rawResponse: String,
        issue: String
    ): String {
        return buildString {
            appendLine("【任务】")
            appendLine("你是中文小说删改编辑。下方 JSON 的人物、动作顺序、地点、物品、结果和因果已经确定；只改善 narrative 的文字，不得新增、删除或改变任何剧情事实。")
            appendLine("检测到的文风问题：$issue。")
            appendLine()
            appendLine("【删改规则】")
            appendLine("- 保留用户动作及其结果、主动续写的新变化、所有具名人物和结尾场景。允许合并冗句，但不能省略已经发生的流程节点。")
            appendLine("- 使用第三人称限知视角和自然现代中文。每句话至少带来一个可看到、听到、触到的动作、对白、物件变化或新信息。")
            appendLine("- 删除作者结论、心理包装、流程讲解和应用元信息。不要解释动作的意义、效率、阶段或信号，直接写动作及现场结果。")
            appendLine("- 同一个动作只写一次；屏幕反馈只保留最终变化，不逐帧描写按键、光标、进度条、数据流或系统执行。")
            appendLine("- 所有引号内对白及其说话者必须原样保留。不得新增主角对白、选项、承诺、签字、付款、转账或其他决定，也不得把NPC提出的问题改写成主角已经回答。")
            appendLine("- narrative 保持 3-6 段和 1000-1500 个中文字符。写完逐句检查：删掉后不损失事实、对白或选择的句子必须删除。")
            appendLine("- 完成 narrative 后，依据新正文重建 summary、action_coverage、forward_hook、ending_scene 和 introduced_characters。evidence 必须逐字摘自新 narrative，不能复用同一句。")
            appendLine("- 只输出一个完整合法 JSON 对象，不要 Markdown、说明或注释。")
            appendLine()
            appendLine("【待删改 JSON】")
            appendLine(rawResponse.take(8000))
            appendLine()
            appendLine("【输出格式】")
            appendLine("""{"narrative":"3-6段、1000-1500个中文字符的删改后小说正文","summary":"一句话记录正文事实和新变化","action_coverage":[{"action":"用户的一个原子动作","evidence":"从新 narrative 逐字摘录的连续原句"}],"forward_hook":{"cause":"由既有因果触发的新变化","evidence":"最后一个用户动作之后的新正文原句"},"ending_scene":{"location":"正文结尾的实际地点","present_characters":["同场NPC真实姓名"]},"introduced_characters":[{"name":"首次登场者真实姓名","occupation":"身份","relationship_to_player":"与主角关系","first_appearance":"新正文中的首次登场事实"}]}""")
        }
    }

    fun buildNarrativeLengthExpansionPrompt(
        rawResponse: String,
        issue: String
    ): String {
        return buildString {
            appendLine("【任务】")
            appendLine("你是中文小说扩写编辑。下方 JSON 的剧情方向与事实正确，但 narrative 明显不足 1000 个中文字符。请把正文扩写到 1100-1300 个中文字符，不能原样返回。")
            appendLine("检测结果：$issue。")
            appendLine()
            appendLine("【扩写规则】")
            appendLine("- 保留原有全部动作、对白、姓名、物品、地点、结果、因果顺序和结尾状态，不删除或压缩原文中的有效内容。")
            appendLine("- 在现有事件内部补足可感知的施行动作、环境反馈、NPC即时反应、信息核对和自然衔接；主动续写只能延展原文已启动的直接后果。")
            appendLine("- 不新增主角的重大决定、承诺、签字、付款、关系选择或原文没有发生的结果；不要用回忆、空泛心理、重复动作和景物堆砌凑字数。")
            appendLine("- 使用第三人称限知视角与自然现代中文，保持 4-6 段。每段都必须带来动作、对白、物件变化、现场反馈或新信息。")
            appendLine("- 所有引号内对白及说话者原样保留；可以在对白前后补足对方可观察的动作与反应。")
            appendLine("- 写完后先计算 narrative 的非空白字符数：少于 1100 或多于 1300 都要继续修改，达到范围后才输出。")
            appendLine("- 根据扩写后的正文重建 summary、action_coverage、forward_hook、ending_scene 和 introduced_characters；evidence 必须逐字摘自新 narrative。")
            appendLine("- 只输出一个完整合法 JSON 对象，不要 Markdown、解释、注释或字数报告。")
            appendLine()
            appendLine("【待扩写 JSON】")
            appendLine(rawResponse.take(8000))
            appendLine()
            appendLine("【输出格式】")
            appendLine("""{"narrative":"4-6段、1100-1300个中文字符的扩写后小说正文","summary":"一句话记录正文事实和新变化","action_coverage":[{"action":"用户的一个原子动作","evidence":"从新 narrative 逐字摘录的连续原句"}],"forward_hook":{"cause":"由本轮既有因果触发的新变化","evidence":"最后一个用户动作之后的新正文原句"},"ending_scene":{"location":"正文结尾的实际地点","present_characters":["同场NPC真实姓名"]},"introduced_characters":[{"name":"首次登场者真实姓名","occupation":"身份","relationship_to_player":"与主角关系","first_appearance":"新正文中的首次登场事实"}]}""")
        }
    }

    /**
     * 构建状态调用的提示词（单个NPC，对齐设计文档 4.4 节）
     */
    fun buildCharacterStatePrompt(
        worldSetting: WorldSetting,
        character: CharacterCard,
        recentNarratives: List<NarrativeEntry>,
        narrative: String,
        userMessage: String,
        playerName: String = "主角",
        updateReason: String = "",
        recoveryNarratives: List<NarrativeEntry> = emptyList()
    ): String {
        val sb = StringBuilder()

        sb.appendLine("【任务】")
        sb.appendLine("你是角色动态状态同步器。根据本轮实际交互，为 ${character.name} 生成增量补丁。")
        sb.appendLine("- 只更新正文有依据的变化；姓名及 manual 字段不得改写。职业、性格、背景、经历和能力不能因普通寒暄改写，但重大事件、反复行为或明确成长形成稳定新事实时必须通过 attributes 刷新。")
        sb.appendLine("- physical_state 与 mental_state 中只写改变字段，未变化字段省略；取值遵守 character_fields 对同 key 的范围、选项和语义。")
        sb.appendLine("- attributes 使用【世界整体设定.character_fields】中的 key。dynamic 字段有明确即时变化时更新；event 字段只有本轮形成可持续变化时更新；manual 字段不得输出。")
        sb.appendLine("- 逐项检查 dynamic 字段的 description 和世界 rules；本轮触发消耗、恢复、累积、缓解、暴露或受损条件时必须输出更新后的最终值，不能因变化较小或动作成功而省略。")
        sb.appendLine("- 人物特征、经历、外貌、能力和自定义字段在正文确有长期改变时必须刷新；重大事件可直接形成经历，性格变化通常需要重大选择或反复行为证据。不得每轮机械改写，也不得因为旧卡已有内容就永远忽略新经历。")
        sb.appendLine("- relationship_to_player 表示对 $playerName 的变化，favor_delta 与 trust_delta 是本轮增减量，不是最终总值，通常控制在 -10 到 10。")
        sb.appendLine("- inventory_changes 只记录本轮明确获得或失去的物品。")
        sb.appendLine("- 补同步时必须以【角色当前状态】为基准；已经体现在当前状态中的关系或物品变化不得重复累计。")
        sb.appendLine("- 只输出一个合法 JSON 对象，不要代码块、解释、注释或省略号。")
        sb.appendLine()

        if (updateReason.isNotBlank()) {
            sb.appendLine("【本次更新原因】")
            sb.appendLine(updateReason.trim())
            sb.appendLine()
        }

        sb.appendLine("【世界整体设定】")
        sb.appendLine(gson.toJson(LorePromptSnapshotPolicy.world(worldSetting)))
        sb.appendLine()

        sb.appendLine("【角色当前状态】")
        sb.appendLine(gson.toJson(characterStateSnapshot(character, playerName)))
        sb.appendLine()

        if (recentNarratives.isNotEmpty()) {
            sb.appendLine("【最近对话】")
            recentNarratives.takeLast(2).forEach { entry ->
                sb.appendLine("第${entry.turn}轮：用户=${entry.userInput.take(240)}；结果=${entry.narrative.take(500)}")
            }
            sb.appendLine()
        }

        if (recoveryNarratives.isNotEmpty()) {
            sb.appendLine("【待补同步的角色相关剧情】")
            recoveryNarratives.forEach { entry ->
                sb.appendLine("第${entry.turn}轮：用户=${entry.userInput.take(320)}；正文=${entry.narrative.take(900)}")
            }
            sb.appendLine("只补充其中尚未反映在角色当前状态里的变化。")
            sb.appendLine()
        }

        sb.appendLine("【交互内容】")
        sb.appendLine("用户输入：$userMessage")
        sb.appendLine("AI 回复：$narrative")
        sb.appendLine()

        sb.appendLine("【输出模板】")
        sb.appendLine("""{"emotion":null,"physical_state":null,"mental_state":null,"attributes":{},"relationship_to_player":{"favor_delta":0,"trust_delta":0,"stage":null},"inventory_changes":[]}""")

        return sb.toString()
    }

    private fun characterNarrativeSnapshot(card: CharacterCard): Map<String, Any> {
        return mapOf(
            "id" to card.id,
            "name" to card.name,
            "occupation" to card.occupation,
            "personality" to card.personality,
            "appearance" to card.appearance,
            "background" to card.background,
            "lore" to LorePromptSnapshotPolicy.character(card.lore),
            "emotion" to card.emotion,
            "physical_state" to card.physicalState,
            "mental_state" to card.mentalState,
            "custom_attributes" to card.customAttributes,
            "relationships" to card.relationships
        )
    }

    private fun characterTaskSnapshot(card: CharacterCard): Map<String, Any> {
        return mapOf(
            "name" to card.name,
            "occupation" to card.occupation,
            "status" to card.status,
            "emotion" to card.emotion,
            "physical_state" to card.physicalState,
            "mental_state" to card.mentalState,
            "custom_attributes" to card.customAttributes,
            "relationships" to card.relationships
        )
    }

    private fun characterStateSnapshot(card: CharacterCard, playerName: String): Map<String, Any> {
        val relationship = card.relationships[playerName]
            ?: Relationship(favor = card.favor, stage = "陌生人")
        return mapOf(
            "name" to card.name,
            "occupation" to card.occupation,
            "emotion" to card.emotion,
            "physical_state" to card.physicalState,
            "mental_state" to card.mentalState,
            "custom_attributes" to card.customAttributes,
            "relationship_to_player" to relationship,
            "inventory" to card.inventory
        )
    }

    private fun worldbookNarrativeSnapshot(entry: WorldbookEntry): Map<String, Any> {
        return mapOf(
            "id" to entry.id,
            "type" to entry.type,
            "name" to entry.name,
            "description" to entry.description.take(800),
            "status" to entry.status,
            "related_characters" to entry.relatedCharacters,
            "is_secret" to entry.isSecret,
            "known_by_player" to entry.knownByPlayer
        )
    }

    private fun worldbookTaskSnapshot(entry: WorldbookEntry): Map<String, Any> {
        return mapOf(
            "id" to entry.id,
            "type" to entry.type,
            "name" to entry.name,
            "status" to entry.status
        )
    }
}
