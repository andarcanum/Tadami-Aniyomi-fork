package tachiyomi.domain.release.interactor

object AppUpdateVersionComparator {

    fun isUpdateAvailable(
        isPreview: Boolean,
        installedCommitCount: Int,
        installedVersionName: String,
        availableVersionTag: String,
    ): Boolean {
        val availableVersion = availableVersionTag.replace("[^\\d.]".toRegex(), "")
        return if (isPreview) {
            availableVersion.toInt() > installedCommitCount
        } else {
            val installedVersion = installedVersionName.replace("[^\\d.]".toRegex(), "")

            val availableSemVer = availableVersion.split(".").map { it.toInt() }
            val installedSemVer = installedVersion.split(".").map { it.toInt() }

            installedSemVer.mapIndexed { index, versionPart ->
                if (availableSemVer[index] > versionPart) {
                    return true
                }
            }

            false
        }
    }

    fun hasInstalledOrNewer(
        isPreview: Boolean,
        installedCommitCount: Int,
        installedVersionName: String,
        targetVersionTag: String,
    ): Boolean {
        return !isUpdateAvailable(
            isPreview = isPreview,
            installedCommitCount = installedCommitCount,
            installedVersionName = installedVersionName,
            availableVersionTag = targetVersionTag,
        )
    }
}
