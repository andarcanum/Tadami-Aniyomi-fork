package tachiyomi.domain.source.novel.model

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter

@Suppress("OverridingDeprecatedMember")
class StubNovelSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : NovelSource {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override suspend fun getNovelDetails(novel: SNovel): SNovel =
        throw SourceNotInstalledException()

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> =
        throw SourceNotInstalledException()

    override suspend fun getChapterText(chapter: SNovelChapter): String =
        throw SourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: NovelSource): StubNovelSource {
            return StubNovelSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()
