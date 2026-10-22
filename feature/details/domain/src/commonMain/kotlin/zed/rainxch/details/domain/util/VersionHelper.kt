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
        val candidateScheme = VersionMath.detectScheme(candidate)
        val currentScheme = VersionMath.detectScheme(current)
        val bothSchemed =
            candidateScheme != VersionMath.Scheme.Unknown &&
                currentScheme != VersionMath.Scheme.Unknown

        val cmp = VersionMath.compareVersions(candidate, current)

        if (bothSchemed) {
            return cmp < 0
        }

        if (cmp == 0) return false

        val normalizedCandidate = VersionMath.normalizeVersion(candidate)
        val normalizedCurrent = VersionMath.normalizeVersion(current)
        val candidateIndex =
            allReleases.indexOfFirst {
                VersionMath.normalizeVersion(it.tagName) == normalizedCandidate
            }
        val currentIndex =
            allReleases.indexOfFirst {
                VersionMath.normalizeVersion(it.tagName) == normalizedCurrent
            }
        if (candidateIndex != -1 && currentIndex != -1) {
            return candidateIndex > currentIndex
        }
        return false
    }
}
