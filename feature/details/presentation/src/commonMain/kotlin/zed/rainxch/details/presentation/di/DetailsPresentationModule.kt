package zed.rainxch.details.presentation.di

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import zed.rainxch.details.presentation.handlers.ApkInstallStep
import zed.rainxch.details.presentation.handlers.DetailsAppCoordinator
import zed.rainxch.details.presentation.handlers.DetailsAppHandler
import zed.rainxch.details.presentation.handlers.DetailsContentHandler
import zed.rainxch.details.presentation.handlers.DetailsHandoffHandler
import zed.rainxch.details.presentation.handlers.DetailsInstallHandler
import zed.rainxch.details.presentation.handlers.DetailsInstalledApps
import zed.rainxch.details.presentation.handlers.DetailsPageCoordinator
import zed.rainxch.details.presentation.handlers.DetailsPageLoader
import zed.rainxch.details.presentation.handlers.DetailsRefresher
import zed.rainxch.details.presentation.handlers.DetailsReleasePicker
import zed.rainxch.details.presentation.handlers.DetailsRepoExtras
import zed.rainxch.details.presentation.handlers.DetailsRepoHandler
import zed.rainxch.details.presentation.handlers.DetailsRepoReads
import zed.rainxch.details.presentation.handlers.OrchestratedDownloads
import zed.rainxch.details.presentation.handlers.ReleaseSelection

// The handlers behind DetailsViewModel. They keep no state of their own, so each ViewModel gets fresh ones.
val detailsPresentationModule =
    module {
        factoryOf(::ReleaseSelection)
        factoryOf(::DetailsContentHandler)
        factoryOf(::DetailsReleasePicker)
        factoryOf(::DetailsRepoReads)
        factoryOf(::DetailsRepoExtras)
        factoryOf(::DetailsInstalledApps)
        factoryOf(::DetailsPageLoader)
        factoryOf(::DetailsRefresher)
        factoryOf(::DetailsPageCoordinator)
        factoryOf(::ApkInstallStep)
        factoryOf(::OrchestratedDownloads)
        factoryOf(::DetailsInstallHandler)
        factoryOf(::DetailsHandoffHandler)
        factoryOf(::DetailsAppHandler)
        factoryOf(::DetailsRepoHandler)
        factoryOf(::DetailsAppCoordinator)
    }
