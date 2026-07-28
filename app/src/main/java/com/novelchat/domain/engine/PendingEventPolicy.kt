package com.novelchat.domain.engine

internal object PendingEventPolicy {

    private val sentenceSeparator = Regex("[。！？!?；;\\n]+")
    private val genericCompletion = Regex("办妥|解决了|完成了|已完成")
    private val paymentCompletion = Regex("付清|缴清|结清|已经支付|已支付|缴纳了")
    private val submissionCompletion = Regex("提交了|已提交|交付了|已交付|已经交付|发出了|已发出|发送了|发送成功")
    private val replyCompletion = Regex("回复了|已回复|答复了|已答复")
    private val attendanceCompletion = Regex("参加了|已参加|赴约了|已赴约|面试结束|完成面试|开完了|会议结束|已结束")
    private val completionMarker = Regex(
        listOf(
            genericCompletion,
            paymentCompletion,
            submissionCompletion,
            replyCompletion,
            attendanceCompletion
        ).joinToString("|") { it.pattern }
    )
    private val incompleteOrFutureMarker = Regex(
        "尚未|还没|未完成|仍需|仍要|等待|正在|着手|开始|初稿|草稿|计划|准备|将于|明天|今晚|稍后|随后再|继续|周[一二三四五六日天]前|中午前|月底前"
    )
    private val eventNoise = Regex(
        "本周|本月|今天|明天|今晚|中午前|周一|周二|周三|周四|周五|周六|周日|尽快|及时|完成|处理|回复|提交|支付|缴纳|交付|交(?=房租|租金|费用|材料|文件)|待办|任务|[（(][^）)]*[）)]"
    )
    private val lotteryMarker = Regex("彩票|兑奖")
    private val lotteryWinMarker = Regex("中奖|中了|一等奖|二等奖|三等奖|奖金")
    private val lotteryClaimMarker = Regex("兑奖|领奖|奖金领取|奖金手续")
    private val lotteryClaimCompletion = Regex(
        "完成兑奖|兑奖手续(?:已经|已)?完成|办完兑奖|领到(?:了)?奖金|奖金(?:已经|已)?到账|奖金打入|领取(?:了)?奖金"
    )
    private val uselessGeneratedEvent = Regex(
        "^(?:尚未执行|尚未安排时间或地点|尚未到达该日期|尚未开始|时间|待处理|暂无|无)$"
    )
    private val completedStagePrefix = Regex("^已(?:获得|抵达|到达|进入|完成|提交|回复|支付|联系|发送)")
    private val leadingConjunction = Regex("""^(?:并且|并|且|以及)[，,：:\s]*""")
    private val edgePunctuation = Regex("""^[，,。.!！；;：:\s]+|[，,。.!！；;：:\s]+$""")

    fun reconcile(events: List<String>, completedContext: String): List<String> {
        if (events.isEmpty() || completedContext.isBlank()) return events.distinct()
        val completionSentences = completedContext
            .split(sentenceSeparator)
            .map { it.trim() }
            .filter { it.isNotBlank() && completionMarker.containsMatchIn(it) }
        if (completionSentences.isEmpty()) return events.distinct()

        return events
            .filter { event -> completionSentences.none { sentence -> provesCompletion(event, sentence) } }
            .distinct()
    }

    fun prioritizeImmediateConsequences(events: List<String>, narrative: String): List<String> {
        val deduplicated = events.mapNotNull(::normalizeGeneratedEvent).distinct()
        val hasUnfinishedLotteryClaim = lotteryMarker.containsMatchIn(narrative) &&
            lotteryWinMarker.containsMatchIn(narrative) &&
            !lotteryClaimCompletion.containsMatchIn(narrative)
        if (!hasUnfinishedLotteryClaim) return deduplicated

        val existingClaim = deduplicated.firstOrNull { lotteryClaimMarker.containsMatchIn(it) }
        val registrationInProgress = narrative.contains("A07", ignoreCase = true) &&
            Regex("登记册|登记信息|登记一下").containsMatchIn(narrative)
        val urgentClaim = when {
            registrationInProgress -> "在A07柜台填写兑奖登记信息"
            else -> existingClaim ?: "办理彩票兑奖与奖金领取手续"
        }
        return listOf(urgentClaim) + deduplicated.filterNot { event ->
            event == existingClaim ||
                event == urgentClaim ||
                (registrationInProgress && (
                    lotteryClaimMarker.containsMatchIn(event) || event.contains("登记")
                ))
        }
    }

    private fun normalizeGeneratedEvent(raw: String): String? {
        val event = raw.trim()
            .replace(leadingConjunction, "")
            .replace(edgePunctuation, "")
            .trim()
        if (event.length < 4 || uselessGeneratedEvent.matches(event)) return null
        if (
            completedStagePrefix.containsMatchIn(event) &&
            !incompleteOrFutureMarker.containsMatchIn(event)
        ) return null
        return event
    }

    private fun provesCompletion(event: String, sentence: String): Boolean {
        if (incompleteOrFutureMarker.containsMatchIn(sentence)) return false
        val eventCompletionMarker = completionMarkerFor(event)
        if (!eventCompletionMarker.containsMatchIn(sentence)) return false

        val core = event
            .replace(eventNoise, "")
            .filter { it.isLetterOrDigit() }
        if (core.length < 2) return false
        val normalizedSentence = sentence.filter { it.isLetterOrDigit() }
        if (normalizedSentence.contains(core)) return true
        val requiredOverlap = if (core.length <= 3) core.length else 4
        return longestCommonSubstringLength(core, normalizedSentence) >= requiredOverlap
    }

    private fun completionMarkerFor(event: String): Regex {
        val specificMarkers = when {
            event.contains("回复") || event.contains("答复") -> replyCompletion
            event.contains("面试") || event.contains("会议") || event.contains("赴约") || event.contains("参加") -> {
                attendanceCompletion
            }
            event.contains("房租") || event.contains("租金") || event.contains("缴费") || event.contains("支付") -> {
                paymentCompletion
            }
            event.contains("提交") || event.contains("交付") || event.contains("发送") -> submissionCompletion
            event.contains("完成") -> submissionCompletion
            else -> genericCompletion
        }
        return Regex("${genericCompletion.pattern}|${specificMarkers.pattern}")
    }

    private fun longestCommonSubstringLength(left: String, right: String): Int {
        if (left.isEmpty() || right.isEmpty()) return 0
        val row = IntArray(right.length + 1)
        var best = 0
        left.forEach { leftChar ->
            for (rightIndex in right.length - 1 downTo 0) {
                row[rightIndex + 1] = if (leftChar == right[rightIndex]) row[rightIndex] + 1 else 0
                if (row[rightIndex + 1] > best) best = row[rightIndex + 1]
            }
        }
        return best
    }
}
