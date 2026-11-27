package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.TranslationProfile

/** Public configuration only. Device vault records and runtime routing/budgets have no wire slots. */
@Serializable
data class BackupTranslationConfiguration(
    @ProtoNumber(1) val version: Int = 1,
    @ProtoNumber(2) val configurationJson: String,
) {
    internal fun decode(): TranslationConfigurationData = checkedConfiguration {
        require(version == VERSION)
        checkSize(configurationJson)
        json.decodeFromString<TranslationConfigurationData>(configurationJson).also { it.validate() }
    }

    override fun toString(): String = "BackupTranslationConfiguration(version=$version)"

    companion object {
        private const val VERSION = 1
        internal const val MAX_JSON_BYTES = 16 * 1_048_576
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
            isLenient = false
            coerceInputValues = false
        }

        internal fun fromCatalog(
            catalog: ProfileCatalog,
            novelAssignments: List<BackupTranslationNovelAssignment> = emptyList(),
        ): BackupTranslationConfiguration = checkedConfiguration {
            require(novelAssignments.size == catalog.assignments.count { it.scope == ProfileAssignmentScope.NOVEL })
            val data = TranslationConfigurationData(
                catalog.connections,
                catalog.profiles,
                catalog.credentials,
                catalog.assignments.filter { it.scope != ProfileAssignmentScope.NOVEL },
                catalog.legacyReferences,
                novelAssignments,
            )
            data.validate()
            checkEncodedSize(data)
            val encoded = json.encodeToString(data)
            checkSize(encoded)
            BackupTranslationConfiguration(VERSION, encoded)
        }

        // Count actual UTF-8 entries before materializing the whole JSON string.
        private fun checkEncodedSize(data: TranslationConfigurationData) {
            var size = json.encodeToString(TranslationConfigurationData()).toByteArray(Charsets.UTF_8).size.toLong()
            fun <T> add(values: List<T>, serializer: KSerializer<T>) {
                values.forEachIndexed { index, value ->
                    size += json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8).size
                    if (index > 0) size++
                    require(size <= MAX_JSON_BYTES)
                }
            }
            add(data.connections, ConnectionProfile.serializer())
            add(data.profiles, TranslationProfile.serializer())
            add(data.credentials, CredentialMetadata.serializer())
            add(data.assignments, ProfileAssignment.serializer())
            add(data.legacyReferences, LegacyCredentialReference.serializer())
            add(data.novelAssignments, BackupTranslationNovelAssignment.serializer())
        }

        private fun checkSize(value: String) {
            require(value.length <= MAX_JSON_BYTES && value.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES)
        }
    }
}

/** Never carries an old SQL novel ID: resolution is solely by this exact source and URL pair. */
@Serializable
internal data class BackupTranslationNovelAssignment(val source: Long, val url: String, val profileId: String) {
    init {
        require(url.isNotBlank() && url.length <= 8192 && url.none(Char::isISOControl))
        ProfileAssignment(ProfileAssignmentScope.NOVEL, 0, profileId)
    }
    override fun toString(): String = "BackupTranslationNovelAssignment(profile=$profileId)"
}

@Serializable
internal data class TranslationConfigurationData(
    val connections: List<ConnectionProfile> = emptyList(),
    val profiles: List<TranslationProfile> = emptyList(),
    val credentials: List<CredentialMetadata> = emptyList(),
    val assignments: List<ProfileAssignment> = emptyList(),
    val legacyReferences: List<LegacyCredentialReference> = emptyList(),
    val novelAssignments: List<BackupTranslationNovelAssignment> = emptyList(),
) {
    fun toCatalog(resolvedNovelAssignments: List<ProfileAssignment> = emptyList()) = ProfileCatalog(
        connections,
        profiles,
        credentials,
        assignments + resolvedNovelAssignments,
        legacyReferences,
    )

    fun validate() {
        require(
            listOf(
                connections.size,
                profiles.size,
                credentials.size,
                assignments.size,
                legacyReferences.size,
                novelAssignments.size,
            )
                .sumOf { it.toLong() } <= 100_000L,
        )
        val connectionIds = connections.map { it.id }.toSet()
        val profileIds = profiles.map { it.id }.toSet()
        val credentialIds = credentials.map { it.id }.toSet()
        require(
            connectionIds.size == connections.size && profileIds.size == profiles.size &&
                credentialIds.size == credentials.size,
        )
        require(assignments.none { it.scope == ProfileAssignmentScope.NOVEL })
        require(assignments.map { it.scope to it.scopeId }.distinct().size == assignments.size)
        require(
            legacyReferences.map {
                Triple(it.provider, it.scope, it.scopeId)
            }.distinct().size == legacyReferences.size,
        )
        require(novelAssignments.map { it.source to it.url }.distinct().size == novelAssignments.size)
        require(profiles.all { it.connectionId in connectionIds })
        require(
            connections.all { connection ->
                connection.credentialBindings.all { it.credentialId in credentialIds }
            },
        )
        require(assignments.all { it.profileId in profileIds } && novelAssignments.all { it.profileId in profileIds })
        require(legacyReferences.all { it.credentialId in credentialIds })
    }

    override fun toString(): String = "TranslationConfigurationData(profiles=" + profiles.size + ")"
}

private inline fun <T> checkedConfiguration(block: () -> T): T = try {
    block()
} catch (_: Exception) {
    throw IllegalArgumentException("Invalid or unsupported translation configuration backup")
}
