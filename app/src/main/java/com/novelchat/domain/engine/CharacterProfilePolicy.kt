package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy

internal object CharacterProfilePolicy {

    fun reconcile(card: CharacterCard, playerName: String): CharacterCard {
        val namedCard = CharacterNamingPolicy.normalizeNpcCard(card)
        val relationship = namedCard.relationships[playerName]
        val stage = relationship?.stage.orEmpty().trim()
        val confirmedStatus = TaskAssignmentRosterPolicy.relationshipStatusFor(stage)
        val relationshipStatus = if (
            confirmedStatus != "未知" && namedCard.relationshipStatus in uncertainRelationshipStatuses
        ) {
            confirmedStatus
        } else {
            namedCard.relationshipStatus.ifBlank { "未知" }
        }
        val occupation = if (isUncertainOccupation(namedCard.occupation) && stage !in genericRelationships) {
            "${playerName.ifBlank { "主角" }}的$stage"
        } else {
            namedCard.occupation
        }
        val gender = namedCard.gender.ifBlank {
            when {
                femaleRelationship.containsMatchIn(stage) -> "女"
                maleRelationship.containsMatchIn(stage) -> "男"
                else -> ""
            }
        }
        return namedCard.copy(
            occupation = occupation,
            gender = gender,
            relationshipStatus = relationshipStatus
        )
    }

    private fun isUncertainOccupation(value: String): Boolean {
        return value.isBlank() || uncertainOccupation.containsMatchIn(value)
    }

    private val uncertainOccupation = Regex("未知|不详|待定|可能|也许|或者|或(?=[^业])")
    private val uncertainRelationshipStatuses = setOf("", "未知", "单身")
    private val genericRelationships = setOf("", "初识", "陌生人", "不熟", "未知")
    private val femaleRelationship = Regex("女朋友|女友|妻子|老婆")
    private val maleRelationship = Regex("男朋友|男友|丈夫|老公")
}
