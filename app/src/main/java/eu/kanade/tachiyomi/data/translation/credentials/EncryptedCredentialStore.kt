package eu.kanade.tachiyomi.data.translation.credentials

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.credentials.CredentialStoreException
import tachiyomi.domain.translation.credentials.CredentialStoreFailure
import java.io.File

/** No preferences, migration or fallback: a successful put acknowledges atomic encrypted storage. */
internal class EncryptedCredentialStore(
    private val cipher: CredentialCipher,
    private val records: CredentialRecordStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialStore {
    override suspend fun put(id: String, secret: String) {
        CredentialIds.requireValid(id)
        withContext(dispatcher) {
            try {
                records.writeAtomic(id, cipher.encrypt(id, secret))
            } catch (error: CancellationException) {
                throw error
            } catch (error: CredentialStoreException) {
                // Recreate even typed failures so an injected/platform cause cannot escape.
                throw CredentialStoreException(error.kind)
            } catch (_: Exception) {
                throw CredentialStoreException(CredentialStoreFailure.UNAVAILABLE)
            }
        }
    }

    override suspend fun read(id: String): CredentialRead {
        CredentialIds.requireValid(id)
        return withContext(dispatcher) {
            try {
                val record = records.read(id) ?: return@withContext CredentialRead.Missing
                cipher.decrypt(id, record)
            } catch (error: CancellationException) {
                throw error
            } catch (_: CorruptCredentialRecord) {
                CredentialRead.Corrupt
            } catch (_: Exception) {
                CredentialRead.Unavailable
            }
        }
    }

    override suspend fun remove(id: String) {
        CredentialIds.requireValid(id)
        withContext(dispatcher) {
            try {
                records.remove(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                throw CredentialStoreException(CredentialStoreFailure.UNAVAILABLE)
            }
        }
    }
}

/** Isolated primitive only. Callers opt in; this factory never reads or deletes legacy preferences. */
fun createAndroidCredentialStore(context: Context): CredentialStore = EncryptedCredentialStore(
    cipher = AesGcmCredentialCipher(AndroidCredentialKeyLoader()),
    records = FileCredentialRecordStore(File(context.applicationContext.noBackupFilesDir, "translation_credentials")),
)
