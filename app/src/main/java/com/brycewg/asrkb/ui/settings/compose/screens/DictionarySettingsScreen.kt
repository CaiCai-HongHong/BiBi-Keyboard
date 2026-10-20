/**
 * 词库管理设置页（vocabulary / aliases / snippets）。
 *
 * 归属模块：ui/settings/compose/screens
 *
 * 支持：
 * - 三个识别行为开关（本地错词替换 / 润色保持原样 / ASR 偏置[二期]）
 * - 词汇、别名、口令三区的增删
 * - 从剪贴板导入 PC dictionary.json、导出到剪贴板、清空
 */
@file:Suppress("FunctionName")

package com.brycewg.asrkb.ui.settings.compose.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.brycewg.asrkb.R
import com.brycewg.asrkb.store.AliasPair
import com.brycewg.asrkb.store.Prefs
import com.brycewg.asrkb.store.Snippet
import com.brycewg.asrkb.store.UserDictionary
import com.brycewg.asrkb.ui.settings.compose.components.SettingsDetailScaffold
import com.brycewg.asrkb.ui.settings.compose.components.SettingsLazyColumn
import com.brycewg.asrkb.ui.settings.compose.components.SettingsMessageDialog
import com.brycewg.asrkb.ui.settings.compose.components.SettingsMessageDialogState
import com.brycewg.asrkb.ui.settings.compose.components.SettingsPreferenceGroup
import com.brycewg.asrkb.ui.settings.compose.components.SettingsSectionContainer
import com.brycewg.asrkb.ui.settings.compose.components.SettingsTextField
import com.brycewg.asrkb.ui.settings.compose.core.BibiUiMode
import com.brycewg.asrkb.ui.settings.compose.core.SettingsActionController
import com.brycewg.asrkb.ui.settings.compose.core.SettingsLayoutMetrics
import com.brycewg.asrkb.ui.settings.compose.model.SettingsEntry

@Composable
fun DictionarySettingsScreen(
    uiMode: BibiUiMode,
    onBack: () -> Unit,
    actions: SettingsActionController
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val clipboard =
        remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    var dict by remember { mutableStateOf(prefs.getUserDictionary()) }
    var aliasesEnabled by remember { mutableStateOf(prefs.dictAliasesEnabled) }
    var vocabPolishEnabled by remember { mutableStateOf(prefs.dictVocabPolishEnabled) }
    var vocabAsrEnabled by remember { mutableStateOf(prefs.dictVocabAsrEnabled) }
    var messageDialog by remember { mutableStateOf<SettingsMessageDialogState?>(null) }

    // 添加区输入状态（词汇单字段；别名/口令双字段）
    var newVocab by remember { mutableStateOf("") }
    var newAliasWrong by remember { mutableStateOf("") }
    var newAliasCorrect by remember { mutableStateOf("") }
    var newSnippetTrigger by remember { mutableStateOf("") }
    var newSnippetContent by remember { mutableStateOf("") }

    fun showMessage(title: String, message: String, confirmText: String = context.getString(android.R.string.ok)) {
        messageDialog = SettingsMessageDialogState(
            title = title,
            message = message,
            confirmText = confirmText
        )
    }

    fun persist(updated: UserDictionary) {
        dict = updated
        prefs.setUserDictionary(updated)
    }

    fun confirmDelete(title: String, message: String, onConfirm: () -> Unit) {
        messageDialog = SettingsMessageDialogState(
            title = title,
            message = message,
            confirmText = context.getString(android.R.string.ok),
            dismissText = context.getString(android.R.string.cancel),
            onConfirm = onConfirm
        )
    }

    SettingsDetailScaffold(
        uiMode = uiMode,
        titleRes = R.string.title_dictionary_settings,
        onBack = onBack
    ) { innerPadding, scrollModifier ->
        SettingsMessageDialog(
            state = messageDialog,
            uiMode = uiMode,
            onDismiss = { messageDialog = null }
        )
        SettingsLazyColumn(
            uiMode = uiMode,
            modifier = Modifier.fillMaxSize(),
            miuixScrollModifier = scrollModifier,
            contentPadding = SettingsLayoutMetrics.pageContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsLayoutMetrics.SectionSpacing)
        ) {
            item("behavior") {
                SettingsSectionContainer(uiMode = uiMode, titleRes = R.string.section_dictionary_behavior) {
                    SettingsPreferenceGroup(
                        SettingsEntry.Switch(
                            id = "dict_aliases_enabled",
                            titleRes = R.string.dict_aliases_switch,
                            summaryRes = R.string.dict_aliases_switch_summary,
                            checked = aliasesEnabled,
                            onCheckedChange = {
                                aliasesEnabled = it
                                prefs.dictAliasesEnabled = it
                            }
                        ),
                        SettingsEntry.Switch(
                            id = "dict_vocab_polish_enabled",
                            titleRes = R.string.dict_vocab_polish_switch,
                            summaryRes = R.string.dict_vocab_polish_switch_summary,
                            checked = vocabPolishEnabled,
                            onCheckedChange = {
                                vocabPolishEnabled = it
                                prefs.dictVocabPolishEnabled = it
                            }
                        ),
                        SettingsEntry.Switch(
                            id = "dict_vocab_asr_enabled",
                            titleRes = R.string.dict_vocab_asr_switch,
                            summaryRes = R.string.dict_vocab_asr_switch_summary,
                            checked = vocabAsrEnabled,
                            onCheckedChange = {
                                vocabAsrEnabled = it
                                prefs.dictVocabAsrEnabled = it
                            }
                        )
                    )
                }
            }

            item("vocabulary") {
                SettingsSectionContainer(uiMode = uiMode, titleRes = R.string.section_dictionary_vocabulary) {
                    if (dict.vocabulary.isEmpty()) {
                        SettingsPreferenceGroup(
                            SettingsEntry.Action(
                                id = "vocab_empty",
                                title = context.getString(R.string.dict_empty_vocabulary),
                                enabled = false,
                                onClick = {}
                            )
                        )
                    } else {
                        SettingsPreferenceGroup(
                            *dict.vocabulary.mapIndexed { index, word ->
                                SettingsEntry.Action(
                                    id = "vocab_$index",
                                    title = word,
                                    icon = Icons.Rounded.Delete,
                                    onClick = {
                                        confirmDelete(
                                            title = word,
                                            message = context.getString(R.string.dict_delete_item_confirm, word)
                                        ) {
                                            persist(dict.copy(vocabulary = dict.vocabulary - word))
                                            messageDialog = null
                                        }
                                    }
                                )
                            }.toTypedArray()
                        )
                    }
                    SettingsTextField(
                        uiMode = uiMode,
                        value = newVocab,
                        onValueChange = { newVocab = it },
                        label = stringResource(R.string.dict_input_word),
                        index = 0,
                        count = 2
                    )
                    SettingsPreferenceGroup(
                        SettingsEntry.Action(
                            id = "vocab_add",
                            titleRes = R.string.dict_add_vocabulary,
                            icon = Icons.Rounded.Add,
                            onClick = {
                                val word = newVocab.trim()
                                if (word.isNotEmpty()) {
                                    persist(
                                        dict.copy(
                                            vocabulary = (dict.vocabulary + word).distinct()
                                        )
                                    )
                                    newVocab = ""
                                }
                            }
                        )
                    )
                }
            }

            item("aliases") {
                SettingsSectionContainer(uiMode = uiMode, titleRes = R.string.section_dictionary_aliases) {
                    if (dict.aliases.isEmpty()) {
                        SettingsPreferenceGroup(
                            SettingsEntry.Action(
                                id = "alias_empty",
                                title = context.getString(R.string.dict_empty_aliases),
                                enabled = false,
                                onClick = {}
                            )
                        )
                    } else {
                        SettingsPreferenceGroup(
                            *dict.aliases.mapIndexed { index, alias ->
                                SettingsEntry.Action(
                                    id = "alias_$index",
                                    title = alias.wrong + " → " + alias.correct,
                                    icon = Icons.Rounded.Delete,
                                    onClick = {
                                        confirmDelete(
                                            title = alias.wrong,
                                            message = context.getString(R.string.dict_delete_item_confirm, alias.wrong)
                                        ) {
                                            persist(dict.copy(aliases = dict.aliases - alias))
                                            messageDialog = null
                                        }
                                    }
                                )
                            }.toTypedArray()
                        )
                    }
                    SettingsTextField(
                        uiMode = uiMode,
                        value = newAliasWrong,
                        onValueChange = { newAliasWrong = it },
                        label = stringResource(R.string.dict_input_wrong),
                        index = 0,
                        count = 3
                    )
                    SettingsTextField(
                        uiMode = uiMode,
                        value = newAliasCorrect,
                        onValueChange = { newAliasCorrect = it },
                        label = stringResource(R.string.dict_input_correct),
                        index = 1,
                        count = 3
                    )
                    SettingsPreferenceGroup(
                        SettingsEntry.Action(
                            id = "alias_add",
                            titleRes = R.string.dict_add_alias,
                            icon = Icons.Rounded.Add,
                            onClick = {
                                val wrong = newAliasWrong.trim()
                                val correct = newAliasCorrect.trim()
                                if (wrong.isNotEmpty() && correct.isNotEmpty()) {
                                    persist(dict.copy(aliases = dict.aliases + AliasPair(wrong, correct)))
                                    newAliasWrong = ""
                                    newAliasCorrect = ""
                                }
                            }
                        )
                    )
                }
            }

            item("snippets") {
                SettingsSectionContainer(uiMode = uiMode, titleRes = R.string.section_dictionary_snippets) {
                    if (dict.snippets.isEmpty()) {
                        SettingsPreferenceGroup(
                            SettingsEntry.Action(
                                id = "snippet_empty",
                                title = context.getString(R.string.dict_empty_snippets),
                                enabled = false,
                                onClick = {}
                            )
                        )
                    } else {
                        SettingsPreferenceGroup(
                            *dict.snippets.mapIndexed { index, snippet ->
                                SettingsEntry.Action(
                                    id = "snippet_$index",
                                    title = snippet.trigger,
                                    summary = snippet.content,
                                    icon = Icons.Rounded.Delete,
                                    onClick = {
                                        confirmDelete(
                                            title = snippet.trigger,
                                            message = context.getString(R.string.dict_delete_item_confirm, snippet.trigger)
                                        ) {
                                            persist(dict.copy(snippets = dict.snippets - snippet))
                                            messageDialog = null
                                        }
                                    }
                                )
                            }.toTypedArray()
                        )
                    }
                    SettingsTextField(
                        uiMode = uiMode,
                        value = newSnippetTrigger,
                        onValueChange = { newSnippetTrigger = it },
                        label = stringResource(R.string.dict_input_trigger),
                        index = 0,
                        count = 3
                    )
                    SettingsTextField(
                        uiMode = uiMode,
                        value = newSnippetContent,
                        onValueChange = { newSnippetContent = it },
                        label = stringResource(R.string.dict_input_content),
                        index = 1,
                        count = 3
                    )
                    SettingsPreferenceGroup(
                        SettingsEntry.Action(
                            id = "snippet_add",
                            titleRes = R.string.dict_add_snippet,
                            icon = Icons.Rounded.Add,
                            onClick = {
                                val trigger = newSnippetTrigger.trim()
                                val content = newSnippetContent.trim()
                                if (trigger.isNotEmpty() && content.isNotEmpty()) {
                                    persist(dict.copy(snippets = dict.snippets + Snippet(trigger, content)))
                                    newSnippetTrigger = ""
                                    newSnippetContent = ""
                                }
                            }
                        )
                    )
                }
            }

            item("transfer") {
                SettingsSectionContainer(uiMode = uiMode, titleRes = R.string.section_dictionary_transfer) {
                    SettingsPreferenceGroup(
                        SettingsEntry.Action(
                            id = "dict_import",
                            titleRes = R.string.dict_import_from_clipboard,
                            icon = Icons.Rounded.ContentPaste,
                            onClick = {
                                val text = clipboard.primaryClip
                                    ?.getItemAt(0)
                                    ?.text
                                    ?.toString()
                                    .orEmpty()
                                val parsed = prefs.parsePcDictionaryJson(text)
                                if (parsed.vocabulary.isEmpty() && parsed.aliases.isEmpty() && parsed.snippets.isEmpty()) {
                                    showMessage(
                                        title = context.getString(R.string.dict_import_from_clipboard),
                                        message = context.getString(R.string.dict_import_empty)
                                    )
                                } else {
                                    persist(parsed)
                                    showMessage(
                                        title = context.getString(R.string.dict_import_from_clipboard),
                                        message = context.getString(
                                            R.string.dict_import_ok,
                                            parsed.vocabulary.size,
                                            parsed.aliases.size,
                                            parsed.snippets.size
                                        )
                                    )
                                }
                            }
                        ),
                        SettingsEntry.Action(
                            id = "dict_export",
                            titleRes = R.string.dict_export_to_clipboard,
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("dictionary.json", prefs.exportPcDictionaryJson(dict))
                                )
                                showMessage(
                                    title = context.getString(R.string.dict_export_to_clipboard),
                                    message = context.getString(R.string.dict_export_ok)
                                )
                            }
                        ),
                        SettingsEntry.Action(
                            id = "dict_clear",
                            titleRes = R.string.dict_clear_all,
                            icon = Icons.Rounded.ClearAll,
                            onClick = {
                                confirmDelete(
                                    title = context.getString(R.string.dict_clear_all),
                                    message = context.getString(R.string.dict_clear_confirm)
                                ) {
                                    persist(UserDictionary())
                                    messageDialog = null
                                }
                            }
                        )
                    )
                }
            }
        }
    }
}
