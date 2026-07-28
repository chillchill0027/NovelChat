package com.novelchat.data.storage

import java.io.File
import java.io.IOException

internal object ConversationIdAllocator {

    fun reserve(rootDirectory: File, seed: Long): Long {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw IOException("无法创建故事存档目录")
        }
        if (!rootDirectory.isDirectory) {
            throw IOException("故事存档路径不是目录")
        }

        val firstCandidate = seed.coerceAtLeast(1L)
        var candidate = firstCandidate
        do {
            val candidateDirectory = File(rootDirectory, candidate.toString())
            if (candidateDirectory.mkdir()) return candidate
            if (!candidateDirectory.exists()) {
                throw IOException("无法预留故事存档目录")
            }
            candidate = if (candidate == Long.MAX_VALUE) 1L else candidate + 1L
        } while (candidate != firstCandidate)

        throw IOException("没有可用的故事标识")
    }
}
