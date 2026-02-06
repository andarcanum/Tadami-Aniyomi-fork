package tachiyomi.domain.extension.novel.model

sealed class NovelPlugin {
    abstract val id: String
    abstract val name: String
    abstract val site: String
    abstract val lang: String
    abstract val version: Int
    abstract val url: String
    abstract val iconUrl: String?
    abstract val customJs: String?
    abstract val customCss: String?
    abstract val hasSettings: Boolean
    abstract val sha256: String
    abstract val repoUrl: String

    data class Available(
        override val id: String,
        override val name: String,
        override val site: String,
        override val lang: String,
        override val version: Int,
        override val url: String,
        override val iconUrl: String?,
        override val customJs: String?,
        override val customCss: String?,
        override val hasSettings: Boolean,
        override val sha256: String,
        override val repoUrl: String,
    ) : NovelPlugin()

    data class Installed(
        override val id: String,
        override val name: String,
        override val site: String,
        override val lang: String,
        override val version: Int,
        override val url: String,
        override val iconUrl: String?,
        override val customJs: String?,
        override val customCss: String?,
        override val hasSettings: Boolean,
        override val sha256: String,
        override val repoUrl: String,
    ) : NovelPlugin()
}
