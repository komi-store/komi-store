package zed.rainxch.details.domain.util

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.utils.VersionMath

object VersionHelper {
    fun normalizeVersion(version: String?): String = VersionMath.normalizeVersion(version)

    fun isDowngradeVersion(
        candidate: String,
        current: String,
        allReleases: List<GithubRelease>,
    ): Boolean {
        val bothSchemed =
            VersionMath.detectScheme(candidate) != VersionMath.Scheme.Unknown &&
                VersionMath.detectScheme(current) != VersionMath.Scheme.Unknown
        val cmp = VersionMath.compareVersions(candidate, current)
        return when {
            bothSchemed -> cmp < 0
            cmp == 0 -> false
            else -> comesAfterInReleaseList(candidate, current, allReleases)
        }
    }

    // Without a version scheme to compare, the release list's newest-first order decides.
    private fun comesAfterInReleaseList(
        candidate: String,
        current: String,
        allReleases: List<GithubRelease>,
    ): Boolean {
        val candidateIndex = allReleases.indexOfVersion(candidate)
        val currentIndex = allReleases.indexOfVersion(current)
        return candidateIndex != -1 && currentIndex != -1 && candidateIndex > currentIndex
    }

    private fun List<GithubRelease>.indexOfVersion(version: String): Int {
        val normalized = VersionMath.normalizeVersion(version)
        return indexOfFirst { VersionMath.normalizeVersion(it.tagName) == normalized }
    }
}
