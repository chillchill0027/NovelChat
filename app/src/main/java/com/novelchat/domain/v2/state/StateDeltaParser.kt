package com.novelchat.domain.v2.state

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.novelchat.domain.parser.JsonResponseParser

class StateSyncParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class StateDeltaParser {

    private val gson = Gson()
    private val helper = JsonResponseParser()
    private val trailingComma = Regex(""",\s*([}\]])""")

    fun parse(raw: String): StateDelta {
        val candidate = helper.extractJson(raw)
            ?: helper.extractJson(trailingComma.replace(raw, "$1"))
            ?: throw StateSyncParseException("StateKeeper 未返回可解析的 JSON")

        val element = try {
            JsonParser.parseString(candidate)
        } catch (e: Exception) {
            try {
                JsonParser.parseString(trailingComma.replace(candidate, "$1"))
            } catch (e2: Exception) {
                throw StateSyncParseException("StateKeeper JSON 语法无效", e2)
            }
        }

        if (!element.isJsonObject) {
            throw StateSyncParseException("StateKeeper 根节点必须是 JSON 对象")
        }
        val root = element.asJsonObject
        removeNulls(root)
        normalizeRoot(root)

        return try {
            gson.fromJson(root, StateDelta::class.java) ?: StateDelta()
        } catch (e: Exception) {
            throw StateSyncParseException("StateDelta 反序列化失败：${e.message}", e)
        }
    }

    private fun normalizeRoot(root: JsonObject) {
        normalizeStringArray(root, "suggestions")
        root.get("scene")?.takeIf { it.isJsonObject }?.asJsonObject?.let { scene ->
            normalizeStringArray(scene, "present")
        }
        root.get("goals")?.takeIf { it.isJsonObject }?.asJsonObject?.let { goals ->
            normalizeStringArray(goals, "complete")
            normalizeStringArray(goals, "add")
        }
        root.get("player")?.takeIf { it.isJsonObject }?.asJsonObject?.let { player ->
            normalizeStringMap(player, "fields")
            normalizeInventoryArray(player, "inventory")
        }
        root.get("npcs")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeIntField(obj, "favor_delta")
            normalizeIntField(obj, "trust_delta")
            normalizeStringMap(obj, "fields")
            normalizeInventoryArray(obj, "inventory")
        }
        root.get("new_npcs")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeIntField(obj, "age")
            normalizeStringMap(obj, "fields")
        }
        root.get("departed")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeBooleanField(obj, "permanent")
        }
        root.get("transfers")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeIntField(obj, "qty")
        }
        root.get("world")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeStringArray(obj, "keywords")
        }
        root.get("events")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeStringArray(obj, "related")
        }
    }

    private fun normalizeInventoryArray(parent: JsonObject, key: String) {
        val arr = parent.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        arr.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            normalizeIntField(obj, "qty")
        }
    }

    private fun normalizeStringMap(parent: JsonObject, key: String) {
        val mapObj = parent.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val normalized = JsonObject()
        mapObj.entrySet().forEach { (k, v) ->
            val str = when {
                v.isJsonPrimitive -> v.asJsonPrimitive.asString
                v.isJsonArray -> v.asJsonArray.joinToString("、") { item ->
                    if (item.isJsonPrimitive) item.asString else ""
                }.trim('、')
                else -> ""
            }
            if (k.isNotBlank() && str.isNotBlank()) {
                normalized.addProperty(k, str)
            }
        }
        parent.add(key, normalized)
    }

    private fun normalizeStringArray(parent: JsonObject, key: String) {
        val elem = parent.get(key) ?: return
        val arr = JsonArray()
        when {
            elem.isJsonArray -> elem.asJsonArray.forEach { item ->
                if (item.isJsonPrimitive) {
                    val s = item.asString.trim()
                    if (s.isNotBlank()) arr.add(s)
                }
            }
            elem.isJsonPrimitive && elem.asJsonPrimitive.isString -> {
                val s = elem.asString.trim()
                if (s.isNotBlank()) arr.add(s)
            }
        }
        parent.add(key, arr)
    }

    private fun normalizeIntField(obj: JsonObject, key: String) {
        val elem = obj.get(key) ?: return
        if (!elem.isJsonPrimitive) {
            obj.remove(key)
            return
        }
        val prim = elem.asJsonPrimitive
        val intVal = when {
            prim.isNumber -> prim.asDouble.toInt()
            prim.isString -> prim.asString.trim().toDoubleOrNull()?.toInt()
                ?: Regex("""-?\d+""").find(prim.asString)?.value?.toIntOrNull()
            else -> null
        }
        if (intVal != null) {
            obj.add(key, JsonPrimitive(intVal))
        } else {
            obj.remove(key)
        }
    }

    private fun normalizeBooleanField(obj: JsonObject, key: String) {
        val elem = obj.get(key) ?: return
        if (!elem.isJsonPrimitive) {
            obj.remove(key)
            return
        }
        val prim = elem.asJsonPrimitive
        if (prim.isBoolean) return
        val parsed = when (prim.asString.trim().lowercase()) {
            "true", "1", "yes", "是" -> true
            "false", "0", "no", "否" -> false
            else -> null
        }
        if (parsed != null) {
            obj.add(key, JsonPrimitive(parsed))
        } else {
            obj.remove(key)
        }
    }

    private fun removeNulls(element: JsonElement) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val keys = obj.entrySet().map { it.key }
                keys.forEach { key ->
                    val child = obj.get(key)
                    if (child == null || child.isJsonNull) {
                        obj.remove(key)
                    } else {
                        removeNulls(child)
                    }
                }
            }
            element.isJsonArray -> {
                val arr = element.asJsonArray
                for (i in arr.size() - 1 downTo 0) {
                    val child = arr[i]
                    if (child == null || child.isJsonNull) {
                        arr.remove(i)
                    } else {
                        removeNulls(child)
                    }
                }
            }
        }
    }
}
