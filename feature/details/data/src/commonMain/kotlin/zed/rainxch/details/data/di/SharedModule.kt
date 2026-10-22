package zed.rainxch.details.data.di

import org.koin.dsl.module
import zed.rainxch.details.data.repository.DetailsRepositoryImpl
import zed.rainxch.details.data.repository.TranslationRepositoryImpl
import zed.rainxch.details.data.system.AttestationVerifierImpl
import zed.rainxch.details.data.system.InstallationManagerImpl
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.InstallationManager

val detailsModule =
    module {
        single<DetailsRepository> {
            DetailsRepositoryImpl(
                logger = get(),
                clientProvider = get(),
                backendApiClient = get(),
                localizationManager = get(),
                cacheManager = get(),
                forgejoClientRegistry = get(),
                tokenStore = get(),
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
