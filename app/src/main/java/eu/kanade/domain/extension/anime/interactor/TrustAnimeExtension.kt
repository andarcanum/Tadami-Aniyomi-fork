package eu.kanade.domain.extension.anime.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import tachiyomi.core.common.preference.getAndSet

class TrustAnimeExtension(
    private val animeExtensionStoreRepository: AnimeExtensionStoreRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun getTrustedFingerprints(): Set<String> {
        return animeExtensionStoreRepository.getAll()
            .asSequence()
            .map { it.signingKey.trim().lowercase() }
            .filter { it.isNotEmpty() && it != NO_SIGNING_KEY && !it.startsWith(PLACEHOLDER_FINGERPRINT_PREFIX) }
            .toHashSet()
    }

    fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>, trustedFingerprints: Set<String>): Boolean {
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        return fingerprints.any { it.trim().lowercase() in trustedFingerprints } ||
            key in preferences.trustedExtensions().get()
    }

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        return isTrusted(pkgInfo, fingerprints, getTrustedFingerprints())
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }

    private companion object {
        const val NO_SIGNING_KEY = "no_signing_key"
        const val PLACEHOLDER_FINGERPRINT_PREFIX = "nofingerprint-"
    }
}
