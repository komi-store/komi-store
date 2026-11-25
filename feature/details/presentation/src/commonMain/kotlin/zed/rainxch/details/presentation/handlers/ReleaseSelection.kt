package zed.rainxch.details.presentation.handlers

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.AssetOwnership
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.ReleaseLines
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.platforms
import zed.rainxch.core.presentation.utils.daysSinceIso
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.RawDetailsState
import zed.rainxch.details.presentation.model.TranslationState

// Which release, asset and installed app the page shows: pure decisions over the state and the installer's asset rules.
internal class ReleaseSelection(
    private val installer: Installer,
) {
    internal class Assets(
        val installable: List<GithubAsset>,
        val primary: GithubAsset?,
    )

    internal class Insights(
        val stalledStableSinceDays: Int?,
        val mergedChangelog: String?,
        val mergedChangelogBaseTag: String?,
        val latestStableHasInstallableAsset: Boolean,
    )

    internal fun assetsFor(
        state: RawDetailsState,
        release: GithubRelease?,
        installedApp: InstalledApp? = state.installedApp,
        anchorAssetName: String? = state.primaryAsset?.name ?: installedApp?.installedAssetName,
    ): Assets {
        val releaseTag = release?.tagName
        val installable = release?.assets?.filter { installer.isAssetInstallable(it.name) }.orEmpty()
        val candidates = assetsOfSameApp(state, installable, releaseTag, installedApp, anchorAssetName)
        val variantMatch =
            AssetVariant.resolvePreferredAsset(
                assets = candidates,
                pinnedVariant = installedApp?.preferredAssetVariant,
                pinnedTokens = AssetVariant.deserializeTokens(installedApp?.preferredAssetTokens),
                pinnedGlob = installedApp?.assetGlobPattern,
                releaseTag = releaseTag,
            )
        val samePositionMatch =
            if (variantMatch == null && candidates.size == installable.size) {
                AssetVariant.resolveBySamePosition(
                    assets = installable,
                    originalIndex = installedApp?.pickedAssetIndex,
                    siblingCountAtPickTime = installedApp?.pickedAssetSiblingCount,
                )
            } else {
                null
            }
        return Assets(installable, variantMatch ?: samePositionMatch ?: installer.choosePrimaryAsset(candidates))
    }

    private fun assetsOfSameApp(
        state: RawDetailsState,
        installable: List<GithubAsset>,
        releaseTag: String?,
        installedApp: InstalledApp?,
        anchorAssetName: String?,
    ): List<GithubAsset> {
        val filter = AssetFilter.parse(installedApp?.assetFilterRegex)?.getOrNull()
        val filtered = filter?.let { installable.filter { asset -> it.matches(asset.name) } }.orEmpty()
        return filtered.ifEmpty {
            AssetOwnership.narrowToApp(
                installable,
                anchorAssetName,
                releaseTag,
                tagOfAnchor(state, anchorAssetName, installedApp),
            )
        }
    }

    // The release an anchor name came from: the selection it was picked in, or the installed, latest or pending version.
    private fun tagOfAnchor(
        state: RawDetailsState,
        anchorAssetName: String?,
        app: InstalledApp?,
    ): String? =
        when {
            anchorAssetName == null -> null
            anchorAssetName == state.primaryAsset?.name -> state.selectedRelease?.tagName
            app == null -> null
            anchorAssetName == app.installedAssetName -> app.installedVersion
            anchorAssetName == app.latestAssetName -> app.latestVersion
            anchorAssetName == app.pendingInstallAssetName -> app.pendingInstallVersion
            else -> null
        }

    internal fun primaryInstalledApp(
        apps: List<InstalledApp>,
        primaryAssetName: String?,
        releaseAssets: List<GithubAsset>,
        releaseHistory: List<GithubRelease>,
        releaseTag: String? = null,
    ): InstalledApp? =
        when {
            apps.isEmpty() -> null
            primaryAssetName == null -> apps.singleOrNull() ?: apps.firstOrNull { !it.isUpdateAvailable } ?: apps.first()
            else -> AssetOwnership.ownerOf(primaryAssetName, apps, releaseAssets, releaseHistory, releaseTag)
        }

    // The newest release in the category whose chosen asset belongs to the app, judged against the state's releases and apps.
    internal fun firstOwnedBy(
        state: RawDetailsState,
        app: InstalledApp,
        category: ReleaseCategory,
        anchorAssetName: String?,
    ): GithubRelease? =
        state.allReleases.firstOrNull { release ->
            release.isIn(category) && isOwnedBy(state, release, app, anchorAssetName)
        }

    private fun isOwnedBy(
        state: RawDetailsState,
        release: GithubRelease,
        app: InstalledApp,
        anchorAssetName: String?,
    ): Boolean {
        val assets = assetsFor(state, release, app, anchorAssetName)
        val owner =
            assets.primary?.let { primary ->
                primaryInstalledApp(state.installedApps, primary.name, assets.installable, state.allReleases, release.tagName)
            }
        return owner?.packageName == app.packageName
    }

    internal fun updateTargetOf(
        releases: List<GithubRelease>,
        app: InstalledApp?,
    ): GithubRelease? =
        app?.takeIf { it.isUpdateAvailable }?.let { target ->
            target.latestReleaseId?.let { id -> releases.firstOrNull { it.id == id } }
                ?: releases.firstOrNull { VersionMath.isExactSameVersion(it.tagName, target.latestVersion) }
        }

    internal fun insights(
        allReleases: List<GithubRelease>,
        installedApp: InstalledApp?,
    ): Insights {
        val latestStable = allReleases.filter { !it.isEffectivelyPreRelease() }.maxByOrNull { it.publishedAt }
        val newer =
            installedApp
                ?.takeIf { allReleases.size > 1 }
                ?.let { app -> allReleases.filter { VersionMath.isVersionNewer(it.tagName, app.installedVersion) } }
                ?.takeIf { it.size >= MIN_RELEASES_TO_MERGE }
        return Insights(
            stalledStableSinceDays = stalledStableDays(allReleases, latestStable),
            mergedChangelog = newer?.let(::mergedChangelog),
            mergedChangelogBaseTag = newer?.let { installedApp.installedVersion },
            latestStableHasInstallableAsset = latestStable?.assets?.any { installer.isAssetInstallable(it.name) } == true,
        )
    }

    private fun mergedChangelog(newer: List<GithubRelease>): String =
        newer.joinToString(separator = "\n\n") { release ->
            val heading = "— ${release.tagName} —"
            val notes = release.description?.trim().orEmpty()
            if (notes.isEmpty()) heading else "$heading\n$notes"
        }

    private fun stalledStableDays(
        allReleases: List<GithubRelease>,
        latestStable: GithubRelease?,
    ): Int? {
        val stable = latestStable ?: return null
        val preReleasesAfter =
            allReleases.any { it.isEffectivelyPreRelease() && VersionMath.isVersionNewer(it.tagName, stable.tagName) }
        val days = daysSinceIso(stable.publishedAt)?.takeIf { preReleasesAfter }
        return days?.takeIf { it >= STALLED_STABLE_THRESHOLD_DAYS }
    }

    internal fun releaseLines(releases: List<GithubRelease>): ImmutableMap<Long, String> =
        if (ReleaseLines.isMultiLine(releases)) {
            releases.associate { it.id to ReleaseLines.of(it.tagName) }.toImmutableMap()
        } else {
            persistentMapOf()
        }

    internal fun platformsByRelease(releases: List<GithubRelease>): ImmutableMap<Long, ImmutableSet<DiscoveryPlatform>> =
        releases.associate { it.id to it.platforms().toImmutableSet() }.toImmutableMap()

    internal fun deviceBuildReleaseIds(releases: List<GithubRelease>): ImmutableSet<Long> =
        releases
            .filter { release -> release.assets.any { installer.isAssetInstallable(it.name) } }
            .map { it.id }
            .toImmutableSet()

    // The state after a fresh release list: the selection carries over by id, then tag, else the channel's first.
    internal fun withFreshReleases(
        state: RawDetailsState,
        freshReleases: List<GithubRelease>,
    ): RawDetailsState {
        val previous = state.selectedRelease
        val carried =
            previous?.let { prev ->
                freshReleases.firstOrNull { it.id == prev.id } ?: freshReleases.firstOrNull { it.tagName == prev.tagName }
            }
        val newSelected =
            carried
                ?: freshReleases.firstInCategory(state.selectedReleaseCategory)
                ?: freshReleases.firstOrNull { !it.isEffectivelyPreRelease() }
                ?: freshReleases.firstOrNull()
        val category =
            when {
                carried != null -> state.selectedReleaseCategory
                newSelected != null -> newSelected.category()
                else -> state.selectedReleaseCategory
            }
        val assets = assetsFor(state, newSelected, state.installedApp)
        val insights = insights(freshReleases, state.installedApp)
        return state
            .copy(
                allReleases = freshReleases,
                releasePlatforms = platformsByRelease(freshReleases),
                deviceBuildReleaseIds = deviceBuildReleaseIds(freshReleases),
                releaseLines = releaseLines(freshReleases),
                selectedRelease = newSelected,
                selectedReleaseCategory = category,
                installableAssets = assets.installable,
                primaryAsset = assets.primary,
                whatsNewTranslation = if (newSelected?.id != previous?.id) TranslationState() else state.whatsNewTranslation,
            ).withInsights(insights)
    }

    private companion object {
        const val STALLED_STABLE_THRESHOLD_DAYS = 180
        const val MIN_RELEASES_TO_MERGE = 2
    }
}

internal fun RawDetailsState.withInsights(insights: ReleaseSelection.Insights): RawDetailsState =
    copy(
        stalledStableSinceDays = insights.stalledStableSinceDays,
        mergedChangelog = insights.mergedChangelog,
        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
        latestStableHasInstallableAsset = insights.latestStableHasInstallableAsset,
    )

internal fun List<GithubRelease>.firstInCategory(category: ReleaseCategory): GithubRelease? = firstOrNull { it.isIn(category) }

internal fun GithubRelease.isIn(category: ReleaseCategory): Boolean =
    when (category) {
        ReleaseCategory.STABLE -> !isEffectivelyPreRelease()
        ReleaseCategory.PRE_RELEASE -> isEffectivelyPreRelease()
        ReleaseCategory.ALL -> true
    }

internal fun GithubRelease.category(): ReleaseCategory =
    if (isEffectivelyPreRelease()) ReleaseCategory.PRE_RELEASE else ReleaseCategory.STABLE
