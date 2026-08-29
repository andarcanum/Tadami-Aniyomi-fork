package eu.kanade.presentation.reader.novel

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
        // Chromium shows selection drag handles only for a focusable WebView: with this view
        // unfocusable, long-press produced the word menu but never the drag handles.
        // focusableInTouchMode stays off so taps do not steal key/input focus from the reader.
        isFocusable = true
        isFocusableInTouchMode = false
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        // Chromium installs an ActionMode.Callback2 (SelectionActionModeDelegate) that anchors
        // the floating toolbar to the selection via onGetContentRect. Re-wrapping it as a plain
        // Callback loses that anchor: the system degrades the mode to a docked primary window,
        // and Chromium then hides the selection drag handles entirely (word-only selection).
        // Preserve the Callback2 subtype - and its content rect - through the wrapper.
        val wrappedCallback: ActionMode.Callback = when (callback) {
            is ActionMode.Callback2 -> object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    addReaderSelectionActions(mode, menu)
                    return callback.onCreateActionMode(mode, menu)
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    return callback.onPrepareActionMode(mode, menu)
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    return handleReaderSelectionAction(item, mode) ||
                        callback.onActionItemClicked(mode, item)
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    callback.onDestroyActionMode(mode)
                }

                override fun onGetContentRect(
                    mode: ActionMode,
                    view: View,
                    outRect: Rect,
                ) {
                    callback.onGetContentRect(mode, view, outRect)
                }
            }
            else -> object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    addReaderSelectionActions(mode, menu)
                    return callback?.onCreateActionMode(mode, menu) ?: true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    return callback?.onPrepareActionMode(mode, menu) ?: false
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    return handleReaderSelectionAction(item, mode) ||
                        callback?.onActionItemClicked(mode, item) ?: false
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    callback?.onDestroyActionMode(mode)
                }
            }
        }
        return super.startActionMode(wrappedCallback, type)
    }

    private fun addReaderSelectionActions(mode: ActionMode?, menu: Menu?) {
        if (menu == null) return
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

    private fun handleReaderSelectionAction(item: MenuItem?, mode: ActionMode?): Boolean {
        if (item == null) return false
        val selection = localSelection ?: return false
        when (item.itemId) {
            MENU_ID_DICTIONARY -> {
                isExecutingAction = true
                onSelectedTextSelectionChanged?.invoke(
                    selection.copy(triggerAction = SelectedTextAction.DICTIONARY),
                )
                mode?.finish()
                return true
            }
            MENU_ID_TRANSLATION -> {
                isExecutingAction = true
                onSelectedTextSelectionChanged?.invoke(
                    selection.copy(triggerAction = SelectedTextAction.TRANSLATION),
                )
                mode?.finish()
                return true
            }
            MENU_ID_HIGHLIGHT -> {
                isExecutingAction = true
                android.widget.Toast.makeText(
                    context,
                    context.getString(AYMR.strings.novel_highlight_saved.resourceId),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                mode?.finish()
                return true
            }
        }
        return false
    }
}

fun createNovelReaderWebView(context: Context): WebView {
    return NovelReaderWebView(context)
}
