package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page

internal fun List<Page>.dedupeByStableIdentity(): List<Page> {
    if (isEmpty()) return this

    val seen = linkedSetOf<String>()
    return buildList(size) {
        for (page in this@dedupeByStableIdentity) {
            if (seen.add(page.stableIdentityKey())) {
                add(page)
            }
        }
    }
}

internal fun Page.stableIdentityKey(): String {
    val pageUrl = url.trim().takeIf { it.isNotEmpty() }
    if (pageUrl != null) {
        return "url:$pageUrl"
    }

    val pageImageUrl = imageUrl?.trim().takeIf { !it.isNullOrEmpty() }
    if (pageImageUrl != null) {
        return "image:$pageImageUrl"
    }

    return "index:$index"
}
