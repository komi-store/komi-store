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

        val candidates = apps.filter { canOwn(it, assetName) }

        val glob = AssetVariant.deriveGlob(assetName)
        if (glob != null) {
            val byGlob = candidates.filter { app ->
                val appGlob =
                    app.installedAssetName?.let(AssetVariant::deriveGlob) ?: app.assetGlobPattern
                appGlob == glob
            }
            if (byGlob.isNotEmpty()) return byGlob.closestVersionLine(assetName)
        }

        val byStem = candidates.filter { app ->
            app.installedAssetName?.let { isSameApp(it, assetName) } == true
        }
        if (byStem.isNotEmpty()) return byStem.closestVersionLine(assetName)

        val missing = apps.filter { app ->
            canOwn(app, assetName) &&
                app.installedAssetName?.let { own -> releaseAssets.none { isSameApp(it.name, own) } } != false
        }
        val renamed = missing.singleOrNull() ?: return null
        val renamedFrom = renamed.installedAssetName ?: return renamed.takeIf { apps.size == 1 }
        return renamed.takeIf { isRename(renamedFrom, assetName, releaseHistory) }
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
        if (oldestWithNew > newestWithOld) return false
        if (oldestWithNew < 0) return true
        // A monorepo's brand-new app also starts after the installed one's last release;
        // its tags (locker-v1.0.0 vs auth-v4.4.25) still say it is another app.
        return ReleaseLines.of(releaseHistory[oldestWithNew].tagName) ==
            ReleaseLines.of(releaseHistory[newestWithOld].tagName)
    }

    // A package that names its major version (org.godotengine.editor.v4) can't be updated by
    // another major's APK; that APK is a different app installed side by side.
    fun canOwn(app: InstalledApp, assetName: String): Boolean {
        val packageMajor = app.packageName.split('.').firstNotNullOfOrNull { segment ->
            PACKAGE_MAJOR.matchEntire(segment)?.groupValues?.get(1)?.toIntOrNull()
        } ?: return true
        val assetMajor = AssetVariant.versionMajor(assetName) ?: return true
        return packageMajor == assetMajor
    }

    private val PACKAGE_MAJOR = Regex("""v(\d+)""", RegexOption.IGNORE_CASE)

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
