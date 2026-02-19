package mihon.domain.extensionrepo.novel.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.model.ExtensionRepo

interface NovelExtensionRepoRepository {
    fun subscribeAll(): Flow<List<ExtensionRepo>>

    suspend fun getAll(): List<ExtensionRepo>

    suspend fun getRepo(baseUrl: String): ExtensionRepo?

    suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo?

    fun getCount(): Flow<Int>

    suspend fun insertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    )

    suspend fun upsertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    )

    suspend fun upsertRepo(repo: ExtensionRepo) {
        upsertRepo(
            repo.baseUrl,
            repo.name,
            repo.shortName,
            repo.website,
            repo.signingKeyFingerprint,
        )
    }

    suspend fun replaceRepo(newRepo: ExtensionRepo)

    suspend fun deleteRepo(baseUrl: String)
}
