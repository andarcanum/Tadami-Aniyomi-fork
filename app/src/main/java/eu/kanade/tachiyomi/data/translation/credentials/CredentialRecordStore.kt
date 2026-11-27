package eu.kanade.tachiyomi.data.translation.credentials

internal interface CredentialRecordStore {
    fun read(id: String): ByteArray?
    fun writeAtomic(id: String, record: ByteArray)
    fun remove(id: String)
}

/** Invalid or oversized encrypted framing, never a platform exception containing payload text. */
internal class CorruptCredentialRecord : IllegalStateException("Credential record is corrupt")
