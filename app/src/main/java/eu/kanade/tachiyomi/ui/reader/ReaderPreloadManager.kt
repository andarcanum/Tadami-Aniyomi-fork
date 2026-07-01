package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Global manager to hold dynamic preloading configuration computed based on reading speed and network.
 */
object ReaderPreloadManager {
    private val readerPreferences: ReaderPreferences by lazy { Injekt.get() }

    @Volatile
    private var _dynamicPreloadPagesAfter: Int? = null

    var dynamicPreloadPagesAfter: Int
        get() {
            return if (readerPreferences.adaptivePreload().get()) {
                _dynamicPreloadPagesAfter ?: readerPreferences.preloadPagesAfter().get()
            } else {
                readerPreferences.preloadPagesAfter().get()
            }
        }
        set(value) {
            _dynamicPreloadPagesAfter = value
        }

    val nextChapterPreloadThreshold: Int
        get() {
            return if (readerPreferences.adaptivePreload().get()) {
                when (_dynamicPreloadPagesAfter) {
                    6 -> 10
                    2 -> 3
                    else -> 5
                }
            } else {
                5
            }
        }
}
