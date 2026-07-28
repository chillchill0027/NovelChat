package com.novelchat.domain.engine

internal object NarrativeStreamPreviewPolicy {

    fun extract(raw: String): String {
        val keyIndex = raw.indexOf("\"narrative\"")
        if (keyIndex < 0) return ""

        val colonIndex = raw.indexOf(':', keyIndex)
        if (colonIndex < 0) return ""

        var index = colonIndex + 1
        while (index < raw.length && raw[index].isWhitespace()) index++
        if (index >= raw.length || raw[index] != '"') return ""
        index++

        val result = StringBuilder()
        var escaped = false
        while (index < raw.length) {
            val char = raw[index]
            if (escaped) {
                when (char) {
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    '"' -> result.append('"')
                    '\\' -> result.append('\\')
                    '/' -> result.append('/')
                    'u' -> {
                        val unicodeEnd = index + 5
                        if (unicodeEnd > raw.length) return result.toString()
                        val codePoint = raw.substring(index + 1, unicodeEnd).toIntOrNull(16)
                        if (codePoint == null) {
                            result.append('u')
                        } else {
                            result.append(codePoint.toChar())
                            index += 4
                        }
                    }
                    else -> result.append(char)
                }
                escaped = false
            } else {
                when (char) {
                    '\\' -> escaped = true
                    '"' -> return result.toString()
                    else -> result.append(char)
                }
            }
            index++
        }
        return result.toString()
    }
}
