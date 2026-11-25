package zed.rainxch.details.data.di

import org.koin.dsl.module
import zed.rainxch.details.data.repository.BackendFallback
import zed.rainxch.details.data.repository.DetailsRepositoryImpl
import zed.rainxch.details.data.repository.ForgejoDetailsSource
import zed.rainxch.details.data.repository.ReleaseReader
import zed.rainxch.details.data.repository.RepoContentReader
import zed.rainxch.details.data.repository.RepoInfoReader
import zed.rainxch.details.data.repository.TranslationRepositoryImpl
import zed.rainxch.details.data.system.AttestationVerifierImpl
import zed.rainxch.details.data.system.InstallationManagerImpl
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.InstallationManager

val detailsModule =
    module {
        single {
            BackendFallback(tokenStore = get())
        }

        single {
            ForgejoDetailsSource(
                clientRegistry = get(),
                cacheManager = get(),
                logger = get(),
            )
        }

        single<DetailsRepository> {
            DetailsRepositoryImpl(
                repoInfo =
                    RepoInfoReader(
                        clientProvider = get(),
                        backendApiClient = get(),
                        cacheManager = get(),
                        backendFallback = get(),
                        forgejo = get(),
                        logger = get(),
                    ),
                releases =
                    ReleaseReader(
                        clientProvider = get(),
                        backendApiClient = get(),
                        cacheManager = get(),
                        backendFallback = get(),
                        forgejo = get(),
                        logger = get(),
                    ),
                content =
                    RepoContentReader(
                        clientProvider = get(),
                        backendApiClient = get(),
                        cacheManager = get(),
                        backendFallback = get(),
                        forgejo = get(),
                        logger = get(),
                    ),
            )
        }

        single<TranslationRepository> {
            TranslationRepositoryImpl(
                localizationManager = get(),
                clientProvider = get(),
                tweaksRepository = get(),
            )
        }

        single<AttestationVerifier> {
            AttestationVerifierImpl(
                detailsRepository = get(),
                logger = get(),
            )
        }

        single<InstallationManager> {
            InstallationManagerImpl(
                installer = get(),
                installedAppsRepository = get(),
                favouritesRepository = get(),
                tweaksRepository = get(),
                packageMonitor = get(),
                logger = get(),
            )
        }
    }
