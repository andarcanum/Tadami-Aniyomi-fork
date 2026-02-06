package eu.kanade.tachiyomi.extension.novel.repo

interface NovelPluginStorage {
    suspend fun save(pkg: NovelPluginPackage)
    suspend fun get(id: String): NovelPluginPackage?
    suspend fun getAll(): List<NovelPluginPackage>
}

class InMemoryNovelPluginStorage : NovelPluginStorage {
    private val storage = mutableMapOf<String, NovelPluginPackage>()

    override suspend fun save(pkg: NovelPluginPackage) {
        storage[pkg.entry.id] = pkg
    }

    override suspend fun get(id: String): NovelPluginPackage? = storage[id]

    override suspend fun getAll(): List<NovelPluginPackage> = storage.values.toList()
}
