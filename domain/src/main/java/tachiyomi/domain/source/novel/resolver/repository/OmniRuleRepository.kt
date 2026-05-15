package tachiyomi.domain.source.novel.resolver.repository

import tachiyomi.domain.source.novel.resolver.model.OmniRule

interface OmniRuleRepository {
    suspend fun getRuleByDomain(domain: String): OmniRule?
    suspend fun insertRule(rule: OmniRule)
    suspend fun deleteRule(domain: String)
}
