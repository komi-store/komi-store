package zed.rainxch.core.domain.repository.installedapps

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.smartdetect.MatchingPreview
import zed.rainxch.core.domain.utils.ResolvedRelease

interface InstalledAppUpdateChecks {
    suspend fun checkForUpdates(packageName: String): Boolean

    // checkForUpdates on a release list the caller already read; a list without this app's release changes nothing.
    suspend fun checkForUpdatesWithReleases(
        packageName: String,
        releases: List<GithubRelease>,
    ): Boolean

    suspend fun resolveTrackedRelease(packageName: String): ResolvedRelease?

    suspend fun checkAllForUpdates()

    suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview
}
