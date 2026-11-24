package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.flow.distinctUntilChanged
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.usecases.SyncInstalledAppsUseCase
import zed.rainxch.details.presentation.model.TranslationState

// The repo's apps on this device: reading, watching and re-checking them, and the installer apps the page can hand off to.
internal class DetailsInstalledApps(
    private val installedAppsRepository: InstalledAppsRepository,
    private val packageMonitor: PackageMonitor,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val installer: Installer,
    private val selection: ReleaseSelection,
    private val logger: KomiStoreLogger,
) {
    internal class Environment(
        val systemArchitecture: SystemArchitecture,
        val isObtainiumAvailable: Boolean,
        val isAppManagerAvailable: Boolean,
    )

    internal fun environment(): Environment =
        Environment(
            systemArchitecture = installer.detectSystemArchitecture(),
            isObtainiumAvailable = installer.isObtainiumInstalled(),
            isAppManagerAvailable = installer.isAppManagerInstalled(),
        )

    internal suspend fun sync() {
        syncInstalledAppsUseCase().onFailure { logger.warn("Sync had issues but continuing: ${it.message}") }
    }

    // A pending install the system has since finished is marked installed before the page shows it.
    internal suspend fun appsOf(repoId: Long): List<InstalledApp> =
        try {
            installedAppsRepository.getAppsByRepoId(repoId).map { app ->
                if (app.isPendingInstall && packageMonitor.isPackageInstalled(app.packageName)) {
                    installedAppsRepository.updatePendingStatus(app.packageName, false)
                    installedAppsRepository.getAppByPackage(app.packageName) ?: app
                } else {
                    app
                }
            }
        } catch (e: LocalStoreException) {
            logger.error("Failed to load installed apps: ${e.message}")
            emptyList()
        } catch (e: SystemPackagesException) {
            logger.error("Failed to load installed apps: ${e.message}")
            emptyList()
        }

    internal fun observe(
        session: DetailsSession,
        repoId: Long,
    ) {
        session.launch {
            installedAppsRepository
                .getAppsByRepoIdAsFlow(repoId)
                .distinctUntilChanged()
                .collect { apps -> onAppsChanged(session, apps) }
        }
    }

    private fun onAppsChanged(
        session: DetailsSession,
        apps: List<InstalledApp>,
    ) {
        val current = session.current
        val primaryAssetName = current.primaryAsset?.name
        val packageName = session.args.packageName
        val primary =
            if (primaryAssetName == null && !packageName.isNullOrBlank()) {
                apps.firstOrNull { it.packageName == packageName }
                    ?: selection.primaryInstalledApp(apps, null, current.installableAssets, current.allReleases)
            } else {
                selection.primaryInstalledApp(
                    apps,
                    primaryAssetName,
                    current.installableAssets,
                    current.allReleases,
                    releaseTag = current.selectedRelease?.tagName,
                )
            }
        val insights = selection.insights(current.allReleases, primary)
        session.update { it.copy(installedApp = primary, installedApps = apps).withInsights(insights) }
    }

    // Best-effort: a failed sync must not fail the refresh that read these releases.
    internal suspend fun syncLibraryUpdateState(
        session: DetailsSession,
        freshReleases: List<GithubRelease>,
    ) {
        val installed = session.current.installedApp ?: return
        try {
            installedAppsRepository.getAppsByRepoId(installed.repoId).forEach { app ->
                installedAppsRepository.checkForUpdatesWithReleases(packageName = app.packageName, releases = freshReleases)
            }
            installedAppsRepository.getAppByPackage(installed.packageName)?.let { refreshed ->
                followUpdateTarget(session, installed, refreshed)
            }
        } catch (e: LocalStoreException) {
            logger.warn("Refresh: library update sync failed: ${e.message}")
        }
    }

    // observe already hands the UI the rewritten row; this moves the selection to the new update target as loading would.
    private fun followUpdateTarget(
        session: DetailsSession,
        installed: InstalledApp,
        refreshed: InstalledApp,
    ) {
        val updateStateChanged =
            refreshed.isUpdateAvailable != installed.isUpdateAvailable ||
                refreshed.latestVersion != installed.latestVersion ||
                refreshed.latestReleaseId != installed.latestReleaseId
        if (!updateStateChanged || session.hasUserPickedRelease) return
        val current = session.current
        val target =
            selection.updateTargetOf(current.allReleases, refreshed)?.takeIf { it.id != current.selectedRelease?.id } ?: return
        val anchor = refreshed.installedAssetName ?: refreshed.latestAssetName ?: refreshed.pendingInstallAssetName
        val assets = selection.assetsFor(current, target, refreshed, anchor)
        session.whatsNewTranslationJob?.cancel()
        session.update {
            it.copy(
                installedApp = refreshed,
                selectedRelease = target,
                selectedReleaseCategory = target.category(),
                installableAssets = assets.installable,
                primaryAsset = assets.primary,
                whatsNewTranslation = TranslationState(),
                whatsNewMeasuredHeightPx = null,
            )
        }
    }
}
