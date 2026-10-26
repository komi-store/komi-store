package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp

object AssetOwnership {
    // Each tag is the release its name came from, so names still compare when the patterns miss how the version is written.
    fun isSameApp(
        assetName: String,
        otherAssetName: String,
        assetTag: String? = null,
        otherTag: String? = null,
    ): Boolean {
        val stems = stemsOf(assetName, assetTag)
        return stemsOf(otherAssetName, otherTag).any { it in stems }
    }

    private fun stemsOf(
        assetName: String,
        releaseTag: String?,
    ): Set<String> =
        setOfNotNull(
            AssetVariant.extractBaseStem(assetName).takeIf { it.isNotEmpty() },
            AssetGlob.tagStem(assetName, releaseTag),
        )

    fun narrowToApp(
        assets: List<GithubAsset>,
        anchorAssetName: String?,
        releaseTag: String? = null,
        anchorTag: String? = null,
    ): List<GithubAsset> {
        if (anchorAssetName == null) return assets
        return assets.filter { isSameApp(it.name, anchorAssetName, releaseTag, anchorTag) }.ifEmpty { assets }
    }

    enum class VariantStatus {
        INSTALLED,
        UPDATE,
        OTHER_VERSION,
    }

    fun variantStatus(
        assetName: String,
        releaseTag: String?,
        apps: List<InstalledApp>,
    ): VariantStatus? {
        val app = installedVariantOf(assetName, releaseTag, apps) ?: return null
        return when {
            VersionMath.isExactSameVersion(app.installedVersion, releaseTag) -> VariantStatus.INSTALLED
            VersionMath.isVersionNewer(releaseTag, app.installedVersion) -> VariantStatus.UPDATE
            else -> VariantStatus.OTHER_VERSION
        }
    }

    private fun installedVariantOf(
        assetName: String,
        releaseTag: String?,
        apps: List<InstalledApp>,
    ): InstalledApp? {
        val globs = AssetGlob.familyGlobs(assetName, releaseTag)
        return apps.firstOrNull { app ->
            if (app.isPendingInstall) return@firstOrNull false
            if (globs.isEmpty()) {
                app.installedAssetName == assetName
            } else {
                app.installedGlobs().any { it in globs }
            }
        }
    }

    private fun InstalledApp.installedGlobs(): Set<String> =
        installedAssetName
            ?.let { AssetGlob.familyGlobs(it, installedVersion) }
            ?.takeIf { it.isNotEmpty() }
            ?: setOfNotNull(assetGlobPattern)

    fun ownerOf(
        assetName: String,
        apps: List<InstalledApp>,
        releaseAssets: List<GithubAsset>,
        releaseHistory: List<GithubRelease>,
        releaseTag: String? = null,
    ): InstalledApp? {
        if (apps.isEmpty()) return null

        val candidates = apps.filter { canOwn(it, assetName) }
        val owners =
            ownersByFilter(assetName, apps)
                .ifEmpty { ownersByGlob(assetName, releaseTag, candidates) }
                .ifEmpty { ownersByStem(assetName, releaseTag, candidates) }
        return if (owners.isNotEmpty()) {
            owners.closestVersionLine(assetName)
        } else {
            renamedOwner(assetName, apps, releaseAssets, releaseHistory, releaseTag)
        }
    }

    private fun ownersByFilter(
        assetName: String,
        apps: List<InstalledApp>,
    ): List<InstalledApp> =
        apps.filter { app ->
            AssetFilter.parse(app.assetFilterRegex)?.getOrNull()?.matches(assetName) == true
        }

    private fun ownersByGlob(
        assetName: String,
        releaseTag: String?,
        candidates: List<InstalledApp>,
    ): List<InstalledApp> {
        val globs = AssetGlob.familyGlobs(assetName, releaseTag)
        return candidates.filter { app -> app.installedGlobs().any { it in globs } }
    }

    private fun ownersByStem(
        assetName: String,
        releaseTag: String?,
        candidates: List<InstalledApp>,
    ): List<InstalledApp> =
        candidates.filter { app ->
            app.installedAssetName?.let { isSameApp(it, assetName, app.installedVersion, releaseTag) } == true
        }

    private fun renamedOwner(
        assetName: String,
        apps: List<InstalledApp>,
        releaseAssets: List<GithubAsset>,
        releaseHistory: List<GithubRelease>,
        releaseTag: String?,
    ): InstalledApp? {
        val renamed =
            apps.singleOrNull { app ->
                canOwn(app, assetName) &&
                    app.installedAssetName?.let { own ->
                        releaseAssets.none { isSameApp(it.name, own, releaseTag, app.installedVersion) }
                    } != false
            } ?: return null
        val renamedFrom = renamed.installedAssetName ?: return renamed.takeIf { apps.size == 1 }
        return renamed.takeIf { isRename(renamedFrom, assetName, releaseHistory) }
    }

    // Newest first: a rename never goes back, though apps released side by side (K-9 / Thunderbird) interleave.
    private fun isRename(
        fromAssetName: String,
        toAssetName: String,
        releaseHistory: List<GithubRelease>,
    ): Boolean {
        // By name alone: release-aware matching would join the names a rename deliberately split.
        val newestWithOld =
            releaseHistory.indexOfFirst { release ->
                release.assets.any { isSameApp(it.name, fromAssetName) }
            }
        if (newestWithOld < 0) return true
        val oldestWithNew =
            releaseHistory.indexOfLast { release ->
                release.assets.any { isSameApp(it.name, toAssetName) }
            }
        return when {
            oldestWithNew > newestWithOld -> {
                false
            }

            oldestWithNew < 0 -> {
                true
            }

            // A monorepo's new app also starts after the installed one's last release; its tags still tell them apart.
            else -> {
                ReleaseLines.of(releaseHistory[oldestWithNew].tagName) ==
                    ReleaseLines.of(releaseHistory[newestWithOld].tagName)
            }
        }
    }

    // A package naming its major version (org.godotengine.editor.v4) can't take another major's APK; that one installs beside it.
    fun canOwn(
        app: InstalledApp,
        assetName: String,
    ): Boolean {
        val packageMajor =
            app.packageName.split('.').firstNotNullOfOrNull { segment ->
                PACKAGE_MAJOR
                    .matchEntire(segment)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            } ?: return true
        val assetMajor = AssetGlob.versionMajor(assetName) ?: return true
        return packageMajor == assetMajor
    }

    private val PACKAGE_MAJOR = Regex("""v(\d+)""", RegexOption.IGNORE_CASE)

    // Apps named alike except the version (Godot editor.v3 / .v4) tie on glob and stem.
    private fun List<InstalledApp>.closestVersionLine(assetName: String): InstalledApp {
        if (size == 1) return first()
        val major = AssetGlob.versionMajor(assetName) ?: return first()
        return firstOrNull { it.versionLineMajor() == major } ?: first()
    }

    private fun InstalledApp.versionLineMajor(): Int? =
        installedAssetName?.let(AssetGlob::versionMajor)
            ?: AssetGlob.versionMajor(installedVersion)
}
