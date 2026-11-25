package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.download.AssetSourceGoneException
import zed.rainxch.core.data.download.AssetSourceRefetcher
import zed.rainxch.core.data.download.catchingStepFailures
import zed.rainxch.core.data.network.GithubAssetAuth
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.MultiSourceDownloader
import zed.rainxch.core.domain.utils.AssetFileName
import zed.rainxch.core.domain.utils.ResolvedRelease

class AssetFetcher(
    private val downloader: Downloader,
    private val multiSourceDownloader: MultiSourceDownloader,
    private val assetSourceRefetcher: AssetSourceRefetcher,
    private val digestVerifier: DigestVerifier,
    private val tokenStore: TokenStore,
    private val installedAppsRepository: InstalledAppsRepository,
) {
    data class Fetched(
        val spec: DownloadSpec,
        val filePath: String,
    )

    // The verified file and the spec it came from, or null once the entry is marked failed.
    suspend fun fetch(
        spec: DownloadSpec,
        entries: DownloadEntries,
    ): Fetched? = FetchRun(spec, entries).fetch()

    private inner class FetchRun(
        private val spec: DownloadSpec,
        private val entries: DownloadEntries,
    ) {
        private val scopedName =
            AssetFileName.scoped(
                owner = spec.repoOwner,
                repo = spec.repoName,
                originalName = spec.asset.name,
            )

        suspend fun fetch(): Fetched? {
            entries.update(spec.packageName) {
                it.copy(
                    stage = DownloadStage.Downloading,
                    bytesDownloaded = 0L,
                    totalBytes = spec.asset.size.takeIf { size -> size > 0 },
                )
            }
            val fetched = downloadWithFallbacks()
            val filePath = downloadedFile("Downloaded file missing: $scopedName")
            entries.update(spec.packageName) {
                it.copy(filePath = filePath, progressPercent = 100)
            }
            return verified(fetched, filePath)?.let { Fetched(fetched, it) }
        }

        private suspend fun downloadWithFallbacks(): DownloadSpec {
            val failure =
                catchingStepFailures {
                    stream(
                        multiSourceDownloader.download(
                            spec.asset.downloadUrl,
                            scopedName,
                            identity = spec.identity(),
                        ),
                    )
                }.exceptionOrNull() ?: return spec
            if (failure is AssetSourceGoneException) return retryMovedAsset(failure)
            retryViaAuthenticatedAssetApi(failure, spec)
            return spec
        }

        private suspend fun retryMovedAsset(gone: AssetSourceGoneException): DownloadSpec {
            // A rebuilt asset keeps its name; a renamed one only shows up once the tracked release resolves again.
            val refetched = assetSourceRefetcher.refetch(spec) ?: resolveTrackedReplacement(spec)
            if (refetched == null) {
                retryViaAuthenticatedAssetApi(gone, spec)
                return spec
            }
            Logger.w(gone) {
                "Orchestrator: ${spec.asset.name} is no longer at ${spec.releaseTag}; " +
                    "the repository now offers it at ${refetched.releaseTag}, retrying"
            }
            entries.update(spec.packageName) {
                it.copy(
                    bytesDownloaded = 0L,
                    progressPercent = 0,
                    downloadUrl = refetched.asset.downloadUrl,
                    assetSize = refetched.asset.size,
                    assetId = refetched.asset.id,
                    assetDigest = refetched.asset.digest,
                    releaseTag = refetched.releaseTag,
                    releaseId = refetched.releaseId,
                )
            }
            catchingStepFailures {
                stream(
                    multiSourceDownloader.download(
                        refetched.asset.downloadUrl,
                        scopedName,
                        identity = refetched.identity(),
                    ),
                )
            }.onFailure { retryViaAuthenticatedAssetApi(it, refetched) }
            return refetched
        }

        private suspend fun retryViaAuthenticatedAssetApi(
            failure: Throwable,
            failedSpec: DownloadSpec,
        ) {
            val apiUrl = authenticatedGithubAssetUrl(failedSpec) ?: throw failure
            Logger.w(failure) {
                "Orchestrator: primary download failed for ${failedSpec.asset.name}, " +
                    "retrying via authenticated GitHub asset API"
            }
            entries.update(failedSpec.packageName) {
                it.copy(bytesDownloaded = 0L, progressPercent = 0)
            }
            stream(
                downloader.download(apiUrl, scopedName, bypassMirror = true, identity = failedSpec.identity()),
            )
        }

        // The path once its digest checks out or there is none to check, or null once the entry is marked failed.
        private suspend fun verified(
            fetched: DownloadSpec,
            filePath: String,
        ): String? {
            val expectedDigest = fetched.asset.digest
            val mismatch = expectedDigest?.let { digestVerifier.verify(filePath, it) }
            return when {
                expectedDigest == null -> {
                    Logger.i { "No digest for ${fetched.asset.name}, skipping SHA-256 verification" }
                    filePath
                }

                mismatch == null -> {
                    filePath
                }

                else -> {
                    Logger.w {
                        "Orchestrator: digest mismatch for ${spec.asset.name} ($mismatch); " +
                            "re-fetching from the source"
                    }
                    refetchFromSource(fetched, filePath, expectedDigest)
                }
            }
        }

        private suspend fun refetchFromSource(
            fetched: DownloadSpec,
            mismatchedPath: String,
            expectedDigest: String,
        ): String? {
            java.io.File(mismatchedPath).delete()
            catchingStepFailures { downloader.discardPartial(scopedName) }
                .onFailure { Logger.w(it) { "Orchestrator: could not discard the partial for $scopedName" } }
            entries.update(spec.packageName) {
                it.copy(progressPercent = 0, bytesDownloaded = 0L)
            }
            val sourceUrl = authenticatedGithubAssetUrl(fetched) ?: fetched.asset.downloadUrl
            val failure =
                catchingStepFailures {
                    stream(
                        downloader.download(
                            sourceUrl,
                            scopedName,
                            bypassMirror = true,
                            identity = fetched.identity(),
                        ),
                    )
                }.exceptionOrNull() ?: return acceptRefetched(expectedDigest)
            Logger.e(failure) { "Orchestrator: source re-fetch failed for ${spec.asset.name}" }
            entries.markFailed(spec.packageName, failure.message)
            return null
        }

        private suspend fun acceptRefetched(expectedDigest: String): String? {
            val filePath = downloadedFile("Downloaded file missing after re-fetch: $scopedName")
            val stillMismatched = digestVerifier.verify(filePath, expectedDigest)
            if (stillMismatched == null) {
                entries.update(spec.packageName) {
                    it.copy(filePath = filePath, progressPercent = 100)
                }
                return filePath
            }
            Logger.w {
                "Orchestrator: digest still mismatched after the source re-fetch for " +
                    "${spec.asset.name}: $stillMismatched"
            }
            java.io.File(filePath).delete()
            entries.markFailed(
                spec.packageName,
                "Checksum mismatch: file may have been tampered with",
            )
            return null
        }

        private suspend fun downloadedFile(missingMessage: String): String =
            downloader.getDownloadedFilePath(scopedName) ?: error(missingMessage)

        private suspend fun stream(flow: Flow<DownloadProgress>) {
            flow.collect { progress ->
                entries.update(spec.packageName) {
                    it.copy(
                        progressPercent = progress.percent,
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes ?: it.totalBytes,
                    )
                }
            }
        }
    }

    private suspend fun authenticatedGithubAssetUrl(spec: DownloadSpec): String? {
        if (spec.asset.id <= 0L) return null
        if (!GithubAssetAuth.isGithubHost(spec.asset.downloadUrl)) return null
        val signedIn =
            try {
                tokenStore
                    .currentToken()
                    ?.accessToken
                    ?.trim()
                    .isNullOrEmpty()
                    .not()
            } catch (e: LocalStoreException) {
                Logger.w(e) { "Orchestrator: could not read the GitHub token for ${spec.asset.name}" }
                false
            }
        if (!signedIn) return null
        return GithubAssetAuth.assetApiUrl(spec.repoOwner, spec.repoName, spec.asset.id)
    }

    private suspend fun resolveTrackedReplacement(spec: DownloadSpec): DownloadSpec? =
        catchingStepFailures { installedAppsRepository.resolveTrackedRelease(spec.packageName) }
            .fold(
                onSuccess = { resolved -> trackedReplacementFor(spec, resolved) },
                onFailure = { e ->
                    Logger.w(e) { "Orchestrator: tracked-release fallback failed for ${spec.packageName}" }
                    null
                },
            )

    // Only a replacement inside the same release: the download may be a version picked in Details.
    private fun trackedReplacementFor(
        spec: DownloadSpec,
        resolved: ResolvedRelease?,
    ): DownloadSpec? =
        when {
            resolved == null -> {
                Logger.w {
                    "Orchestrator: ${spec.packageName} has no tracked release after " +
                        "${spec.asset.name} went 404"
                }
                null
            }

            resolved.release.tagName != spec.releaseTag -> {
                Logger.w {
                    "Orchestrator: ${spec.packageName} went 404 on ${spec.releaseTag}, but it " +
                        "tracks ${resolved.release.tagName}; not swapping releases"
                }
                null
            }

            resolved.primaryAsset.downloadUrl == spec.asset.downloadUrl -> {
                null
            }

            else -> {
                Logger.i {
                    "Orchestrator: ${spec.packageName} went 404 on ${spec.asset.name}; " +
                        "retrying with ${resolved.release.tagName}/${resolved.primaryAsset.name}"
                }
                spec.copy(
                    asset = resolved.primaryAsset,
                    releaseTag = resolved.release.tagName,
                    releaseId = resolved.release.id,
                )
            }
        }

    private fun DownloadSpec.identity(): AssetIdentity =
        AssetIdentity(
            assetId = asset.id,
            digest = asset.digest,
            size = asset.size,
        )
}
