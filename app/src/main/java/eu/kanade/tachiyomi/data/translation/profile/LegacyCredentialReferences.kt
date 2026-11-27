package eu.kanade.tachiyomi.data.translation.profile

import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope

/** Public reference metadata only. Synchronous reader checks never open the vault or database. */
internal object LegacyCredentialReferences {
    @Volatile private var references: List<LegacyCredentialReference> = emptyList()

    @Synchronized
    fun replace(values: List<LegacyCredentialReference>) {
        references = values.toList()
    }

    @Synchronized
    fun remember(value: LegacyCredentialReference) {
        references = references.filterNot {
            it.provider == value.provider && it.scope == value.scope && it.scopeId == value.scopeId
        } + value
    }

    fun contains(provider: LegacyProvider, scope: LegacyCredentialScope, id: Long): Boolean =
        references.any { it.provider == provider.name && it.scope == scope && it.scopeId == id }
}
