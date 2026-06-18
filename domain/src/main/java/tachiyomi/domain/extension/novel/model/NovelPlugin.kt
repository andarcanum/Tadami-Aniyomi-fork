package tachiyomi.domain.extension.novel.model

sealed class NovelPlugin {
    abstract val id: String
    abstract val name: String
    abstract val site: String
    abstract val lang: String
    abstract val versionCode: Int
    abstract val versionName: String
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
        override val versionCode: Int,
        override val versionName: String,
        override val url: String,
        override val iconUrl: String?,
        override val customJs: String?,
        override val customCss: String?,
        override val hasSettings: Boolean,
        override val sha256: String,
        override val repoUrl: String,
        val repoName: String = "",
        val pkgName: String? = null,
        val apkUrl: String? = null,
        val isKotlinExtension: Boolean = false,
    ) : NovelPlugin()

    data class Installed(
        override val id: String,
        override val name: String,
        override val site: String,
        override val lang: String,
        override val versionCode: Int,
        override val versionName: String,
        override val url: String,
        override val iconUrl: String?,
        override val customJs: String?,
        override val customCss: String?,
        override val hasSettings: Boolean,
        override val sha256: String,
        override val repoUrl: String,
        val repoName: String? = null,
        val pkgName: String? = null,
        val apkUrl: String? = null,
        val isKotlinExtension: Boolean = false,
    ) : NovelPlugin()
}
