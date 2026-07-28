package com.novelchat.data.storage

import com.google.gson.Gson
import com.google.gson.Strictness
import java.io.File
import java.io.IOException

internal class CorruptStoredDataException(
    label: String,
    file: File,
    cause: Throwable? = null
) : IOException("${label}损坏（${file.name}），已停止写入以避免覆盖原数据", cause)

internal class StoredJsonCodec(private val gson: Gson) {
    private val strictGson = gson.newBuilder()
        .setStrictness(Strictness.STRICT)
        .create()

    fun <T : Any> decode(
        json: String,
        file: File,
        type: Class<T>,
        label: String
    ): T {
        return try {
            strictGson.fromJson(json, type)
                ?: throw CorruptStoredDataException(label, file)
        } catch (e: CorruptStoredDataException) {
            throw e
        } catch (e: Exception) {
            throw CorruptStoredDataException(label, file, e)
        }
    }
}
