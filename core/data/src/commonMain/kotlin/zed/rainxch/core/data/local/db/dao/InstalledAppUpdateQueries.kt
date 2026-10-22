package zed.rainxch.core.data.local.db.dao

import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.utils.VersionMath

// Small DAO write helpers used by the repository, kept here so the repository file stays
// within the architecture line limit.
internal suspend fun InstalledAppDao.recordUnmatchedRelease(packageName: String) {
    clearUpdateFlagKeepBaseline(packageName, System.currentTimeMillis())
}

internal suspend fun InstalledAppDao.adoptMatchedTag(
    app: InstalledAppEntity,
    matchedTag: String,
    isUpdateAvailable: Boolean,
) {
    updateInstalledVersion(
        packageName = app.packageName,
        installedVersion = matchedTag,
        installedVersionName = app.installedVersionName,
        installedVersionCode = app.installedVersionCode,
        isUpdateAvailable = isUpdateAvailable,
    )
}

// The window is the fresher record; the stored value only covers a release the window no
// longer carries (deleted upstream, or rotated out by newer releases). Backfill on a hit.
internal suspend fun InstalledAppDao.resolveInstalledReleasePublishedAt(
    app: InstalledAppEntity,
    window: List<GithubRelease>,
): String? {
    val fromWindow = app.installedReleaseId?.let { id -> window.firstOrNull { it.id == id }?.publishedAt }
    if (fromWindow != null && fromWindow != app.installedReleasePublishedAt) {
        setInstalledReleasePublishedAt(app.packageName, fromWindow)
    }
    return fromWindow ?: VersionMath.publishedAtOrNull(app.installedReleasePublishedAt)
}
