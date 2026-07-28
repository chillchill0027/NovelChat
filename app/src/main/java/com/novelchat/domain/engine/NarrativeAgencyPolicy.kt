package com.novelchat.domain.engine

internal object NarrativeAgencyPolicy {

    private val quotedSpeech = Regex("“([^”]+)”|\"([^\"]+)\"")
    private val decisiveQuotedChoice = Regex(
        "我(?:决定|选择|选定|同意|接受|拒绝|答应|确认|授权|愿意|承诺|保证|" +
            "辞职|离职|分手|结婚|报警|起诉|认罪|放弃|加入|退出)|" +
            "(?:直接)?(?:打款|转账|汇款|划款|存入)[^。！？\\n]{0,20}" +
            "(?:我的|我名下|银行卡|储蓄卡|账户)|" +
            "(?:银行转账|现金兑现|支票兑现)[^。！？\\n]{0,8}(?:就好|就行|定了)|" +
            "(?:我)?(?:签署|签下|买下|卖掉|投资|认购|贷款)"
    )
    private const val MAJOR_DECISION =
        "(?:选择了|选定了|作出(?:了)?选择|做出(?:了)?决定|" +
            "决定(?:要|将|直接|接受|拒绝|签署|转账|汇款|打款|投资|辞职|离职|分手|结婚|报警|起诉)|" +
            "(?:同意|接受|拒绝|答应)(?:了)?[^，。！？\\n]{0,12}" +
            "(?:方案|条件|报价|邀请|安排|要求|交易|合同|兑现方式)|" +
            "签署(?:了)?|签下(?:了)?|授权(?:了)?|" +
            "(?:转账|汇款|打款|划款|投资|认购|贷款)(?:了|给|到|至)|" +
            "卖掉(?:了)?|提出(?:了)?辞职|办理(?:了)?离职|提出(?:了)?分手|" +
            "答应(?:了)?结婚|报(?:了)?警|提起(?:了)?诉讼|承认(?:了)?罪行)"

    fun issue(
        narrative: String,
        playerName: String,
        continuationRequest: Boolean,
        authorizationContext: String = ""
    ): String? {
        if (!continuationRequest || narrative.isBlank()) return null
        val canonicalPlayerName = playerName.trim()
        if (canonicalPlayerName.isBlank()) return null

        val playerSubject = "(?:${Regex.escape(canonicalPlayerName)}|主角)"
        val narrationWithoutQuotes = quotedSpeech.replace(narrative, "")
        val directDecision = Regex(
            "(?:^|[。！？!?；;\\n])\\s*(?:(?:最终|片刻后|随即|当即|这时)[，,]?\\s*)?" +
                "$playerSubject[^。！？!?\\n]{0,56}$MAJOR_DECISION",
            setOf(RegexOption.MULTILINE)
        )
        val directDecisionMatch = directDecision.find(narrationWithoutQuotes)
        if (
            directDecisionMatch != null &&
            !isAuthorizedByCurrentGoal(directDecisionMatch.value, authorizationContext)
        ) {
            return UNAUTHORIZED_DECISION_ISSUE
        }

        val playerSpeechLead = Regex(
            "(?:^|[。！？!?；;\\n])\\s*(?:(?:最终|片刻后|随即|当即|这时)[，,]?\\s*)?" +
                "$playerSubject[^。！？!?\\n]{0,72}" +
                "(?:说|说道|问|问道|答|答道|回答|回应|开口|表示|宣布|补充|报出)[^。！？!?\\n]*[：:]?\\s*$",
            setOf(RegexOption.MULTILINE)
        )
        val unauthorizedSpeech = quotedSpeech.findAll(narrative).any { match ->
            val quote = match.groups[1]?.value ?: match.groups[2]?.value.orEmpty()
            val clauseStart = lastClauseBoundary(narrative, match.range.first)
            val lead = narrative.substring(clauseStart, match.range.first)
            playerSpeechLead.containsMatchIn(lead) &&
                !looksLikeQuestion(quote) &&
                decisiveQuotedChoice.containsMatchIn(quote) &&
                !isAuthorizedByCurrentGoal(quote, authorizationContext)
        }
        return UNAUTHORIZED_DECISION_ISSUE.takeIf { unauthorizedSpeech }
    }

    private fun lastClauseBoundary(text: String, endExclusive: Int): Int {
        val start = text
            .lastIndexOfAny(
                chars = charArrayOf('。', '！', '？', '!', '?', '；', ';', '\n'),
                startIndex = (endExclusive - 1).coerceAtLeast(0)
            )
        return (start + 1).coerceAtMost(endExclusive)
    }

    private fun looksLikeQuestion(quote: String): Boolean {
        val value = quote.trim()
        return value.endsWith("？") || value.endsWith("?") ||
            value.contains("可以吗") || value.contains("能否") || value.contains("是不是")
    }

    private fun isAuthorizedByCurrentGoal(
        decisionText: String,
        authorizationContext: String
    ): Boolean {
        val context = authorizationContext.trim()
        if (context.isBlank()) return false
        return authorizationCategories.any { category ->
            category.decision.containsMatchIn(decisionText) &&
                category.authorization.containsMatchIn(context)
        }
    }

    private val authorizationCategories = listOf(
        AuthorizationCategory(
            decision = Regex("打款|转账|汇款|划款|存入|银行转账|储蓄卡|银行卡|账户"),
            authorization = Regex(
                "已确认[^。；]{0,16}(?:打款|转账|汇款|银行卡|账户)|" +
                    "(?:打款|转账|汇款)[^。；]{0,16}(?:已确认|已选定|已决定)"
            )
        ),
        AuthorizationCategory(
            decision = Regex("辞职|离职"),
            authorization = Regex("已决定[^。；]{0,12}(?:辞职|离职)|办理(?:辞职|离职)")
        ),
        AuthorizationCategory(
            decision = Regex("分手|结婚"),
            authorization = Regex("已决定[^。；]{0,12}(?:分手|结婚)|办理结婚")
        ),
        AuthorizationCategory(
            decision = Regex("报警|起诉|诉讼|认罪"),
            authorization = Regex("已决定[^。；]{0,12}(?:报警|起诉|诉讼|认罪)|办理(?:报警|起诉|诉讼)")
        ),
        AuthorizationCategory(
            decision = Regex("投资|认购|贷款|卖掉|出售"),
            authorization = Regex(
                "已决定[^。；]{0,12}(?:投资|认购|贷款|卖掉|出售)|" +
                    "办理(?:投资|认购|贷款|出售)"
            )
        )
    )

    private data class AuthorizationCategory(
        val decision: Regex,
        val authorization: Regex
    )

    const val UNAUTHORIZED_DECISION_ISSUE =
        "泛化续写替主角作出未获用户授权的重大决定，必须停在选项或请求出现的位置，把决定留给用户"
}
