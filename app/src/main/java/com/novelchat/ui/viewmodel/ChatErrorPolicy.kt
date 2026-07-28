package com.novelchat.ui.viewmodel

object ChatErrorPolicy {
    fun userVisibleMessage(rawMessage: String?): String {
        val detail = UserVisibleTextPolicy.errorDetail(rawMessage)
        return "生成失败：$detail\n请检查模型服务后重试，或调整输入再发送。"
    }

    fun userVisibleWarning(rawMessage: String?): String {
        return UserVisibleTextPolicy.multiline(
            rawText = rawMessage,
            maxLength = UserVisibleTextPolicy.MAX_ERROR_DETAIL_LENGTH,
            fallback = "状态同步暂未完成，请稍后重试。"
        )
    }
}
