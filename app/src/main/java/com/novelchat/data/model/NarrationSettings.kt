package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

enum class LengthPreference(val storageKey: String, val displayName: String) {
    SHORT("short", "简短"),
    STANDARD("standard", "标准"),
    DETAILED("detailed", "详细");

    companion object {
        fun fromStorageKey(value: String?): LengthPreference? =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class CreativityLevel(
    val storageKey: String,
    val displayName: String,
    val temperature: Float
) {
    STEADY("steady", "稳定", 0.65f),
    BALANCED("balanced", "平衡", 0.85f),
    VIVID("vivid", "奔放", 1.0f);

    companion object {
        fun fromStorageKey(value: String?): CreativityLevel =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) } ?: BALANCED
    }
}

enum class NarrationPov(val storageKey: String, val displayName: String, val promptLabel: String) {
    THIRD_LIMITED("third_limited", "第三人称限知", "第三人称、贴近主角的视角"),
    SECOND("second", "第二人称（“你”）", "第二人称（“你”）的视角");

    companion object {
        fun fromStorageKey(value: String?): NarrationPov =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) }
                ?: THIRD_LIMITED
    }
}

data class NarrationSettings(
    @SerializedName("pov") val pov: String = NarrationPov.THIRD_LIMITED.storageKey,
    @SerializedName("length") val length: String? = null,
    @SerializedName("style_sample") val styleSample: String = ""
) {
    fun resolvedPov(): NarrationPov = NarrationPov.fromStorageKey(pov)

    fun resolvedLength(globalDefault: LengthPreference): LengthPreference =
        LengthPreference.fromStorageKey(length) ?: globalDefault

    fun normalized(): NarrationSettings = copy(
        pov = resolvedPov().storageKey,
        length = LengthPreference.fromStorageKey(length)?.storageKey,
        styleSample = styleSample.trim().take(MAX_STYLE_SAMPLE_LENGTH)
    )

    companion object {
        const val MAX_STYLE_SAMPLE_LENGTH = 800
    }
}
