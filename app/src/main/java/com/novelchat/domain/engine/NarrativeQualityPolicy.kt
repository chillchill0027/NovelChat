package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.NarrativeResult

internal object NarrativeQualityPolicy {

    private val actionSeparators = Regex(
        pattern = "(?:[,，；;。.!！?？]+\\s*|然后|接着|随后|继而|(?:之后|以后)(?:再)?(?=去|到|给|把|将|向|对|拿|买|卖|打|发|问|说|做|看|读|写|开|关|回|联系|支付|领取|检查|确认|处理|安置|照顾|等待)|再(?=去|到|给|把|将|向|对|拿|买|卖|打|发|问|说|做|看|读|写|开|关|回|联系|支付|领取|检查|确认|处理|安置|照顾|等待)|最后(?=去|到|给|把|将|向|对|拿|买|卖|打|发|问|说|做|看|读|写|开|关|回|联系|支付|领取|检查|确认|处理|安置|照顾|等待)|并(?:且)?(?=去|到|给|把|将|向|对|拿|买|卖|打|发|问|说|做|看|读|写|开|关|回|联系|支付|领取|检查|确认|处理|安置|照顾|等待)|\\b(?:and\\s+then|then|after\\s+that|next)\\b)",
        option = RegexOption.IGNORE_CASE
    )
    private val quotedText = Regex("“[^”]*”|\"[^\"]*\"|'[^']*'")
    private val paragraphSeparator = Regex("\\n\\s*\\n")
    private val stiffNarration = Regex(
        "他决定将注意力转向|[^。！？\\n]{0,24}让他意识到|值得注意的是|显而易见|综上所述|" +
            "作为一个[^，。]{0,12}的人|接下来[，,]他"
    )
    private val mechanicalSentenceTransition = Regex(
        "(?:^|[。！？!?\\n])\\s*(?:然后|接着|随后|最后)[，,]?",
        setOf(RegexOption.MULTILINE)
    )
    private val abstractMotivationNarration = Regex(
        "使命(?:感|驱动)|明确(?:的)?目的性|行动力|步频|半个节奏|身体语言|目标所支配|" +
            "目标只有一个|兴奋感[^。！？]{0,18}(?:提升|提高|转化|取代|驱动)|" +
            "完成了从[^。！？]{0,18}到[^。！？]{0,18}的转变|三根支柱|刻进视网膜|" +
            "(?:任务|行动)的心弦|抽象的目标[^。！？]{0,24}具象化|最有力的鼓点|" +
            "内心(?:的)?[^。！？]{0,12}(?:兴奋|激动)感|巨大(?:的)?冲击感|引导性的姿态|" +
            "口中[^。！？]{0,24}(?:问候|指引)"
    )
    private val interpretiveProcessNarration = Regex(
        "如同(?:一个|一枚|一道)?[^。！？]{0,12}(?:精准|明确)[^。！？]{0,8}(?:开关|信号|指令)|" +
            "被动(?:的)?(?:观望|等待)状态|(?:宏大|庄重|秩序)[^。！？]{0,18}(?:感|氛围)|" +
            "眼中闪过[^。！？]{0,16}(?:认可|高效)|" +
            "(?:引导|回应|动作)(?:是|显得)[^。！？]{0,20}(?:流程化|主动且|高效)|" +
            "带着明确(?:的)?(?:目标|目的)|每一步[^。！？]{0,18}(?:沉稳|有力)|" +
            "目光(?:始终)?(?:锁定|死死地?盯着)|没有再多想[^。！？]{0,14}(?:立刻|马上)"
    )
    private val editorialExplanationNarration = Regex(
        "步伐[^。！？\\n]{0,60}(?:节奏|速度)|" +
            "既不显得[^。！？\\n]{0,36}(?:又|也不会)(?:显得)?|" +
            "确保自己(?:能|可以)|" +
            "(?:话语|询问|回应)[^。！？\\n]{0,24}(?:将|把)(?:焦点|注意力)" +
            "[^。！？\\n]{0,24}(?:转移|转向)|" +
            "(?:高效|专业)[^。！？\\n]{0,16}(?:气息|氛围)|" +
            "(?:兴奋感|激动感|信任感?)[^。！？\\n]{0,36}(?:覆盖|包裹|笼罩)|" +
            "获得了?[^。！？\\n]{0,20}(?:许可|认可)[^。！？\\n]{0,16}(?:参与者|资格|身份)|" +
            "(?:最终|于是)?决定(?:直接|立刻|马上)?(?:回应|开口|行动)|" +
            "似乎(?:想|要)[^。！？\\n]{0,28}(?:流程引导|进行[^。！？\\n]{0,10}(?:引导|交流))"
    )
    private val appRoundMetaNarration = Regex(
        "第\\s*\\d+\\s*轮(?:那种|行动|剧情|里(?:的)?|中(?:的)?|时(?:的)?感受)|" +
            "(?:上一|上个|前一|本|这一)轮(?:剧情|行动|里(?:的)?|中(?:的)?|那种)|" +
            "(?:游戏|剧情)(?:回合|进度)|玩家(?:输入|选择)"
    )
    private val syntheticAbstractionNarration = Regex(
        "不容置疑(?:的)?[^。！？\\n]{0,10}(?:肯定|决心|态度|意味)|" +
            "没有丝毫(?:犹豫|迟疑)|" +
            "(?:稳健|沉稳)[^。！？\\n]{0,8}(?:有力|坚定)|" +
            "(?:动作|反应|话语|声音|语气)(?:如同|仿佛|带着|显得)|" +
            "(?:如同|仿佛)[^。！？\\n]{0,22}(?:机械|机器|程序|信号|指令|接收者)|" +
            "似乎(?:是|在)?[^。！？\\n]{0,26}(?:指令|信号)[^。！？\\n]{0,12}(?:接收者|执行者)|" +
            "(?:精准|精确)[^。！？\\n]{0,18}(?:落位|锁定|执行|完成)|" +
            "(?:亢奋|兴奋|紧张|冲动)[^。！？\\n]{0,36}(?:压制|宣泄口|出口|覆盖)|" +
            "(?:职业化|专业化)[^。！？\\n]{0,14}(?:专注度|态度|气场)|" +
            "(?:流程|行动)[^。！？\\n]{0,22}(?:所带动|主导权|焦点|指令)|" +
            "(?:主导权|焦点)[^。！？\\n]{0,18}(?:交还|转移|锁定)|" +
            "眼神[^。！？\\n]{0,18}(?:流露|透露)[^。！？\\n]{0,26}(?:复杂|羡慕|期待|情绪)|" +
            "(?:明确|清晰)(?:的)?(?:宣泄口|行动指令|阶段)|" +
            "确认(?:信息|动作)[^。！？\\n]{0,12}(?:到位|预期)"
    )
    private val expositoryNarration = Regex(
        "(?:不是|不再是)[^。！？\\n]{0,52}而是|" +
            "(?:一种|某种)[^。！？\\n]{0,22}(?:意味|状态|姿态|气息|感觉|邀请|支持)|" +
            "(?:这|那|它)(?:代表着?|意味着?|说明|显示出?)|" +
            "(?:像是|如同|仿佛|好像)[^。！？\\n]{0,38}|" +
            "(?:似乎|显然|显得)(?:是|在|更加|更)?|" +
            "视觉上|" +
            "(?:流程|阶段|环节)[^。！？\\n]{0,26}(?:启动|进入|推进|提供|完成|变化|支持|主导)|" +
            "(?:捕捉|察觉)[^。！？\\n]{0,22}(?:变化|信号|意味)|" +
            "(?:身体|语气|目光|动作)[^。！？\\n]{0,20}(?:放松度|档次|状态|意味)|" +
            "职业性[^。！？\\n]{0,18}(?:感|度|状态)|" +
            "注意力[^。！？\\n]{0,18}(?:集中|转移|聚焦)|" +
            "(?:工作|等待|观察)[^。！？\\n]{0,12}状态[^。！？\\n]{0,18}(?:激活|进入)"
    )
    private val stylePaddingToken = Regex(
        "似乎|仿佛|如同|好像|一种|某种|不易察觉|略微|微微|轻微|死死|紧锁|" +
            "弥漫|屏息|姿态|氛围|注意力|兴奋感|亢奋感|职业化|专业化|恰到好处|" +
            "宣泄口|主导权|接收者|行动指令|具体(?:而|和|、)?可控|拉伸|提纯|" +
            "专注度|聚焦感|意味着|代表着"
    )
    private val nominalizedCommentaryNarration = Regex(
        "(?:松弛|兴奋|亢奋|推进|引导|确定|专业|职业|阶段|确认|许可|邀请|欢迎|开放|高效)" +
            "[^。！？\\n]{0,10}(?:感|性|度|信号|批准|状态|姿态|引导)"
    )
    private val interfaceProcessCommentary = Regex(
        "(?:这个|这一|该)(?:动作|变化|操作)[^。！？\\n]{0,28}(?:宣告|标志|意味着|相当于)|" +
            "(?:这|那|它)(?:标志着|相当于)|" +
            "(?:决策|确认|流程|执行|资金划拨|系统)[^。！？\\n]{0,26}(?:指令|阶段|切换|信号)|" +
            "(?:输入框|加载指示条|进度条|数据流|金额数字)[^。！？\\n]{0,34}" +
            "(?:激活|稳定|持续|闪烁|跳动)|" +
            "给系统发出[^。！？\\n]{0,20}(?:信号|指令)|" +
            "系统[^。！？\\n]{0,36}(?:接收|处理|执行|验证)[^。！？\\n]{0,18}(?:指令|阶段)"
    )
    private val overDirectedMicroAction = Regex(
        "干净利落|清晰地(?:锁定|确认)|精准地(?:按下|点击|敲击)|" +
            "身体[^。！？\\n]{0,18}(?:微微|轻轻)[^。！？\\n]{0,18}(?:倾斜|侧向)|" +
            "目光[^。！？\\n]{0,22}(?:紧盯|锁定)[^。！？\\n]{0,18}(?:数据流|进度条|屏幕)|" +
            "(?:职业化|标准化)[^。！？\\n]{0,12}(?:表情|神情)|" +
            "(?:轻微|明显)[^。！？\\n]{0,10}(?:放松感|紧张感)|" +
            "稳定的[、,，]?持续的"
    )
    private val continuationRequests = setOf(
        "继续",
        "继续剧情",
        "继续故事",
        "继续推进",
        "继续推进剧情",
        "继续推进故事",
        "继续推进当前目标",
        "推进当前目标",
        "接着",
        "接着来",
        "下一步",
        "continue",
        "goon",
        "keepgoing"
    )
    private val interactionVerbs =
        "说|问|答|开口|回应|回复|递|接|点头|摇头|笑|皱眉|愣|惊|核对|检查|招呼|叫住|拦住|撞上|撞到|发来|打来"
    private val lotteryOutcomeSignal = Regex(
        "彩票|兑奖|中奖|lottery|lotto|jackpot",
        RegexOption.IGNORE_CASE
    )
    private val lifeChangingAmountSignal = Regex(
        "一\\s*亿|[1-9]\\d*\\s*亿|100\\s*(?:million|m\\b)|100[，,]?000[，,]?000",
        RegexOption.IGNORE_CASE
    )
    private val observerOutcomeReaction = Regex(
        "震惊|惊讶|愣|瞪大|倒吸|失声|惊呼|手(?:指)?(?:停|抖)|声音发紧|" +
            "froze|stunned|shocked|gasped",
        RegexOption.IGNORE_CASE
    )
    private val majorOutcomeVerification = Regex(
        "反复核对|重新核对|再次核对|二次(?:核验|复核)|(?:请|叫来)(?:主管|负责人)|" +
            "锁住终端|启动(?:核验|复核)|出示身份证|兑奖中心|核验手续|" +
            "doublecheck|verify|verification|supervisor",
        RegexOption.IGNORE_CASE
    )
    private val roleSignals = listOf(
        RoleSignal("店主", listOf("老板", "店主", "店老板")),
        RoleSignal("店员", listOf("店员", "服务员", "工作人员", "前台", "接待")),
        RoleSignal("邻居", listOf("邻居", "居民")),
        RoleSignal("房东", listOf("房东")),
        RoleSignal("医护", listOf("医生", "护士")),
        RoleSignal("安保", listOf("保安")),
        RoleSignal("司机", listOf("司机")),
        RoleSignal(
            "陌生人物",
            listOf("中年男人", "中年女人", "年轻男人", "年轻女人", "男人", "女人", "男子", "女子", "陌生人", "那人", "路人"),
            listOf("stranger", "a man", "a woman", "中年男人", "中年女人", "年轻男人", "年轻女人", "男人", "女人", "男子", "女子", "陌生人", "那人", "路人")
        ),
        RoleSignal(
            "伴侣",
            listOf("女朋友", "女友", "男朋友", "男友", "妻子", "丈夫", "老婆", "老公", "恋人", "伴侣", "恋爱中", "已婚", "配偶"),
            listOf("girlfriend", "boyfriend", "wife", "husband", "partner", "女朋友", "女友", "男朋友", "男友", "妻子", "丈夫", "老婆", "老公", "恋人", "伴侣")
        )
    )
    private val styleIssueMarkers = listOf(
        "报告腔",
        "动作清单",
        "使命感或步伐节奏",
        "行为姿态",
        "编辑式解释",
        "应用元信息",
        "抽象包装",
        "说明书式旁白",
        "修饰填充",
        "名词化评价",
        "界面流程解说",
        "逐帧拆解",
        "正文低于目标篇幅",
        "正文超过目标篇幅"
    )

    fun coverageIssue(
        result: NarrativeResult,
        userMessage: String,
        knownCharacters: List<CharacterCard> = emptyList(),
        recentNarratives: List<NarrativeEntry> = emptyList(),
        currentGoal: String = "",
        playerName: String = "主角",
        enforceTargetLength: Boolean = false
    ): String? {
        val narrative = result.narrative.trim()
        if (narrative.isBlank()) return "正文为空"

        val issues = mutableListOf<String>()
        if (enforceTargetLength) {
            targetLengthIssue(narrative)?.let(issues::add)
        }
        val expectedActions = atomicActionHints(userMessage, currentGoal).size
        val coverage = result.actionCoverage.filter { it.action.isNotBlank() || it.evidence.isNotBlank() }
        if (result.actionCoverage.any { it.action.isBlank() || it.evidence.isBlank() }) {
            issues += "动作覆盖记录必须同时包含 action 与 evidence"
        }
        if (coverage.size < expectedActions) {
            issues += "用户输入约含 $expectedActions 个连续动作，但动作覆盖记录只有 ${coverage.size} 项"
        }

        val evidenceAnchors = coverage.map { findEvidenceAnchor(narrative, it.evidence) }
        if (evidenceAnchors.any { it == null }) {
            issues += "动作覆盖证据并非正文中的原句"
        }
        if (evidenceAnchors.filterNotNull().distinct().size < coverage.size) {
            issues += "多个动作复用了同一处正文证据"
        }
        val orderedAnchors = evidenceAnchors.filterNotNull()
        if (
            orderedAnchors.size == evidenceAnchors.size &&
            orderedAnchors.zipWithNext().any { (previous, next) -> previous.start >= next.start }
        ) {
            issues += "动作覆盖证据没有按照用户行动的原顺序出现"
        }
        val minimumLength = maxOf(100, expectedActions * 45)
        if (narrative.length < minimumLength) {
            issues += "正文过短，用户动作的过程、反馈和后续变化不足"
        }
        val paragraphCount = narrative.split(paragraphSeparator).count { it.isNotBlank() }
        val requiredParagraphs = expectedActions.coerceIn(2, 4)
        if (paragraphCount < requiredParagraphs) {
            issues += "正文只有 $paragraphCount 段，$expectedActions 个连续动作至少需要 $requiredParagraphs 段来展开过程与反馈"
        }
        if (repeatsPriorNarrative(narrative, recentNarratives.lastOrNull()?.narrative.orEmpty())) {
            issues += "正文大段复述上一轮，续写必须从上一轮结尾之后产生新事实"
        }
        if (
            isContinuationRequest(userMessage) &&
            repeatsPriorDialogue(narrative, recentNarratives.lastOrNull()?.narrative.orEmpty())
        ) {
            issues += "正文重复上一轮已经说过的对白，续写必须从对白之后的新反应或新事实开始"
        }

        val hookEvidence = result.forwardHook.evidence
        if (result.forwardHook.cause.isBlank()) {
            issues += "forward_hook 缺少由正文事实触发的 cause"
        }
        if (hookEvidence.isBlank()) {
            issues += "缺少承接用户动作后的主动续写证据 forward_hook"
        } else {
            val hookAnchor = findEvidenceAnchor(narrative, hookEvidence)
            if (hookAnchor == null) {
                issues += "forward_hook 证据并非正文中的原句"
            } else {
                evidenceAnchors.filterNotNull().maxOfOrNull { it.end }?.let { lastActionEnd ->
                    if (hookAnchor.start < lastActionEnd || hookAnchor in evidenceAnchors.filterNotNull()) {
                        issues += "主动续写必须出现在最后一个用户动作之后，且不能复用动作证据"
                    }
                }
            }
        }
        issues += highImpactOutcomeIssues(
            result = result,
            userMessage = userMessage,
            narrative = narrative,
            evidenceAnchors = evidenceAnchors,
            knownCharacters = knownCharacters,
            playerName = playerName
        )
        if (result.endingScene.location.isBlank()) {
            issues += "缺少正文结尾实际地点 ending_scene.location"
        }
        characterContinuityIssue(result, userMessage, knownCharacters, narrative)?.let(issues::add)
        NarrativeAgencyPolicy.issue(
            narrative = narrative,
            playerName = playerName,
            continuationRequest = isContinuationRequest(userMessage),
            authorizationContext = currentGoal
        )?.let(issues::add)
        val stiffCount = stiffNarration.findAll(narrative).count()
        if (stiffCount >= 2) {
            issues += "正文有 $stiffCount 处重复的报告腔或解释腔表达"
        }
        val transitionCount = mechanicalSentenceTransition.findAll(narrative).count()
        if (transitionCount >= 3) {
            issues += "正文有 $transitionCount 处顺序连接词起句，读起来像动作清单"
        }
        val motivationCount = abstractMotivationNarration.findAll(narrative).count()
        if (motivationCount >= 3) {
            issues += "正文有 $motivationCount 处解释目标、使命感或步伐节奏，必须改写为实际行动与外部反馈"
        }
        val interpretiveCount = interpretiveProcessNarration.findAll(narrative).count()
        if (interpretiveCount >= 3) {
            issues += "正文有 $interpretiveCount 处替人物解释效率、目标或行为姿态，必须改写为动作、对白与流程反馈"
        }
        val narrationOnly = quotedText.replace(narrative, "")
        val editorialCount = editorialExplanationNarration.findAll(narrationOnly).count()
        if (editorialCount >= 2) {
            issues += "正文有 $editorialCount 处编辑式解释，把步伐节奏、焦点转移、人物气息或内心变化说破了，必须改成可见动作与现场反馈"
        }
        if (appRoundMetaNarration.containsMatchIn(narrationOnly)) {
            issues += "正文直接引用第几轮、上一轮或玩家等应用元信息，破坏故事沉浸感"
        }
        val syntheticCount = syntheticAbstractionNarration.findAll(narrationOnly).count()
        if (syntheticCount >= 3) {
            issues += "正文有 $syntheticCount 处抽象包装，用肯定性、气场、接收者、宣泄口、主导权或行动指令代替现场事实，必须删去作者结论"
        }
        val expositoryCount = expositoryNarration.findAll(narrationOnly).count()
        if (expositoryCount >= 5) {
            issues += "正文有 $expositoryCount 处说明书式旁白，反复解释意味、状态、流程信号或注意力，必须只保留人物可观察到的变化"
        }
        val paddingCount = stylePaddingToken.findAll(narrationOnly).count()
        val paddingLimit = maxOf(6, narrative.length / 140)
        if (paddingCount >= paddingLimit) {
            issues += "正文有 $paddingCount 处高密度修饰填充，似乎、仿佛、姿态、氛围、注意力或职业化等词挤占了实际事件"
        }
        val nominalizedCount = nominalizedCommentaryNarration.findAll(narrationOnly).count()
        if (nominalizedCount >= 3) {
            issues += "正文有 $nominalizedCount 处名词化评价，用松弛感、阶段性批准、推进感或确认信号解释普通动作"
        }
        val interfaceCommentaryCount = interfaceProcessCommentary.findAll(narrationOnly).count()
        if (interfaceCommentaryCount >= 2) {
            issues += "正文有 $interfaceCommentaryCount 处界面流程解说，把按键、进度条、数据流或系统反馈解释成指令与阶段，必须只写一次最终结果"
        }
        val microActionCount = overDirectedMicroAction.findAll(narrationOnly).count()
        if (microActionCount >= 3) {
            issues += "正文有 $microActionCount 处逐帧拆解普通动作，用精准按键、紧盯屏幕、身体倾斜或职业化表情拖慢剧情"
        }
        return issues.distinct().take(8).joinToString("；").takeIf { it.isNotBlank() }
    }

    fun estimateActionCount(userMessage: String): Int {
        return atomicActionHints(userMessage).size
    }

    fun issueCount(
        result: NarrativeResult,
        userMessage: String,
        knownCharacters: List<CharacterCard> = emptyList(),
        recentNarratives: List<NarrativeEntry> = emptyList(),
        currentGoal: String = "",
        playerName: String = "主角"
    ): Int {
        return coverageIssue(result, userMessage, knownCharacters, recentNarratives, currentGoal, playerName)
            ?.split('；')
            ?.count { it.isNotBlank() }
            ?: 0
    }

    fun issueSeverityScore(issueText: String?): Int {
        return issueText
            ?.split('；')
            ?.filter { it.isNotBlank() }
            ?.sumOf(::issueSeverity)
            ?: 0
    }

    fun isRepairImprovement(currentIssue: String?, repairedIssue: String?): Boolean {
        if (repairedIssue == null) return true
        if (currentIssue == null) return false
        val currentSeverity = issueSeverityScore(currentIssue)
        val repairedSeverity = issueSeverityScore(repairedIssue)
        return repairedSeverity < currentSeverity ||
            (repairedSeverity == currentSeverity && issueTextCount(repairedIssue) < issueTextCount(currentIssue))
    }

    fun isTargetLengthImprovement(
        currentNarrative: String,
        repairedNarrative: String,
        currentIssue: String?,
        repairedIssue: String?
    ): Boolean {
        if (!hasOnlyTargetLengthIssue(currentIssue) || !hasOnlyTargetLengthIssue(repairedIssue)) {
            return false
        }
        return targetLengthDistance(repairedNarrative) < targetLengthDistance(currentNarrative)
    }

    fun containsTargetLengthIssue(issueText: String?): Boolean =
        issueText?.split('；')
            ?.filter { it.isNotBlank() }
            ?.any { it.contains("正文低于目标篇幅") || it.contains("正文超过目标篇幅") }
            ?: false

    fun isNonBlockingLengthIssue(issueText: String?): Boolean =
        containsTargetLengthIssue(issueText) && !containsBlockingIssue(issueText)

    fun isStyleOnlyIssue(issueText: String?): Boolean {
        val issues = issueText
            ?.split('；')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return issues.isNotEmpty() && issues.all { issue ->
            styleIssueMarkers.any(issue::contains)
        }
    }

    fun containsAgencyViolation(issueText: String?): Boolean {
        return issueText?.contains(NarrativeAgencyPolicy.UNAUTHORIZED_DECISION_ISSUE) == true
    }

    fun containsContinuationRepetition(issueText: String?): Boolean {
        return issueText?.contains("复述上一轮") == true ||
            issueText?.contains("重复上一轮已经说过的对白") == true
    }

    fun containsBlockingIssue(issueText: String?): Boolean {
        return issueText
            ?.split('；')
            ?.filter { it.isNotBlank() }
            ?.any { issue -> styleIssueMarkers.none(issue::contains) }
            ?: false
    }

    fun targetLengthIssue(narrative: String): String? {
        val characterCount = narrative.count { !it.isWhitespace() }
        return when {
            characterCount < TARGET_NARRATIVE_MIN_CHARS ->
                "正文低于目标篇幅：当前约 $characterCount 字，目标为 $TARGET_NARRATIVE_MIN_CHARS-$TARGET_NARRATIVE_MAX_CHARS 字"
            characterCount > TARGET_NARRATIVE_MAX_CHARS ->
                "正文超过目标篇幅：当前约 $characterCount 字，目标为 $TARGET_NARRATIVE_MIN_CHARS-$TARGET_NARRATIVE_MAX_CHARS 字"
            else -> null
        }
    }

    private fun hasOnlyTargetLengthIssue(issueText: String?): Boolean {
        val issues = issueText?.split('；')?.filter { it.isNotBlank() }.orEmpty()
        return issues.isNotEmpty() && issues.all {
            it.contains("正文低于目标篇幅") || it.contains("正文超过目标篇幅")
        }
    }

    private fun targetLengthDistance(narrative: String): Int {
        val characterCount = narrative.count { !it.isWhitespace() }
        return when {
            characterCount < TARGET_NARRATIVE_MIN_CHARS ->
                TARGET_NARRATIVE_MIN_CHARS - characterCount
            characterCount > TARGET_NARRATIVE_MAX_CHARS ->
                characterCount - TARGET_NARRATIVE_MAX_CHARS
            else -> 0
        }
    }

    fun atomicActionHints(userMessage: String, currentGoal: String = ""): List<String> {
        if (isContinuationRequest(userMessage) && currentGoal.isNotBlank()) {
            return listOf("推动当前目标取得实质进展：${currentGoal.trim().take(160)}")
        }
        val clauses = quotedText.replace(userMessage, "说出指定对话")
            .split(actionSeparators)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(6)
        return clauses.ifEmpty { listOf(userMessage.trim().ifBlank { "继续当前行动" }) }
    }

    fun isContinuationRequest(userMessage: String): Boolean {
        val normalized = userMessage
            .lowercase()
            .filter { it.isLetterOrDigit() }
        return normalized in continuationRequests
    }

    fun majorOutcomeAmountAnchor(userMessage: String): String? {
        if (!lotteryOutcomeSignal.containsMatchIn(userMessage)) return null
        val amount = extractMajorAmountValues(userMessage).firstOrNull() ?: return null
        return when (amount) {
            100_000_000L -> "一亿元（人民币100,000,000元）"
            else -> "%,d元".format(java.util.Locale.US, amount)
        }
    }

    private fun repeatsPriorNarrative(narrative: String, previousNarrative: String): Boolean {
        val current = normalizeProofText(narrative).lowercase()
        val previous = normalizeProofText(previousNarrative).lowercase()
        if (current.length < 160 || previous.length < 160) return false

        val shingleSize = 12
        val currentShingles = current.windowed(shingleSize).toHashSet()
        val previousShingles = previous.windowed(shingleSize).toHashSet()
        if (currentShingles.isEmpty() || previousShingles.isEmpty()) return false

        val sharedCount = currentShingles.count(previousShingles::contains)
        val repeatedRatio = sharedCount.toDouble() / currentShingles.size
        return sharedCount >= 24 && repeatedRatio >= 0.08
    }

    private fun repeatsPriorDialogue(narrative: String, previousNarrative: String): Boolean {
        if (previousNarrative.isBlank()) return false
        val normalizedPrevious = normalizeProofText(previousNarrative)
        return quotedText.findAll(narrative)
            .map { match ->
                match.value.trim('“', '”', '‘', '’', '"', '\'').trim()
            }
            .filter { dialogue ->
                dialogue.count(Char::isLetter) >= 6 &&
                    normalizeProofText(dialogue).length >= 8
            }
            .map(::normalizeProofText)
            .any(normalizedPrevious::contains)
    }

    private fun issueSeverity(issue: String): Int {
        val styleOccurrences = Regex("(\\d+) 处")
            .find(issue)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(0, 20)
            ?: 0
        val stylePenalty = 25 + styleOccurrences
        return when {
            issue.contains("正文为空") -> 1_000
            issue.contains("未获用户授权的重大决定") -> 240
            issue.contains("复述上一轮") -> 180
            issue.contains("重复上一轮已经说过的对白") -> 170
            issue.contains("真实姓名") || issue.contains("introduced_characters") ||
                issue.contains("首次登场") || issue.contains("结尾在场人物") -> 150
            issue.contains("动作覆盖") || issue.contains("正文证据") ||
                issue.contains("原顺序") || issue.contains("forward_hook") ||
                issue.contains("最后一个用户动作之后") || issue.contains("ending_scene") ||
                issue.contains("高影响结果") -> 130
            issue.contains("正文过短") || issue.contains("段来展开") -> 80
            issue.contains("应用元信息") -> 120
            issue.contains("报告腔") || issue.contains("动作清单") ||
                issue.contains("使命感或步伐节奏") || issue.contains("行为姿态") ||
                issue.contains("编辑式解释") || issue.contains("抽象包装") ||
                issue.contains("说明书式旁白") || issue.contains("修饰填充") ||
                issue.contains("名词化评价") || issue.contains("界面流程解说") ||
                issue.contains("逐帧拆解") || issue.contains("目标篇幅") -> stylePenalty
            else -> 100
        }
    }

    private fun issueTextCount(issueText: String): Int {
        return issueText.split('；').count { it.isNotBlank() }
    }

    private fun highImpactOutcomeIssues(
        result: NarrativeResult,
        userMessage: String,
        narrative: String,
        evidenceAnchors: List<EvidenceAnchor?>,
        knownCharacters: List<CharacterCard>,
        playerName: String
    ): List<String> {
        if (
            !lotteryOutcomeSignal.containsMatchIn(userMessage) ||
            !lifeChangingAmountSignal.containsMatchIn(userMessage)
        ) {
            return emptyList()
        }
        val issues = mutableListOf<String>()
        val expectedAmount = extractMajorAmountValues(userMessage).firstOrNull()
        if (
            expectedAmount != null &&
            expectedAmount !in extractMajorAmountValues(narrative)
        ) {
            issues += "用户明确的高影响结果金额没有原样保留，禁止把${majorOutcomeAmountAnchor(userMessage)}改成更小或不同的数值"
        }
        val normalizedNarrative = normalizeProofText(narrative).lowercase()
        val lastActionEnd = evidenceAnchors.filterNotNull().maxOfOrNull { it.end }
        if (lastActionEnd == null) {
            issues += "用户明确的高影响结果发生后，正文缺少具名兑奖人员的可观察反应和核验流程"
            return issues
        }
        val aftermath = normalizedNarrative.drop(lastActionEnd)
        val observerNames = (result.introducedCharacters.map { it.name } + knownCharacters.map { it.name })
            .map(String::trim)
            .filter { name ->
                name.isNotBlank() &&
                    !namesReferToSameCharacter(name, playerName) &&
                    !CharacterNamingPolicy.isGenericNpcName(name)
            }
            .distinct()
        val hasObserverReaction = observerOutcomeReaction.findAll(aftermath).any { reaction ->
            val windowStart = (reaction.range.first - 28).coerceAtLeast(0)
            val windowEnd = reaction.range.last.coerceAtMost(aftermath.lastIndex)
            val window = aftermath.substring(windowStart, windowEnd + 1)
            observerNames.any { name ->
                window.contains(normalizeProofText(name).lowercase())
            }
        }
        if (!hasObserverReaction) {
            issues += "用户明确的高影响结果发生后，正文没有在最后动作之后写出具名兑奖人员的可观察震惊反应，不能只写主角自己的感受"
        }
        if (!majorOutcomeVerification.containsMatchIn(aftermath)) {
            issues += "用户明确的高影响结果发生后，正文没有在最后动作之后启动可信的核验或兑奖流程"
        }
        return issues
    }

    private fun extractMajorAmountValues(text: String): Set<Long> {
        val values = linkedSetOf<Long>()
        Regex("([1-9]\\d*)\\s*亿").findAll(text).forEach { match ->
            match.groupValues[1].toLongOrNull()
                ?.takeIf { it <= Long.MAX_VALUE / 100_000_000L }
                ?.let { values += it * 100_000_000L }
        }
        Regex("([一二三四五六七八九十])\\s*亿").findAll(text).forEach { match ->
            chineseInteger(match.groupValues[1])?.let { values += it * 100_000_000L }
        }
        Regex(
            "([1-9]\\d*(?:\\.\\d+)?)\\s*(?:million|m\\b)",
            RegexOption.IGNORE_CASE
        ).findAll(text).forEach { match ->
            match.groupValues[1].toDoubleOrNull()
                ?.times(1_000_000.0)
                ?.toLong()
                ?.let(values::add)
        }
        Regex("(?<!\\d)(?:[¥￥]\\s*)?([1-9]\\d{0,2}(?:[，,]\\d{3}){2,}|[1-9]\\d{7,})(?:\\.\\d+)?(?!\\d)")
            .findAll(text)
            .forEach { match ->
                match.groupValues[1]
                    .replace(",", "")
                    .replace("，", "")
                    .toLongOrNull()
                    ?.let(values::add)
            }
        return values
    }

    private fun chineseInteger(value: String): Long? {
        val digits = mapOf(
            '一' to 1L,
            '二' to 2L,
            '三' to 3L,
            '四' to 4L,
            '五' to 5L,
            '六' to 6L,
            '七' to 7L,
            '八' to 8L,
            '九' to 9L
        )
        if (value == "十") return 10L
        if ('十' in value) {
            val parts = value.split('十')
            val tens = parts.firstOrNull()?.firstOrNull()?.let(digits::get) ?: 1L
            val ones = parts.getOrNull(1)?.firstOrNull()?.let(digits::get) ?: 0L
            return tens * 10L + ones
        }
        return value.singleOrNull()?.let(digits::get)
    }

    private fun findEvidenceAnchor(narrative: String, rawEvidence: String): EvidenceAnchor? {
        val evidence = rawEvidence.trim().trim('“', '”', '‘', '’', '"', '\'', '。', '，', ',', '.')
        val normalizedNarrative = normalizeProofText(narrative)
        val candidates = (listOf(evidence) + evidence
            .split(Regex("(?:…{1,}|\\.{2,})"))
            .map { it.trim().trim('“', '”', '‘', '’', '"', '\'', '。', '，', ',', '.') })
            .map(::normalizeProofText)
            .filter { it.length >= 4 && normalizedNarrative.contains(it) }
        val exact = candidates.maxByOrNull { it.length }
        if (exact != null) {
            val start = normalizedNarrative.indexOf(exact)
            return EvidenceAnchor(start = start, end = start + exact.length)
        }

        val normalizedEvidence = normalizeProofText(evidence)
        val common = longestCommonSubstring(normalizedNarrative, normalizedEvidence)
            .takeIf { it.length >= 6 }
            ?: return null
        val start = normalizedNarrative.indexOf(common)
        return EvidenceAnchor(start = start, end = start + common.length)
    }

    private fun normalizeProofText(value: String): String {
        return value.filter { it.isLetterOrDigit() }
    }

    private fun characterContinuityIssue(
        result: NarrativeResult,
        userMessage: String,
        knownCharacters: List<CharacterCard>,
        narrative: String
    ): String? {
        val introductions = result.introducedCharacters.filter { it.name.isNotBlank() }
        introductions.firstOrNull { CharacterNamingPolicy.isGenericNpcName(it.name) }?.let { character ->
            return "首次登场人物仍使用“${character.name}”这类身份泛称，必须给出真实姓名"
        }
        introductions.firstOrNull { it.occupation.isBlank() }?.let { character ->
            return "首次登场人物 ${character.name} 缺少身份 occupation"
        }
        introductions.firstOrNull { !mentionsCharacterName(narrative, it.name) }?.let { character ->
            return "首次登场人物 ${character.name} 没有以真实姓名出现在正文中"
        }
        result.endingScene.presentCharacters.orEmpty()
            .firstOrNull(CharacterNamingPolicy::isGenericNpcName)
            ?.let { genericName ->
                return "结尾在场人物仍写成“$genericName”，必须使用正文中的真实姓名"
            }

        result.endingScene.presentCharacters.orEmpty()
            .firstOrNull { presentName ->
                !mentionsCharacterName(narrative, presentName) ||
                    (introductions + knownCharacters.map {
                        IntroducedCharacter(name = it.name, occupation = it.occupation)
                    }).none { character -> namesReferToSameCharacter(character.name, presentName) }
            }
            ?.let { unresolvedName ->
                return "结尾在场人物 $unresolvedName 未在正文和角色名单中得到确认"
            }

        val introducedRoles = introductions
            .filter { mentionsCharacterName(narrative, it.name) }
            .flatMap(::characterRoleText)
        val knownRoles = knownCharacters
            .filter { mentionsCharacterName(narrative, it.name) }
            .flatMap(::characterRoleText)
        val uncoveredUserRelationship = roleSignals.firstOrNull { signal ->
            signal.sourceAliases.any { alias -> userMessage.contains(alias, ignoreCase = true) } &&
                !roleCovered(signal, knownRoles) &&
                !roleCovered(signal, introducedRoles)
        }
        if (uncoveredUserRelationship != null) {
            return "用户引入的${uncoveredUserRelationship.label}尚未获得真实姓名和 introduced_characters 记录"
        }

        val uncoveredInteraction = roleSignals.firstOrNull { signal ->
            hasInteractiveRoleReference(narrative, signal) &&
                !roleCovered(signal, knownRoles) &&
                !roleCovered(signal, introducedRoles)
        }
        return uncoveredInteraction?.let { signal ->
            "正文与首次登场的${signal.label}发生了互动，但没有为其命名并写入 introduced_characters"
        }
    }

    private fun characterRoleText(character: IntroducedCharacter): List<String> = listOf(
        character.occupation,
        character.relationshipToPlayer,
        character.firstAppearance
    ).filter { it.isNotBlank() }

    private fun characterRoleText(character: CharacterCard): List<String> = buildList {
        add(character.occupation)
        add(character.background)
        add(character.relationshipStatus)
        when {
            character.gender.contains("女") -> add("女人")
            character.gender.contains("男") -> add("男人")
        }
        character.relationships.values.forEach { add(it.stage) }
    }.filter { it.isNotBlank() }

    private fun mentionsCharacterName(narrative: String, rawName: String): Boolean {
        val name = rawName.trim()
        if (name.isBlank()) return false
        if (narrative.contains(name)) return true
        return name.length > 2 && narrative.contains(name.takeLast(2))
    }

    private fun namesReferToSameCharacter(left: String, right: String): Boolean {
        val first = left.trim()
        val second = right.trim()
        if (first.isBlank() || second.isBlank()) return false
        return first == second || first.contains(second) || second.contains(first) ||
            (first.length > 2 && second.length > 1 && first.takeLast(2) == second.takeLast(2))
    }

    private fun roleCovered(signal: RoleSignal, roleTexts: List<String>): Boolean {
        return roleTexts.any { text -> signal.aliases.any { alias -> text.contains(alias, ignoreCase = true) } }
    }

    private fun hasInteractiveRoleReference(narrative: String, signal: RoleSignal): Boolean {
        return signal.aliases.any { alias ->
            val escapedAlias = Regex.escape(alias)
            Regex("(?:$escapedAlias[^。！？\\n]{0,16}(?:$interactionVerbs)|(?:$interactionVerbs)[^。！？\\n]{0,16}$escapedAlias)")
                .containsMatchIn(narrative)
        }
    }

    private fun longestCommonSubstring(left: String, right: String): String {
        if (left.isEmpty() || right.isEmpty()) return ""
        val previous = IntArray(right.length + 1)
        var bestLength = 0
        var bestEnd = 0
        left.forEachIndexed { leftIndex, leftChar ->
            for (rightIndex in right.length - 1 downTo 0) {
                previous[rightIndex + 1] = if (leftChar == right[rightIndex]) {
                    previous[rightIndex] + 1
                } else {
                    0
                }
                if (previous[rightIndex + 1] > bestLength) {
                    bestLength = previous[rightIndex + 1]
                    bestEnd = leftIndex + 1
                }
            }
        }
        return left.substring(bestEnd - bestLength, bestEnd)
    }

    private data class EvidenceAnchor(val start: Int, val end: Int)

    private data class RoleSignal(
        val label: String,
        val aliases: List<String>,
        val sourceAliases: List<String> = aliases
    )

    private const val TARGET_NARRATIVE_MIN_CHARS = 1_000
    private const val TARGET_NARRATIVE_MAX_CHARS = 1_500
}
