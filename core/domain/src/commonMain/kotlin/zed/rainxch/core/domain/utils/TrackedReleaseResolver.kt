package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.system.Installer

// One resolution for the update check and the library download, so a download fetches the asset the check described.
data class ResolvedRelease(
    val release: GithubRelease,
    val primaryAsset: GithubAsset,
    val variantWasLost: Boolean,
)

data class VariantPin(
    val variant: String? = null,
    val tokens: Set<String> = emptySet(),
    val glob: String? = null,
    val pickedIndex: Int? = null,
    val pickedSiblingCount: Int? = null,
) {
    val isSet: Boolean
        get() = variant != null || tokens.isNotEmpty() || !glob.isNullOrBlank()
}

data class ReleaseTracking(
    val packageName: String,
    val installedAssetName: String?,
    val filter: AssetFilter?,
    val fallbackToOlderReleases: Boolean,
    val pin: VariantPin,
)

object TrackedReleaseResolver {
    fun resolve(
        releases: List<GithubRelease>,
        tracking: ReleaseTracking,
        repoApps: List<InstalledApp>,
        installer: Installer,
    ): ResolvedRelease? {
        if (releases.isEmpty()) return null

        val candidates =
            if (tracking.filter != null && !tracking.fallbackToOlderReleases) {
                releases.take(1)
            } else {
                releases
            }
        val resolution = Resolution(releases, tracking, repoApps, installer)
        return candidates.firstNotNullOfOrNull(resolution::resolveIn)
    }

    private class Resolution(
        private val releases: List<GithubRelease>,
        private val tracking: ReleaseTracking,
        private val repoApps: List<InstalledApp>,
        private val installer: Installer,
    ) {
        private val self = repoApps.firstOrNull { it.packageName == tracking.packageName }

        fun resolveIn(release: GithubRelease): ResolvedRelease? {
            val filter = tracking.filter
            val installableForPlatform =
                release.assets.filter { installer.isAssetInstallable(it.name) }
            val installableForApp =
                installableForPlatform
                    .filter { filter == null || filter.matches(it.name) }
                    .filterNot { belongsElsewhere(it, release.tagName, installableForPlatform) }
            return installableForApp.takeIf { it.isNotEmpty() }?.let { pick(release, it) }
        }

        // An APK no installed app owns is usually a sibling app never installed (monorepos); an app with nothing to compare can't tell.
        private fun belongsElsewhere(
            asset: GithubAsset,
            releaseTag: String,
            releaseAssets: List<GithubAsset>,
        ): Boolean {
            val app = self ?: return false
            if (!AssetOwnership.canOwn(app, asset.name)) return true
            val owner =
                AssetOwnership.ownerOf(asset.name, repoApps, releaseAssets, releases, releaseTag)
                    ?: return app.installedAssetName != null || !app.assetGlobPattern.isNullOrBlank()
            return owner.packageName != tracking.packageName
        }

        private fun pick(
            release: GithubRelease,
            installableForApp: List<GithubAsset>,
        ): ResolvedRelease? {
            val pin = tracking.pin
            val sameApp =
                AssetOwnership.narrowToApp(installableForApp, tracking.installedAssetName, release.tagName, self?.installedVersion)
            val fingerprintMatch =
                AssetVariant.resolvePreferredAsset(
                    assets = sameApp,
                    pinnedVariant = pin.variant,
                    pinnedTokens = pin.tokens.takeIf { it.isNotEmpty() },
                    pinnedGlob = pin.glob,
                    releaseTag = release.tagName,
                )

            val positionMatch =
                if (fingerprintMatch == null && pin.isSet && sameApp.size == installableForApp.size) {
                    AssetVariant.resolveBySamePosition(
                        assets = installableForApp,
                        originalIndex = pin.pickedIndex,
                        siblingCountAtPickTime = pin.pickedSiblingCount,
                    )
                } else {
                    null
                }

            val primary =
                fingerprintMatch
                    ?: positionMatch
                    ?: installer.choosePrimaryAsset(autoPickPool(release, installableForApp))
                    ?: return null

            val variantWasLost = pin.isSet && fingerprintMatch == null && positionMatch == null
            return ResolvedRelease(release, primary, variantWasLost)
        }

        private fun autoPickPool(
            release: GithubRelease,
            installableForApp: List<GithubAsset>,
        ): List<GithubAsset> =
            AssetOwnership.narrowToApp(
                AssetVariant.filterByPackageFlavor(installableForApp, tracking.packageName),
                tracking.installedAssetName,
                release.tagName,
                self?.installedVersion,
            )
    }
}
