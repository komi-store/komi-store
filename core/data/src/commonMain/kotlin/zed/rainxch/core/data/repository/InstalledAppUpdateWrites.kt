package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VersionMath

// The one write for a single-app update check, shared by the library's window check and the
// Details refresh. A rejected compare-and-set means another writer advanced the revision while
// this verdict was being formed, so the caller decides whether to re-read the row and judge again.
@Suppress("LongParameterList")
internal suspend fun writeCheckedRelease(
    dao: InstalledAppDao,
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    verdict: UpdateVerdict.Result,
    // Null keeps the stored staleness flag: a forced selection names its own pair, so it has
    // nothing to say about a preferred variant that is missing from the release.
    variantWasLost: Boolean?,
): Boolean? {
    if (verdict.skipBecameStale) {
        dao.setSkippedReleaseTag(app.packageName, null)
    }

    val isUpdateAvailable = verdict.isUpdateAvailable
    val resolvedLatestVersionCode =
        if (matchedRelease.tagName == app.latestVersion) app.latestVersionCode else null

    val expectedRevision = app.updateStateRevision
    val written =
        dao.updateVersionInfo(
            packageName = app.packageName,
            available = isUpdateAvailable,
            version = matchedRelease.tagName,
            assetName = matchedAsset.name,
            assetUrl = matchedAsset.downloadUrl,
            assetSize = matchedAsset.size,
            releaseId = matchedRelease.id,
            assetId = matchedAsset.id,
            assetDigest = matchedAsset.digest,
            releaseNotes = matchedRelease.description ?: "",
            timestamp = System.currentTimeMillis(),
            latestVersionName = matchedRelease.tagName,
            latestVersionCode = resolvedLatestVersionCode,
            latestReleasePublishedAt = matchedRelease.publishedAt,
            expectedRevision = expectedRevision,
            nextRevision = expectedRevision + 1,
        )
    if (written == 0) {
        Logger.w {
            "Discarded a stale write for ${app.appName}: stored revision moved past $expectedRevision"
        }
        return null
    }

    // Adoption may rewrite the installed tag only when the stored identity does not contradict
    // the release it is being relabelled with.
    val shouldRewriteTag =
        UpdateVerdict.shouldAdoptMatchedTag(
            codesAlreadyMatch = verdict.codesAlreadyMatch,
            installedTag = app.installedVersion,
            matchedTag = matchedRelease.tagName,
            deviceRunsMatchedRelease = verdict.deviceRunsMatchedRelease,
            identityContradicts = identityContradicts(app, matchedAsset),
        )
    if (shouldRewriteTag) {
        adoptTagWhenProven(
            dao = dao,
            app = app,
            matchedRelease = matchedRelease,
            matchedAsset = matchedAsset,
            isUpdateAvailable = isUpdateAvailable,
            boundAt = System.currentTimeMillis(),
        )
    }

    if (variantWasLost != null && variantWasLost != app.preferredVariantStale) {
        dao.updateVariantStaleness(app.packageName, variantWasLost)
    }

    return isUpdateAvailable
}

// A refresh may not read the installed file, so the stored identity is all it can compare with.
internal fun storedInstalledBinding(app: InstalledAppEntity): UpdateVerdict.Bound? {
    if (app.installedAssetId == null && app.installedAssetDigest == null) return null
    return UpdateVerdict.Bound(
        assetId = app.installedAssetId,
        assetDigest = app.installedAssetDigest,
        releasePublishedAt = app.installedReleasePublishedAt,
    )
}

// A generic check can read an older cached window. The row already naming a later release means
// the fresher result must survive, so nothing from this window may land. An equal timestamp still
// writes: a build can be replaced within the precision the forge reports, and release identity is
// then the only difference.
internal fun windowWouldRegressStoredRelease(
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
): Boolean {
    val regresses =
        app.latestReleaseId != matchedRelease.id &&
            VersionMath.publishedAtAfter(app.latestReleasePublishedAt, matchedRelease.publishedAt)
    if (regresses) {
        Logger.w {
            "Keeping the stored release for ${app.packageName}: ${app.latestVersion} " +
                "(${app.latestReleasePublishedAt}) is newer than the window's " +
                "${matchedRelease.tagName} (${matchedRelease.publishedAt})"
        }
    }
    return regresses
}

// The row fields and the resolved pair the verdict reads. Both the library window check and the
// Details refresh map through here, so a new input cannot reach only one of the two paths.
@Suppress("LongParameterList")
internal fun decideUpdateVerdict(
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    bound: UpdateVerdict.Bound?,
    selfAttestedDigest: String?,
    canReadInstalledFile: Boolean,
): UpdateVerdict.Result =
    UpdateVerdict.decide(
        installed =
            UpdateVerdict.Installed(
                tag = app.installedVersion,
                versionCode = app.installedVersionCode,
                versionName = app.installedVersionName,
                publishedAt = app.installedReleasePublishedAt,
                releaseId = app.installedReleaseId,
                assetId = app.installedAssetId,
                assetName = app.installedAssetName,
            ),
        stored =
            UpdateVerdict.Stored(
                latestTag = app.latestVersion,
                latestVersionCode = app.latestVersionCode,
                publishedAt = app.latestReleasePublishedAt,
                wasUpdateAvailable = app.isUpdateAvailable,
                latestReleaseId = app.latestReleaseId,
                latestAssetId = app.latestAssetId,
                latestAssetDigest = app.latestAssetDigest,
                latestAssetSize = app.latestAssetSize,
            ),
        matched =
            UpdateVerdict.Matched(
                tag = matchedRelease.tagName,
                publishedAt = matchedRelease.publishedAt,
                isPrerelease = matchedRelease.isPrerelease,
                releaseId = matchedRelease.id,
                assetId = matchedAsset.id,
                assetDigest = matchedAsset.digest,
                assetSize = matchedAsset.size,
                assetName = matchedAsset.name,
            ),
        skippedTag = app.skippedReleaseTag,
        bound = bound,
        selfAttestedDigest = selfAttestedDigest,
        canReadInstalledFile = canReadInstalledFile,
        detectAssetRebuildsInRelease = app.detectAssetRebuildsInRelease,
    )
