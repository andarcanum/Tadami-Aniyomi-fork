package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.ContentPreferenceOption
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.SearchSuggestion
import eu.kanade.tachiyomi.animesource.model.SearchSuggestionKind
import eu.kanade.tachiyomi.animesource.model.SearchSuggestions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeFeedContractV20Test {

    private class FakeWebLogin : AnimeFeedWebLoginSource {
        var imported = false
        override fun webLoginUrl(): String = "https://example.invalid/"
        override fun ownAuthorizeUrl(): String = "https://example.invalid/oauth2/auth?state=own"
        override fun isOwnLoginRedirect(url: String): Boolean = url.contains("state=own")
        override suspend fun importWebRedirect(url: String, cookies: Map<String, String>): Boolean {
            imported = url.contains("code=")
            return imported
        }
        override suspend fun importWebSession(
            cookies: Map<String, String>,
            localStorage: Map<String, String>,
        ): Boolean {
            imported = localStorage.values.any { it.contains("access_token") }
            return imported
        }
    }

    private class FakeBrowse : AnimeFeedBrowseSource {
        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage =
            FeedCategoryPage(
                categories = listOf(FeedCategory(id = "c1", name = "niche1", imageUrl = null, itemCount = 3)),
                hasNextPage = page < 2,
            )
        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(videos = emptyList(), hasNextPage = false)
    }

    private class FakeCategorized : AnimeCategorizedSearchSource {
        override suspend fun getCategorizedSearch(query: String): SearchSuggestions =
            SearchSuggestions(
                niches = listOf(SearchSuggestion("n1", "niche1", SearchSuggestionKind.NICHE)),
                creators = listOf(SearchSuggestion("u1", "creator1", SearchSuggestionKind.CREATOR)),
                tags = listOf(SearchSuggestion("t1", "tag1", SearchSuggestionKind.TAG)),
            )
    }

    private class FakePreferences : AnimeContentPreferencesSource {
        var enabled: List<String> = listOf("a")
        override suspend fun getContentPreferences(): List<ContentPreferenceOption> =
            listOf(
                ContentPreferenceOption("a", "A", true),
                ContentPreferenceOption("b", "B", false),
            )
        override suspend fun setContentPreferences(enabledIds: List<String>): Boolean {
            enabled = enabledIds
            return true
        }
    }

    private class FakeSubscriptions : AnimeCategorySubscriptionSource {
        val followed = mutableSetOf("c1")
        var lastCall: Pair<String, Boolean>? = null
        override suspend fun getSubscribedCategoryIds(): List<String> = followed.toList()
        override suspend fun setCategorySubscription(categoryId: String, followed: Boolean): Boolean {
            lastCall = categoryId to followed
            if (followed) this.followed += categoryId else this.followed -= categoryId
            return true
        }
    }

    @Test
    fun webLoginCapabilityIsInstanceofDetected() {
        val source: Any = FakeWebLogin()
        assertTrue(source is AnimeFeedWebLoginSource)
        assertFalse(source is AnimeFeedLoginSource)
    }

    @Test
    fun browseAndCategorizedCapabilitiesAreIndependent() {
        val browse: Any = FakeBrowse()
        val categorized: Any = FakeCategorized()
        assertTrue(browse is AnimeFeedBrowseSource)
        assertTrue(categorized is AnimeCategorizedSearchSource)
        assertFalse(browse is AnimeCategorizedSearchSource)
    }

    @Test
    fun contentPreferencesCapabilityIsInstanceofDetected() {
        val source: Any = FakePreferences()
        assertTrue(source is AnimeContentPreferencesSource)
        assertFalse(source is AnimeFeedBrowseSource)
    }

    @Test
    fun categorySubscriptionCapabilityIsInstanceofDetected() {
        val source: Any = FakeSubscriptions()
        assertTrue(source is AnimeCategorySubscriptionSource)
        assertFalse(source is AnimeFeedBrowseSource)
    }

    @Test
    fun modelDefaultsAreStable() {
        val category = FeedCategory(id = "c", name = "n")
        assertNull(category.imageUrl)
        assertNull(category.itemCount)
        val page = FeedCategoryPage(categories = emptyList(), hasNextPage = true)
        assertNull(page.nextCursor)
        val suggestion = SearchSuggestion("i", "l", SearchSuggestionKind.TAG)
        assertNull(suggestion.imageUrl)
        assertNull(suggestion.subtitle)
        val suggestions = SearchSuggestions()
        assertTrue(suggestions.niches.isEmpty())
        assertTrue(suggestions.creators.isEmpty())
        assertTrue(suggestions.tags.isEmpty())
    }
}
