/**
 * 词库 aliases 本地替换：按"错词 -> 正确词"对识别文本做整段/子串替换。
 *
 * 归属模块：util
 *
 * 与 PC AI-VoiceInput 语义一致（如"奥比斯迪安" -> "Obsidian"）。
 * 规则：先长后短替换，避免"错词 A 是错词 B 的子串"时互相覆盖。
 */
package com.brycewg.asrkb.util

import android.util.Log
import com.brycewg.asrkb.store.Prefs

object UserDictionaryAliasReplacer {
    private const val TAG = "UserDictionaryAliasReplacer"

    /**
     * 对 [text] 依次应用词库中的 aliases 替换。
     * 词库为空、开关关闭或异常时原样返回。
     */
    fun replace(prefs: Prefs, text: String): String {
        if (text.isBlank()) return text
        return try {
            if (!prefs.dictAliasesEnabled) return text
            val aliases = prefs.getUserDictionary().aliases
                .filter { it.wrong.isNotEmpty() && it.correct.isNotEmpty() }
                // 先长后短：长的错词优先匹配，避免短词命中后破坏长词
                .sortedByDescending { it.wrong.length }
            if (aliases.isEmpty()) return text
            var out = text
            for (pair in aliases) {
                out = out.replace(pair.wrong, pair.correct)
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "alias replacement failed", t)
            text
        }
    }
}
