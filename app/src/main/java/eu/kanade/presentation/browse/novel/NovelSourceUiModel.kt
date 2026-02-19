package eu.kanade.presentation.browse.novel

import tachiyomi.domain.source.novel.model.Source

sealed interface NovelSourceUiModel {
    data class Item(val source: Source) : NovelSourceUiModel
    data class Header(val language: String, val isCollapsed: Boolean) : NovelSourceUiModel
}
