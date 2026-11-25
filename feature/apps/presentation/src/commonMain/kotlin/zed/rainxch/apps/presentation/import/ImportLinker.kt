package zed.rainxch.apps.presentation.import

import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate

// Tracks a candidate under a repo and marks its external link; false when the repo or the tracked row couldn't be made.
internal class ImportLinker(
    private val appsRepository: AppsRepository,
    private val externalImportRepository: ExternalImportRepository,
    private val logger: KomiStoreLogger,
) {
    suspend fun link(
        candidate: ExternalAppCandidate,
        target: LinkTarget,
    ): Boolean {
        val repoInfo = repoInfo(candidate, target) ?: return false
        val tracked = track(candidate, repoInfo, target)
        if (tracked) markLinked(candidate, target)
        return tracked
    }

    private suspend fun repoInfo(
        candidate: ExternalAppCandidate,
        target: LinkTarget,
    ): GithubRepoInfo? {
        val repoInfo =
            try {
                appsRepository.fetchRepoInfo(target.owner, target.repo, target.sourceHost)
            } catch (e: RateLimitException) {
                logger.error("fetchRepoInfo(${target.owner}/${target.repo}) failed: ${e.message}")
                null
            }
        if (repoInfo == null) {
            logger.warn("Skipping link for ${candidate.packageName}: repo ${target.owner}/${target.repo} not found")
        }
        return repoInfo
    }

    private suspend fun track(
        candidate: ExternalAppCandidate,
        repoInfo: GithubRepoInfo,
        target: LinkTarget,
    ): Boolean =
        try {
            appsRepository.linkAppToRepo(candidate.toDeviceApp(), repoInfo, sourceHost = target.sourceHost)
            true
        } catch (e: LocalStoreException) {
            logger.error("linkAppToRepo failed for ${candidate.packageName}: ${e.message}")
            false
        }

    private suspend fun markLinked(
        candidate: ExternalAppCandidate,
        target: LinkTarget,
    ) {
        externalImportRepository
            .linkManually(
                packageName = candidate.packageName,
                owner = target.owner,
                repo = target.repo,
                source = target.source,
            ).onFailure { logger.error("external_links upsert failed for ${candidate.packageName}: ${it.message}") }
    }

    data class LinkTarget(
        val owner: String,
        val repo: String,
        val source: String,
        val sourceHost: String?,
    )

    private fun ExternalAppCandidate.toDeviceApp(): DeviceApp =
        DeviceApp(
            packageName = packageName,
            appName = appLabel,
            versionName = versionName,
            versionCode = versionCode,
            signingFingerprint = signingFingerprint,
        )
}
