package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.system.Installer

// One resolution for every entry point that needs "which release does this app track, and
// which asset of it is its file". The update check and the download started from the library
// both go through this, so a download always fetches the asset the check described.
data class ResolvedRelease(
    val release: GithubRelease,
    val primaryAsset: GithubAsset,
    val variantWasLost: Boolean,
)

object TrackedReleaseResolver {

    fun resolve(
        releases: List<GithubRelease>,
        filter: AssetFilter?,
        fallbackToOlderReleases: Boolean,
        preferredVariant: String?,
        preferredTokens: Set<String>,
        preferredGlob: String?,
        pickedIndex: Int?,
        pickedSiblingCount: Int?,
        trackedPackageName: String,
        installedAssetName: String?,
        repoApps: List<InstalledApp>,
        installer: Installer,
    ): ResolvedRelease? {
        if (releases.isEmpty()) return null

        val self = repoApps.firstOrNull { it.packageName == trackedPackageName }

        // An APK no installed app owns is usually a sibling app the user never installed
        // (monorepos). Only an app with no asset name or glob to compare can't tell.
        fun belongsElsewhere(asset: GithubAsset, releaseTag: String, releaseAssets: List<GithubAsset>): Boolean {
            if (self == null) return false
            if (!AssetOwnership.canOwn(self, asset.name)) return true
            val owner = AssetOwnership.ownerOf(asset.name, repoApps, releaseAssets, releases, releaseTag)
                ?: return self.installedAssetName != null || !self.assetGlobPattern.isNullOrBlank()
            return owner.packageName != trackedPackageName
        }

        val candidates =
            if (filter != null && !fallbackToOlderReleases) {
                releases.take(1)
            } else {
                releases
            }

        val hasAnyPin =
            preferredVariant != null ||
                    preferredTokens.isNotEmpty() ||
                    !preferredGlob.isNullOrBlank()

        for (release in candidates) {
            val installableForPlatform =
                release.assets.filter { installer.isAssetInstallable(it.name) }
            val installableForApp =
                (
                    if (filter == null) installableForPlatform
                    else installableForPlatform.filter { filter.matches(it.name) }
                ).filterNot { belongsElsewhere(it, release.tagName, installableForPlatform) }

            if (installableForApp.isEmpty()) continue

            val sameApp =
                AssetOwnership.narrowToApp(installableForApp, installedAssetName, release.tagName, self?.installedVersion)
            val fingerprintMatch =
                AssetVariant.resolvePreferredAsset(
                    assets = sameApp,
                    pinnedVariant = preferredVariant,
                    pinnedTokens = preferredTokens.takeIf { it.isNotEmpty() },
                    pinnedGlob = preferredGlob,
                    releaseTag = release.tagName,
                )

            val positionMatch =
                if (fingerprintMatch == null && hasAnyPin && sameApp.size == installableForApp.size) {
                    AssetVariant.resolveBySamePosition(
                        assets = installableForApp,
                        originalIndex = pickedIndex,
                        siblingCountAtPickTime = pickedSiblingCount,
                    )
                } else {
                    null
                }

            val autoPickPool =
                AssetOwnership.narrowToApp(
                    AssetVariant.filterByPackageFlavor(installableForApp, trackedPackageName),
                    installedAssetName,
                    release.tagName,
                    self?.installedVersion,
                )
            val primary = fingerprintMatch
                ?: positionMatch
                ?: installer.choosePrimaryAsset(autoPickPool)
                ?: continue

            val variantWasLost =
                hasAnyPin && fingerprintMatch == null && positionMatch == null

            return ResolvedRelease(release, primary, variantWasLost)
        }

        return null
    }
}
