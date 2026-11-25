package zed.rainxch.apps.data.di

import org.koin.dsl.module
import zed.rainxch.apps.data.repository.AppLinkingImpl
import zed.rainxch.apps.data.repository.AppsBackupImpl
import zed.rainxch.apps.data.repository.AppsRepositoryImpl
import zed.rainxch.apps.data.repository.RepoLookupImpl
import zed.rainxch.apps.domain.repository.AppsRepository

val appsModule =
    module {
        single<AppsRepository> {
            val lookup =
                RepoLookupImpl(
                    clientProvider = get(),
                    backendApiClient = get(),
                    forgejoClientRegistry = get(),
                    tweaksRepository = get(),
                    logger = get(),
                )
            val linking =
                AppLinkingImpl(
                    installedApps = get(),
                    packageMonitor = get(),
                    tweaksRepository = get(),
                )
            AppsRepositoryImpl(
                appLauncher = get(),
                logger = get(),
                lookup = lookup,
                linking = linking,
                backup =
                    AppsBackupImpl(
                        installedApps = get(),
                        packageMonitor = get(),
                        lookup = lookup,
                        linking = linking,
                        logger = get(),
                    ),
            )
        }
    }
