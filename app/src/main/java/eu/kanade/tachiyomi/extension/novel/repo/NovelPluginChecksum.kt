package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.util.lang.Hash

object NovelPluginChecksum {
    fun verifySha256(expectedHex: String, bytes: ByteArray): Boolean {
        val expected = expectedHex.lowercase()
        val actual = Hash.sha256(bytes)
        return expected == actual
    }
}
