package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp

object AssetOwnership {

    fun isSameApp(assetName: String, otherAssetName: String): Boolean {
        val stem = AssetVariant.extractBaseStem(assetName)
        return stem.isNotEmpty() && stem == AssetVariant.extractBaseStem(otherAssetName)
    }

    fun narrowToApp(assets: List<GithubAsset>, anchorAssetName: String?): List<GithubAsset> {
        if (anchorAssetName == null) return assets
        return assets.filter { isSameApp(it.name, anchorAssetName) }.ifEmpty { assets }
    }

    fun ownerOf(
        assetName: String,
        apps: List<InstalledApp>,
        releaseAssets: List<GithubAsset>,
        releaseHistory: List<GithubRelease>,
    ): InstalledApp? {
        if (apps.isEmpty()) return null

        val byFilter = apps.filter { app ->
            AssetFilter.parse(app.assetFilterRegex)?.getOrNull()?.matches(assetName) == true
        }
        if (byFilter.isNotEmpty()) return byFilter.closestVersionLine(assetName)

        val glob = AssetVariant.deriveGlob(assetName)
        if (glob != null) {
            val byGlob = apps.filter { app ->
                val appGlob =
                    app.installedAssetName?.let(AssetVariant::deriveGlob) ?: app.assetGlobPattern
                appGlob == glob
            }
            if (byGlob.isNotEmpty()) return byGlob.closestVersionLine(assetName)
        }

        val byStem = apps.filter { app ->
            app.installedAssetName?.let { isSameApp(it, assetName) } == true
        }
        if (byStem.isNotEmpty()) return byStem.closestVersionLine(assetName)

        val sole = apps.singleOrNull() ?: return null
        val soleAsset = sole.installedAssetName ?: return sole
        if (releaseAssets.any { isSameApp(it.name, soleAsset) }) return null
        return sole.takeIf { isRename(soleAsset, assetName, releaseHistory) }
    }

    // Newest first. A rename never goes back: every release with the new name is newer than
    // every release with the old one. Apps released side by side (K-9 / Thunderbird) interleave.
    private fun isRename(
        fromAssetName: String,
        toAssetName: String,
        releaseHistory: List<GithubRelease>,
    ): Boolean {
        val newestWithOld = releaseHistory.indexOfFirst { release ->
            release.assets.any { isSameApp(it.name, fromAssetName) }
        }
        if (newestWithOld < 0) return true
        val oldestWithNew = releaseHistory.indexOfLast { release ->
            release.assets.any { isSameApp(it.name, toAssetName) }
        }
        return oldestWithNew <= newestWithOld
    }

    // Apps named alike except the version (Godot editor.v3 / .v4) tie on glob and stem.
    private fun List<InstalledApp>.closestVersionLine(assetName: String): InstalledApp {
        if (size == 1) return first()
        val major = AssetVariant.versionMajor(assetName) ?: return first()
        return firstOrNull { it.versionLineMajor() == major } ?: first()
    }

    private fun InstalledApp.versionLineMajor(): Int? =
        installedAssetName?.let(AssetVariant::versionMajor)
            ?: AssetVariant.versionMajor(installedVersion)
}
