package eu.kanade.tachiyomi.extension.novel

import java.nio.ByteBuffer
import java.security.MessageDigest

object NovelPluginId {
    fun toSourceId(pluginId: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(pluginId.toByteArray())
        val value = ByteBuffer.wrap(digest).long
        return value and Long.MAX_VALUE
    }
}
