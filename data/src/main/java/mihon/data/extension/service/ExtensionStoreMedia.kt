package mihon.data.extension.service

/**
 * Wire schema family used to decode an extension store index. Anime repos published for
 * Aniyomi encode Extension.isTorrent at proto 8 and sources at proto 9, while mihon-style
 * manga indexes (and novel, which is JSON-only in practice) have no proto 8 and sources at 8.
 */
enum class ExtensionStoreMedia { MANGA, ANIME }
