package eu.kanade.tachiyomi.data.cache

import android.content.ComponentCallbacks2
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EntryMemoryCacheTrimCallbacksTest {

    @Test
    @Suppress("DEPRECATION")
    fun `ignores mild memory trim levels below RUNNING_LOW`() {
        var cleared = 0
        val callbacks = EntryMemoryCacheTrimCallbacks(clearCaches = { cleared++ })

        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        cleared shouldBe 0
    }

    @Test
    @Suppress("DEPRECATION")
    fun `clears caches on RUNNING_LOW and higher trim levels`() {
        var cleared = 0
        val callbacks = EntryMemoryCacheTrimCallbacks(clearCaches = { cleared++ })

        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        cleared shouldBe 4
    }

    @Test
    fun `clears caches on onLowMemory`() {
        var cleared = 0
        val callbacks = EntryMemoryCacheTrimCallbacks(clearCaches = { cleared++ })

        callbacks.onLowMemory()

        cleared shouldBe 1
    }
}
