package eu.kanade.presentation.reader.novel

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import tachiyomi.i18n.aniyomi.AYMR

class NovelReaderWebView(context: Context) : WebView(context) {
    var localSelection: NovelSelectedTextSelection? = null
    var onSelectedTextSelectionChanged: ((NovelSelectedTextSelection?) -> Unit)? = null
    var isExecutingAction = false
    var isDictionaryEnabled = false
    var isTranslationEnabled = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val wrappedCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val result = callback?.onCreateActionMode(mode, menu) ?: true
                if (menu != null) {
                    val menuOrder = 100
                    if (isDictionaryEnabled) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_DICTIONARY,
                            menuOrder,
                            context.getString(AYMR.strings.novel_reader_text_selection_action_dictionary.resourceId),
                        )
                    }
                    if (isTranslationEnabled) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_TRANSLATION,
                            menuOrder + 1,
                            context.getString(AYMR.strings.novel_reader_text_selection_action_translate.resourceId),
                        )
                    }
                    if (localSelection?.selectionAnchor != null) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_HIGHLIGHT,
                            menuOrder + 2,
                            context.getString(AYMR.strings.novel_highlight_action_save.resourceId),
                        )
                    }
                }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return callback?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item != null) {
                    val selection = localSelection
                    if (selection != null) {
                        when (item.itemId) {
                            MENU_ID_DICTIONARY -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.DICTIONARY)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                mode?.finish()
                                return true
                            }
                            MENU_ID_TRANSLATION -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.TRANSLATION)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                mode?.finish()
                                return true
                            }
                            MENU_ID_HIGHLIGHT -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.HIGHLIGHT)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(AYMR.strings.novel_highlight_saved.resourceId),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                mode?.finish()
                                return true
                            }
                        }
                    }
                }
                return callback?.onActionItemClicked(mode, item) ?: false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                callback?.onDestroyActionMode(mode)
            }
        }
        return super.startActionMode(wrappedCallback, type)
    }
}

fun createNovelReaderWebView(context: Context): WebView {
    return NovelReaderWebView(context)
}
