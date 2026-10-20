/**
 * 用户词库模型与 PC 端 dictionary.json 兼容的导入/导出。
 *
 * 归属模块：store
 *
 * 结构设计对齐 Windows AI-VoiceInput 的 dictionary.json：
 * - vocabulary：容易识别错的专有名词（语音识别偏置 + 润色保持原样）
 * - aliases：错词 -> 正确词（本地直接替换，不上云）
 * - snippets：口令 -> 固定内容（本地替换，内容不上云）
 */
package com.brycewg.asrkb.store

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** 错词 -> 正确词。 */
@Serializable
data class AliasPair(val wrong: String, val correct: String)

/** 口令 -> 固定内容。 */
@Serializable
data class Snippet(val trigger: String, val content: String)

/** 用户词库。空列表字段不参与序列化（保持 JSON 干净）。 */
@Serializable
data class UserDictionary(
    val vocabulary: List<String> = emptyList(),
    val aliases: List<AliasPair> = emptyList(),
    val snippets: List<Snippet> = emptyList()
)

/**
 * UserDictionary 的序列化与 PC 兼容转换（从 [Prefs] 中拆出，模式照抄 SpeechPresetStore）。
 */
internal object UserDictionaryStore {
    private const val TAG = "UserDictionary"

    // 内存缓存：raw 未变化时直接复用，避免每个按键/每次识别都 decode 全量 JSON。
    @Volatile
    private var cachedRaw: String? = null
    @Volatile
    private var cachedDict: UserDictionary? = null

    fun get(prefs: Prefs, json: Json): UserDictionary {
        val raw = prefs.userDictionaryJson
        if (raw.isBlank()) {
            if (cachedRaw != "") {
                cachedRaw = ""
                cachedDict = UserDictionary()
            }
            return UserDictionary()
        }
        val cached = cachedDict
        if (raw == cachedRaw && cached != null) return cached
        val dict = try {
            json.decodeFromString<UserDictionary>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse UserDictionary JSON", e)
            UserDictionary()
        }
        if (raw != cachedRaw) {
            cachedRaw = raw
            cachedDict = dict
        }
        return dict
    }

    fun set(prefs: Prefs, json: Json, dict: UserDictionary) {
        val sanitized = UserDictionary(
            vocabulary = dict.vocabulary.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            aliases = dict.aliases.mapNotNull { a ->
                val wrong = a.wrong.trim()
                val correct = a.correct.trim()
                if (wrong.isEmpty() || correct.isEmpty()) null else AliasPair(wrong, correct)
            },
            snippets = dict.snippets.mapNotNull { s ->
                val trigger = s.trigger.trim()
                val content = s.content.trim()
                if (trigger.isEmpty() || content.isEmpty()) null else Snippet(trigger, content)
            }
        )
        try {
            prefs.userDictionaryJson = json.encodeToString(sanitized)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize UserDictionary", e)
        }
    }

    /**
     * 解析 PC 端 dictionary.json（aliases/snippets 为"键->值"对象形式），
     * 转成内部 List 模型。解析失败返回空词库，不抛异常。
     */
    fun fromPcJson(text: String): UserDictionary {
        if (text.isBlank()) return UserDictionary()
        return try {
            val root = JSONObject(text)
            val vocabulary = mutableListOf<String>()
            val aliases = mutableListOf<AliasPair>()
            val snippets = mutableListOf<Snippet>()

            val vocabArray = root.optJSONArray("vocabulary")
            if (vocabArray != null) {
                for (i in 0 until vocabArray.length()) {
                    vocabArray.optString(i).trim().takeIf { it.isNotEmpty() }?.let { vocabulary.add(it) }
                }
            }

            val aliasesObj = root.optJSONObject("aliases")
            if (aliasesObj != null) {
                val names = aliasesObj.names()
                if (names != null) {
                    for (i in 0 until names.length()) {
                        val wrong = names.optString(i).trim()
                        val correct = aliasesObj.optString(names.optString(i), "").trim()
                        if (wrong.isNotEmpty() && correct.isNotEmpty()) {
                            aliases.add(AliasPair(wrong, correct))
                        }
                    }
                }
            }

            val snippetsObj = root.optJSONObject("snippets")
            if (snippetsObj != null) {
                val names = snippetsObj.names()
                if (names != null) {
                    for (i in 0 until names.length()) {
                        val trigger = names.optString(i).trim()
                        val content = snippetsObj.optString(names.optString(i), "").trim()
                        if (trigger.isNotEmpty() && content.isNotEmpty()) {
                            snippets.add(Snippet(trigger, content))
                        }
                    }
                }
            }

            UserDictionary(
                vocabulary = vocabulary.distinct(),
                aliases = aliases,
                snippets = snippets
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PC dictionary.json", e)
            UserDictionary()
        }
    }

    /**
     * 导出为 PC 端 dictionary.json 兼容格式（aliases/snippets 用对象形式），
     * 可直接复制回 PC 覆盖原文件。
     */
    fun toPcJson(dict: UserDictionary): String = try {
        val root = JSONObject()
        val vocabArray = org.json.JSONArray()
        dict.vocabulary.forEach { vocabArray.put(it) }
        root.put("vocabulary", vocabArray)
        val aliasesObj = JSONObject()
        dict.aliases.forEach { aliasesObj.put(it.wrong, it.correct) }
        root.put("aliases", aliasesObj)
        val snippetsObj = JSONObject()
        dict.snippets.forEach { snippetsObj.put(it.trigger, it.content) }
        root.put("snippets", snippetsObj)
        root.toString(2)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to serialize PC dictionary.json", e)
        "{}"
    }
}
