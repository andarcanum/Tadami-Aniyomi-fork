package eu.kanade.tachiyomi.extension.novel.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelPluginCapabilitiesTest {

    @Test
    fun `capability model exposes all expected flags`() {
        val capabilities = NovelPluginCapabilities(
            hasParsePage = true,
            hasResolveUrl = false,
            hasFetchImage = true,
            hasPluginSettings = false,
            usesWebStorage = true,
            hasCustomJs = false,
            hasCustomCss = true,
        )

        assertTrue(capabilities.hasParsePage)
        assertFalse(capabilities.hasResolveUrl)
        assertTrue(capabilities.hasFetchImage)
        assertFalse(capabilities.hasPluginSettings)
        assertTrue(capabilities.usesWebStorage)
        assertFalse(capabilities.hasCustomJs)
        assertTrue(capabilities.hasCustomCss)
    }

    @Test
    fun `default capability model has all flags false`() {
        val capabilities = NovelPluginCapabilities()

        assertFalse(capabilities.hasParsePage)
        assertFalse(capabilities.hasResolveUrl)
        assertFalse(capabilities.hasFetchImage)
        assertFalse(capabilities.hasPluginSettings)
        assertFalse(capabilities.usesWebStorage)
        assertFalse(capabilities.hasCustomJs)
        assertFalse(capabilities.hasCustomCss)
    }

    @Test
    fun `capability model can be copied with modified flags`() {
        val original = NovelPluginCapabilities(
            hasParsePage = true,
            hasResolveUrl = false,
        )

        val modified = original.copy(hasResolveUrl = true)

        assertTrue(modified.hasParsePage)
        assertTrue(modified.hasResolveUrl)
    }

    @Test
    fun `capability model equality works correctly`() {
        val cap1 = NovelPluginCapabilities(hasParsePage = true, hasFetchImage = true)
        val cap2 = NovelPluginCapabilities(hasParsePage = true, hasFetchImage = true)
        val cap3 = NovelPluginCapabilities(hasParsePage = true, hasFetchImage = false)

        assertEquals(cap1, cap2)
        assertFalse(cap1 == cap3)
    }

    @Test
    fun `capability model toString is readable`() {
        val capabilities = NovelPluginCapabilities(
            hasParsePage = true,
            hasPluginSettings = true,
        )

        val str = capabilities.toString()
        assertTrue(str.contains("hasParsePage=true"))
        assertTrue(str.contains("hasPluginSettings=true"))
        assertTrue(str.contains("hasResolveUrl=false"))
    }
}
