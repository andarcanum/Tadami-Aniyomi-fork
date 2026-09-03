package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences

/**
 * Persists auto-scroll settings, honouring per-source overrides.
 *
 * Extracted from [NovelReaderContentHost]: the five persist functions there shared the same
 * shape (write to the source override when one exists, otherwise to the global preference), so
 * they collapsed into one generic writer. The constructor takes plain values, not Compose state:
 * [NovelReaderPreferences] comes from `Injekt.get` and [sourceId]/[hasSourceOverride] are plain
 * fields of the reader state, so instances are safe to keep outside composition.
 */
internal class NovelReaderAutoScrollPreferenceWriter(
    private val readerPreferences: NovelReaderPreferences,
    private val sourceId: Long,
    private val hasSourceOverride: Boolean,
) {
    fun persistAutoScrollEnabledPreference(enabled: Boolean) {
        writeSourceAware(
            global = { readerPreferences.autoScroll().set(enabled) },
            source = { override ->
                override.copy(autoScroll = enabled)
            },
        )
    }

    fun persistAutoScrollIntervalPreference(interval: Int) {
        writeSourceAware(
            global = { readerPreferences.autoScrollInterval().set(interval) },
            source = { override ->
                override.copy(autoScrollInterval = interval)
            },
        )
    }

    fun persistAutoScrollAdaptiveDelayPreference(enabled: Boolean) {
        writeSourceAware(
            global = { readerPreferences.autoScrollAdaptiveDelay().set(enabled) },
            source = { override ->
                override.copy(autoScrollAdaptiveDelay = enabled)
            },
        )
    }

    fun persistAutoScrollChapterEndBehaviorPreference(behavior: NovelAutoScrollChapterEndBehavior) {
        writeSourceAware(
            global = { readerPreferences.autoScrollChapterEndBehavior().set(behavior) },
            source = { override ->
                override.copy(autoScrollChapterEndBehavior = behavior)
            },
        )
    }

    fun persistAutoScrollEndPauseMsPreference(pauseMs: Long) {
        writeSourceAware(
            global = { readerPreferences.autoScrollEndPauseMs().set(pauseMs) },
            source = { override ->
                override.copy(autoScrollEndPauseMs = pauseMs)
            },
        )
    }

    fun persistShowFloatingButtonPreference(visible: Boolean) {
        writeSourceAware(
            global = { readerPreferences.showAutoScrollFloatingButton().set(visible) },
            source = { override ->
                override.copy(showAutoScrollFloatingButton = visible)
            },
        )
    }

    private inline fun writeSourceAware(
        global: () -> Unit,
        noinline source: (NovelReaderOverride) -> NovelReaderOverride,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId, source)
        } else {
            global()
        }
    }
}
