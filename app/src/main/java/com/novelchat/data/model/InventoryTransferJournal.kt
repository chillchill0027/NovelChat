package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class InventoryTransferJournal(
    @SerializedName("operation_id") val operationId: String = "",
    @SerializedName("round") val round: Int = 0,
    @SerializedName("item") val item: InventoryItem = InventoryItem(),
    @SerializedName("quantity") val quantity: Int = 0,
    @SerializedName("source_key") val sourceKey: String = "",
    @SerializedName("target_key") val targetKey: String = "",
    @SerializedName("source_before_quantity") val sourceBeforeQuantity: Int = 0,
    @SerializedName("target_before_quantity") val targetBeforeQuantity: Int = 0,
    @SerializedName("status") val status: String = STATUS_PREPARED
) {
    companion object {
        const val STATUS_PREPARED = "prepared"
        const val STATUS_COMPLETED = "completed"
    }
}
