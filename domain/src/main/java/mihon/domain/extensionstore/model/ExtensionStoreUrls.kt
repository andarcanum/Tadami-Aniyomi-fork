package mihon.domain.extensionstore.model

/**
 * File names that extension repos host next to their base url. A repo can legitimately be
 * referenced by any of them, because users paste whichever url they happen to find, and
 * because legacy repos, store indexes and novel plugin repos use different file names.
 *
 * Every conversion between a repo base url and an index url must therefore go through
 * [toExtensionStoreBaseUrl] instead of blindly appending or stripping one specific suffix.
 * Appending without normalizing first is what persisted urls such as
 * `.../repo/repo.json/repo.json`, which then fail with HTTP 404 on every store refresh and
 * extension list fetch.
 */
private val INDEX_FILE_NAMES = listOf(
    "repo.json",
    "index.min.json",
    "index.json",
    "index.pb",
    "plugins.min.json",
    "plugins.json",
)

/** Whether [this] points at a repo index/metadata file instead of a repo base url. */
fun String.isExtensionStoreIndexUrl(): Boolean {
    val trimmed = trim().trimEnd('/')
    return INDEX_FILE_NAMES.any { trimmed.endsWith("/$it", ignoreCase = true) }
}

/**
 * Canonical repo base url: the url without trailing slashes and without any (possibly
 * repeated) index file suffix. Idempotent, so it is safe to apply to already stored values.
 */
fun String.toExtensionStoreBaseUrl(): String {
    var url = trim().trimEnd('/')
    while (true) {
        val name = INDEX_FILE_NAMES.firstOrNull { url.endsWith("/$it", ignoreCase = true) } ?: break
        url = url.dropLast(name.length + 1).trimEnd('/')
    }
    return url
}

/** Canonical `repo.json` metadata url of a legacy repo, derived from any of its urls. */
fun String.toLegacyExtensionRepoUrl(): String = "${toExtensionStoreBaseUrl()}/repo.json"

/** Canonical `index.min.json` extension listing url of a legacy repo. */
fun String.toLegacyExtensionIndexUrl(): String = "${toExtensionStoreBaseUrl()}/index.min.json"

/**
 * Collapses a duplicated index file suffix while keeping the file name the repo was added
 * with, e.g. `.../repo/repo.json/repo.json` becomes `.../repo/repo.json`. Used to repair
 * values that were already written to the database.
 */
fun String.collapseDuplicateExtensionStoreSuffix(): String {
    val trimmed = trim().trimEnd('/')
    val base = trimmed.toExtensionStoreBaseUrl()
    if (base == trimmed) return trimmed
    val fileName = trimmed.removePrefix(base).trim('/').substringBefore('/')
    return if (fileName.isBlank()) base else "$base/$fileName"
}

/**
 * Best-effort human-readable label for a repo/store url or a plain store name.
 *
 * The repo picker dialogs and extension-screen badges used to fall back to the bare URL
 * host ("github.com") which conveys nothing. This collapses github-style urls to
 * "owner/repo" (or "owner" when the repo segment is missing), gitlab-style hosts to their
 * first two path segments, and leaves plain names ("NovelSourcery") untouched.
 */
fun String.repoDisplayNameFallback(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""
    if (!trimmed.contains("://")) {
        // A plain name such as "NovelSourcery", not a url.
        return trimmed
    }
    val hostAndPath = trimmed.substringAfter("://").substringBefore('#').substringBefore('?')
    val host = hostAndPath.substringBefore('/').removePrefix("www.")
    val segments = hostAndPath.substringAfter('/', "").trim('/').split('/').filter { it.isNotBlank() }
    return when {
        host.equals("github.com", ignoreCase = true) ||
            host.equals("raw.githubusercontent.com", ignoreCase = true) -> {
            if (segments.isEmpty()) {
                host
            } else if (segments.size >= 2) {
                "${segments[0]}/${segments[1]}"
            } else {
                segments[0]
            }
        }
        segments.size >= 2 -> "${segments[0]}/${segments[1]}"
        segments.size == 1 -> segments[0]
        else -> host.ifBlank { trimmed }
    }
}
