package tachiyomi.source.local.filter.novel

import android.content.Context
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

sealed class NovelOrderBy(context: Context, selection: Selection) : NovelFilter.Sort(
    context.stringResource(MR.strings.local_filter_order_by),
    arrayOf(context.stringResource(MR.strings.title), context.stringResource(MR.strings.date)),
    selection,
) {
    class Popular(context: Context) : NovelOrderBy(context, Selection(0, true))
    class Latest(context: Context) : NovelOrderBy(context, Selection(1, false))
}
