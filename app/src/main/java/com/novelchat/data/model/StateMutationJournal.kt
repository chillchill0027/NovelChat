package com.novelchat.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StateMutationJournal(
    @SerializedName("operation_id") val operationId: String = "",
    @SerializedName("operation_key") val operationKey: String = "",
    @SerializedName("round") val round: Int = 0,
    @SerializedName("entity_type") val entityType: String = "",
    @SerializedName("target_key") val targetKey: String = "",
    @SerializedName("before_exists") val beforeExists: Boolean = false,
    @SerializedName("before_state") val beforeState: JsonElement? = null,
    @SerializedName("after_exists") val afterExists: Boolean = false,
    @SerializedName("after_state") val afterState: JsonElement? = null,
    @SerializedName("status") val status: String = STATUS_PREPARED
) {
    companion object {
        const val ENTITY_PLAYER = "player"
        const val ENTITY_CHARACTER = "character"
        const val ENTITY_WORLDBOOK = "worldbook"

        const val STATUS_PREPARED = "prepared"
        const val STATUS_COMPLETED = "completed"
    }
}
