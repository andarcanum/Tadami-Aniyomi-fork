package tachiyomi.data.source.novel

import tachiyomi.domain.source.novel.resolver.model.OmniRule
import tachiyomi.domain.source.novel.resolver.model.PaginationType
import tachiyomi.domain.source.novel.resolver.repository.OmniRuleRepository
import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class OmniRuleRepositoryImpl(
    private val handler: NovelDatabaseHandler
) : OmniRuleRepository {
    override suspend fun getRuleByDomain(domain: String): OmniRule? {
        return handler.awaitOneOrNull { db -> db.omni_rulesQueries.getRuleByDomain(domain, ::mapRule) }
    }

    override suspend fun insertRule(rule: OmniRule) {
        handler.await { db ->
            db.omni_rulesQueries.insertRule(
                domain = rule.domain,
                title_selector = rule.titleSelector,
                author_selector = rule.authorSelector,
                cover_selector = rule.coverSelector,
                description_selector = rule.descriptionSelector,
                chapter_list_selector = rule.chapterListSelector,
                chapter_name_selector = rule.chapterNameSelector,
                chapter_url_selector = rule.chapterUrlSelector,
                pagination_selector = rule.paginationSelector,
                pagination_type = rule.paginationType.name,
                content_selector = rule.contentSelector,
                remove_selectors = rule.removeSelectors
            )
        }
    }

    override suspend fun deleteRule(domain: String) {
        handler.await { db -> db.omni_rulesQueries.deleteRule(domain) }
    }

    private fun mapRule(
        domain: String,
        titleSelector: String?,
        authorSelector: String?,
        coverSelector: String?,
        descriptionSelector: String?,
        chapterListSelector: String,
        chapterNameSelector: String?,
        chapterUrlSelector: String?,
        paginationSelector: String?,
        paginationType: String,
        contentSelector: String,
        removeSelectors: String?
    ): OmniRule {
        return OmniRule(
            domain, titleSelector, authorSelector, coverSelector, descriptionSelector,
            chapterListSelector, chapterNameSelector, chapterUrlSelector,
            paginationSelector, PaginationType.valueOf(paginationType),
            contentSelector, removeSelectors
        )
    }
}
