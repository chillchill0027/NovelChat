package com.novelchat.domain.v2.state

import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.repo.EntityWrite
import com.novelchat.data.repo.StorySnapshot

enum class ApplyIssueLevel {
    INFO,
    WARN
}

data class ApplyIssue(
    val level: ApplyIssueLevel,
    val target: String,
    val message: String
)

data class ApplyResult(
    val snapshot: StorySnapshot,
    val writes: List<EntityWrite>,
    val issues: List<ApplyIssue>,
    val summary: SummaryEntry?,
    val suggestions: List<String>
)
