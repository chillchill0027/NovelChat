package com.novelchat.domain.v2.prompt

import com.novelchat.data.model.NarrationPov
import com.novelchat.domain.engine.NarrativeIdentityAnchor
import com.novelchat.domain.v2.round.LengthHint

object NarratorPromptTemplate {

    fun system(
        playerName: String,
        pov: NarrationPov,
        styleGuide: String,
        styleSample: String,
        worldProse: String,
        playerProfile: String,
        chaptersText: String
    ): String = buildString {
        appendLine("你是这个互动故事的叙事者：描写世界，并扮演除主角以外的所有角色。玩家扮演主角「${playerName.ifBlank { "主角" }}」。")
        appendLine()
        appendLine("## 怎么写")
        appendLine("- 玩家告诉你主角做了什么、说了什么；你写出接下来发生的事：环境的回应、其他角色的言行，以及行动带来的结果。")
        appendLine("- 用${pov.promptLabel}写作，只写主角能看见、听见、感觉到的东西。")
        appendLine("- 主角的台词和重大选择属于玩家。玩家给出的行动，你可以写得具体生动；但不要替主角说出玩家没给的关键台词，也不要替他做决定。需要主角表态的时候，让场面停在那里，等玩家。")
        appendLine("- 其他角色有自己的目的、情绪和说话方式。他们会主动开口、提要求、隐瞒、拒绝、误会，不只是围着主角转。")
        appendLine("- 每次回应都让局面往前走一步：一个新信息、一个人的反应、一处变化，或者一个小麻烦。")
        appendLine("- 已经发生的事和世界规则不能推翻。标着“作者备注”的内容是只有你知道的秘密：可以让它影响角色的行为，但角色不会无故说破，主角也不会凭空知道。")
        appendLine("- 新出场、和主角有交流的人，给他一个自然的名字，并在正文里用这个名字。")
        appendLine()
        appendLine("## 文风")
        appendLine(styleGuide.ifBlank { DefaultStyleGuide.zh })
        if (styleSample.isNotBlank()) {
            appendLine()
            appendLine("## 文风示范（只学语气和节奏，不要复用内容）")
            appendLine(styleSample.trim())
        }
        appendLine()
        appendLine("## 输出")
        appendLine("直接写故事正文。不要标题、列表、解释或总结，不要提到“玩家”“回合”“AI”“设定”。")
        if (worldProse.isNotBlank()) {
            appendLine()
            appendLine("## 世界")
            appendLine(worldProse)
        }
        if (playerProfile.isNotBlank()) {
            appendLine()
            appendLine("## 主角")
            appendLine(playerProfile)
        }
        if (chaptersText.isNotBlank()) {
            appendLine()
            appendLine("## 前情总览")
            appendLine(chaptersText)
        }
    }.trimEnd()

    fun turnContext(
        timeOfDay: String,
        location: String,
        weather: String,
        sceneDescription: String,
        playerName: String,
        presentNames: List<String>,
        playerStatus: String,
        currentGoal: String,
        otherPending: List<String>,
        presentSheets: List<String>,
        relatedBriefs: List<String>,
        worldbookLines: List<String>,
        recentSummaries: List<String>,
        identityAnchors: List<NarrativeIdentityAnchor>,
        lengthHint: LengthHint
    ): String = buildString {
        appendLine("【当前情境】")
        val header = listOf(timeOfDay, location, weather)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("｜")
        if (header.isNotBlank()) appendLine(header)
        if (sceneDescription.isNotBlank()) {
            appendLine("画面：${sceneDescription.trim()}")
        }
        val whoIsHere = if (presentNames.isEmpty()) {
            "只有${playerName.ifBlank { "主角" }}"
        } else {
            presentNames.joinToString("、")
        }
        appendLine("在场：$whoIsHere")
        if (playerStatus.isNotBlank()) {
            appendLine("${playerName.ifBlank { "主角" }}现状：$playerStatus")
        }
        if (currentGoal.isNotBlank() || otherPending.isNotEmpty()) {
            val goalPart = if (currentGoal.isNotBlank()) "当前目标：$currentGoal" else ""
            val otherPart = if (otherPending.isNotEmpty()) "其他待办：${otherPending.joinToString("；")}" else ""
            appendLine(listOf(goalPart, otherPart).filter { it.isNotBlank() }.joinToString("；"))
        }

        if (presentSheets.isNotEmpty()) {
            appendLine()
            appendLine("【在场人物】")
            presentSheets.forEachIndexed { idx, sheet ->
                if (idx > 0) appendLine()
                appendLine(sheet)
            }
        }

        if (relatedBriefs.isNotEmpty()) {
            appendLine()
            appendLine("【相关人物】")
            relatedBriefs.forEach { appendLine("- $it") }
        }

        if (worldbookLines.isNotEmpty()) {
            appendLine()
            appendLine("【相关设定】")
            worldbookLines.forEach { appendLine("- $it") }
        }

        if (recentSummaries.isNotEmpty()) {
            appendLine()
            appendLine("【近期提要】")
            recentSummaries.forEach { appendLine(it) }
        }

        if (identityAnchors.isNotEmpty()) {
            appendLine()
            appendLine("【人物指代】")
            identityAnchors.forEach { anchor ->
                appendLine("用户说的“${anchor.reference}”指的是${anchor.name}（${anchor.relationship}）。")
            }
        }

        appendLine()
        append("【本次篇幅】大约 ${lengthHint.minChars}-${lengthHint.maxChars} 字。")
    }

    fun formatUserTurn(turnContext: String, rawInput: String): String = buildString {
        appendLine(turnContext)
        appendLine()
        appendLine("【主角的行动】")
        append(rawInput.trim())
    }

    fun formatHistoryUser(rawInput: String): String = "【主角的行动】\n${rawInput.trim()}"
}
