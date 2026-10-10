package zed.rainxch.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.dsl.module
import zed.rainxch.core.data.local.data_store.createAnnouncementsDataStore
import zed.rainxch.core.data.local.data_store.createDataStore
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.initDatabase
import zed.rainxch.core.data.services.DesktopApkInspector
import zed.rainxch.core.data.services.DesktopInstallerInfoExtractor
import zed.rainxch.core.data.utils.DesktopAppLauncher
import zed.rainxch.core.data.utils.DesktopBrowserHelper
import zed.rainxch.core.data.utils.DesktopClipboardHelper
import zed.rainxch.core.data.services.DesktopDownloader
import zed.rainxch.core.data.services.DesktopDownloadProgressNotifier
import zed.rainxch.core.data.services.DesktopFileLocationsProvider
import zed.rainxch.core.data.services.DesktopInstaller
import zed.rainxch.core.data.services.DesktopLocalizationManager
import zed.rainxch.core.data.services.DesktopPackageMonitor
import zed.rainxch.core.data.services.DesktopPendingInstallNotifier
import zed.rainxch.core.data.services.DesktopUpdateScheduleManager
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.data.services.external.DesktopExternalAppScanner
import zed.rainxch.core.domain.system.DownloadProgressNotifier
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerStatusProvider
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.system.UpdateScheduleManager
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.data.services.DesktopInstallerStatusProvider
import zed.rainxch.core.data.utils.DesktopShareManager
import zed.rainxch.core.data.network.DesktopDigestVerifier
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.system.ApkInspector
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.helpers.AppLauncher
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.helpers.ShareManager

actual val corePlatformModule = module {

    single<Downloader> {
        DesktopDownloader(
            files = get(),
            tokenStore = get(),
            digestVerifier = get(),
        )
    }

    single<Installer> {
        DesktopInstaller(
            platform = get(),
            installerInfoExtractor = DesktopInstallerInfoExtractor(),
        )
    }

    single<FileLocationsProvider> {
        DesktopFileLocationsProvider(
            platform = get()
        )
    }

    single<zed.rainxch.core.domain.system.AggressiveOemDetector> {
        zed.rainxch.core.data.services.DesktopAggressiveOemDetector()
    }

    single<PackageMonitor> {
        DesktopPackageMonitor()
    }

    single<ApkInspector> {
        DesktopApkInspector()
    }

    single<ExternalAppScanner> {
        DesktopExternalAppScanner()
    }

    single<LocalizationManager> {
        DesktopLocalizationManager()
    }

    single<AppDatabase> {
        initDatabase()
    }

    single<DataStore<Preferences>> {
        createDataStore()
    }

    single<DataStore<Preferences>>(qualifier = org.koin.core.qualifier.named("announcements")) {
        createAnnouncementsDataStore()
    }

    single<eu.anifantakis.lib.ksafe.KSafe>(qualifier = org.koin.core.qualifier.named("tokens")) {
        eu.anifantakis.lib.ksafe.KSafe(
            fileName = "ghs_tokens",
            baseDir = zed.rainxch.core.data.local.DesktopAppDataPaths.ksafeBaseDir(),
        )
    }

    single<eu.anifantakis.lib.ksafe.KSafe>(qualifier = org.koin.core.qualifier.named("prefs")) {
        eu.anifantakis.lib.ksafe.KSafe(
            fileName = "ghs_prefs",
            baseDir = zed.rainxch.core.data.local.DesktopAppDataPaths.ksafeBaseDir(),
        )
    }

    single<eu.anifantakis.lib.ksafe.KSafe>(qualifier = org.koin.core.qualifier.named("announcements_cache")) {
        eu.anifantakis.lib.ksafe.KSafe(
            fileName = "ghs_announcements",
            baseDir = zed.rainxch.core.data.local.DesktopAppDataPaths.ksafeBaseDir(),
        )
    }

    single<BrowserHelper> {
        DesktopBrowserHelper()
    }

    single<DigestVerifier> {
        DesktopDigestVerifier()
    }

    single<ClipboardHelper> {
        DesktopClipboardHelper()
    }

    single<AppLauncher> {
        DesktopAppLauncher(
            logger = get(),
            platform = get()
        )
    }

    single<ShareManager> {
        DesktopShareManager()
    }

    single<InstallerStatusProvider> {
        DesktopInstallerStatusProvider()
    }

    single<UpdateScheduleManager> {
        DesktopUpdateScheduleManager()
    }

    single<PendingInstallNotifier> {
        DesktopPendingInstallNotifier()
    }

    single<DownloadProgressNotifier> {
        DesktopDownloadProgressNotifier()
    }
}