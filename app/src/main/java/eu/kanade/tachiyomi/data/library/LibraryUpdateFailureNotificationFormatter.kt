package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.util.lang.chop
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

data class LibraryUpdateFailureNotificationText(
    val contentText: CharSequence,
    val bigText: CharSequence?,
)

object LibraryUpdateFailureNotificationFormatter {
    private const val MAX_VISIBLE_ITEMS = 3
    private const val MAX_TITLE_LENGTH = 40
    private const val MAX_SOURCE_LENGTH = 24
    private const val MAX_REASON_LENGTH = 70

    fun build(
        context: Context,
        failures: List<LibraryUpdateFailure>,
        hideContent: Boolean,
    ): LibraryUpdateFailureNotificationText {
        return buildText(
            failures = failures,
            hideContent = hideContent,
            actionShowErrorsText = context.stringResource(MR.strings.action_show_errors),
            unknownErrorText = context.stringResource(MR.strings.unknown_error),
            moreText = { count -> context.stringResource(MR.strings.notification_update_error_more, count) },
        )
    }

    internal fun buildText(
        failures: List<LibraryUpdateFailure>,
        hideContent: Boolean,
        actionShowErrorsText: String,
        unknownErrorText: String,
        moreText: (Int) -> String,
    ): LibraryUpdateFailureNotificationText {
        if (hideContent) {
            return LibraryUpdateFailureNotificationText(
                contentText = actionShowErrorsText,
                bigText = null,
            )
        }

        if (failures.isEmpty()) {
            return LibraryUpdateFailureNotificationText(
                contentText = actionShowErrorsText,
                bigText = null,
            )
        }

        val visibleFailures = failures.take(MAX_VISIBLE_ITEMS)
        val lines = visibleFailures.map { buildLine(it, unknownErrorText) }.toMutableList()
        val remaining = failures.size - visibleFailures.size
        if (remaining > 0) {
            lines.add(moreText(remaining))
        }

        return LibraryUpdateFailureNotificationText(
            contentText = visibleFailures.firstOrNull()?.let { buildLine(it, unknownErrorText) }
                ?: actionShowErrorsText,
            bigText = lines.joinToString("\n"),
        )
    }

    private fun buildLine(
        failure: LibraryUpdateFailure,
        unknownErrorText: String,
    ): String {
        val reason = failure.reason
            .orEmpty()
            .ifBlank { unknownErrorText }
            .chop(MAX_REASON_LENGTH)
        val sourceName = failure.sourceName
            .takeIf { it.isNotBlank() }
            ?.chop(MAX_SOURCE_LENGTH)
            ?.let { " • $it" }
            .orEmpty()

        return "${failure.title.chop(MAX_TITLE_LENGTH)}$sourceName — $reason"
    }
}
