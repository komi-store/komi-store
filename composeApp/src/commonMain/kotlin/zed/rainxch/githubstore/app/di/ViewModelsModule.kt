package zed.rainxch.githubstore.app.di

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.apps.presentation.import.ExternalImportViewModel
import zed.rainxch.apps.presentation.library.LibraryBackup
import zed.rainxch.apps.presentation.library.LibraryLoader
import zed.rainxch.apps.presentation.link.LinkSheetActions
import zed.rainxch.apps.presentation.settings.AppRowActions
import zed.rainxch.apps.presentation.settings.AssetMatchSettings
import zed.rainxch.apps.presentation.starred.StarredPickerViewModel
import zed.rainxch.apps.presentation.updates.AppUpdates
import zed.rainxch.apps.presentation.updates.UpdateDownloads
import zed.rainxch.apps.presentation.updates.UpdateInstalls
import zed.rainxch.apps.presentation.updates.UpdateTargetResolver
import zed.rainxch.auth.presentation.AuthenticationViewModel
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.details.presentation.DetailsArgs
import zed.rainxch.details.presentation.DetailsViewModel
import zed.rainxch.details.presentation.about.DetailsAboutViewModel
import zed.rainxch.details.presentation.whatsnew.DetailsWhatsNewViewModel
import zed.rainxch.devprofile.presentation.DeveloperProfileViewModel
import zed.rainxch.favourites.presentation.FavouritesViewModel
import zed.rainxch.favourites.presentation.import.ImportStarsViewModel
import zed.rainxch.feed.presentation.FeedViewModel
import zed.rainxch.home.presentation.HomeViewModel
import zed.rainxch.home.presentation.categorylist.CategoryListViewModel
import zed.rainxch.profile.presentation.ProfileViewModel
import zed.rainxch.profile.presentation.announcements.AnnouncementsViewModel
import zed.rainxch.profile.presentation.whatsnew.WhatsNewViewModel
import zed.rainxch.recentlyviewed.presentation.RecentlyViewedViewModel
import zed.rainxch.repopages.presentation.issuedetail.IssueDetailViewModel
import zed.rainxch.repopages.presentation.issues.IssuesViewModel
import zed.rainxch.repopages.presentation.pulls.PullsViewModel
import zed.rainxch.repopages.presentation.security.SecurityViewModel
import zed.rainxch.reviews.presentation.ReviewsArgs
import zed.rainxch.reviews.presentation.ReviewsViewModel
import zed.rainxch.reviews.presentation.moderation.ModerationViewModel
import zed.rainxch.search.presentation.SearchViewModel
import zed.rainxch.starred.presentation.StarredReposViewModel
import zed.rainxch.tweaks.presentation.TweaksViewModel
import zed.rainxch.tweaks.presentation.appinfo.AppInfoViewModel
import zed.rainxch.tweaks.presentation.feedback.FeedbackViewModel
import zed.rainxch.tweaks.presentation.handlers.ConnectionHandler
import zed.rainxch.tweaks.presentation.handlers.DiagnosticsHandler
import zed.rainxch.tweaks.presentation.handlers.InstallsHandler
import zed.rainxch.tweaks.presentation.handlers.LookAndFeelHandler
import zed.rainxch.tweaks.presentation.handlers.PrivacyHandler
import zed.rainxch.tweaks.presentation.handlers.TranslationHandler
import zed.rainxch.tweaks.presentation.hidden.HiddenRepositoriesViewModel
import zed.rainxch.tweaks.presentation.hosttokens.HostTokensViewModel
import zed.rainxch.tweaks.presentation.licenses.LicensesViewModel
import zed.rainxch.tweaks.presentation.mirror.MirrorPickerViewModel
import zed.rainxch.tweaks.presentation.skipped.SkippedUpdatesViewModel

val viewModelsModule =
    module {
        // Factories, not singles: each collaborator keeps the state of the one AppsViewModel it serves.
        factoryOf(::LibraryLoader)
        factoryOf(::UpdateTargetResolver)
        factoryOf(::UpdateDownloads)
        factoryOf(::UpdateInstalls)
        factoryOf(::AppUpdates)
        factoryOf(::LinkSheetActions)
        factoryOf(::AppRowActions)
        factoryOf(::AssetMatchSettings)
        factoryOf(::LibraryBackup)
        viewModelOf(::AppsViewModel)
        viewModelOf(::ExternalImportViewModel)
        viewModelOf(::AuthenticationViewModel)
        viewModel { params ->
            DetailsViewModel(
                args =
                    DetailsArgs(
                        repositoryId = params[0],
                        owner = params[1],
                        repo = params[2],
                        isComingFromUpdate = params[3],
                        sourceHost = if (params.size() > 4) params[4] else null,
                        packageName = if (params.size() > 5) params[5] else null,
                    ),
                platform = get(),
                page = get(),
                app = get(),
            )
        }
        viewModel { params ->
            DetailsAboutViewModel(
                repositoryId = params[0],
                owner = params[1],
                repo = params[2],
                sourceHost = if (params.size() > 3) params[3] else null,
                detailsRepository = get(),
                translationRepository = get(),
            )
        }
        viewModel { params ->
            DetailsWhatsNewViewModel(
                repositoryId = params[0],
                owner = params[1],
                repo = params[2],
                sourceHost = if (params.size() > 3) params[3] else null,
                detailsRepository = get(),
                translationRepository = get(),
            )
        }
        viewModel { params ->
            zed.rainxch.details.presentation.markdownviewer.MarkdownViewerViewModel(
                url = params[0],
                detailsRepository = get(),
                translationRepository = get(),
            )
        }
        viewModelOf(::DeveloperProfileViewModel)
        viewModelOf(::ModerationViewModel)
        viewModel { params ->
            ReviewsViewModel(
                args =
                    ReviewsArgs(
                        owner = params[0],
                        repo = params[1],
                        sourceHost = params[2],
                        openComposer = params[3],
                        composerRating = params[4],
                        version = params[5],
                        appLabel = params[6],
                    ),
                repository = get(),
                userSessionRepository = get(),
            )
        }
        viewModel { params ->
            IssuesViewModel(
                owner = params[0],
                repo = params[1],
                repository = get(),
                userSessionRepository = get(),
            )
        }
        viewModel { params ->
            IssueDetailViewModel(
                owner = params[0],
                repo = params[1],
                issueNumber = params[2],
                repository = get(),
                userSessionRepository = get(),
            )
        }
        viewModel { params ->
            SecurityViewModel(
                owner = params[0],
                repo = params[1],
                repository = get(),
            )
        }
        viewModel { params ->
            PullsViewModel(
                owner = params[0],
                repo = params[1],
                repository = get(),
            )
        }
        viewModelOf(::FavouritesViewModel)
        viewModelOf(::ImportStarsViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::FeedViewModel)
        viewModelOf(::RecentlyViewedViewModel)
        viewModel { params ->
            SearchViewModel(
                searchRepository = get(),
                installedAppsRepository = get(),
                syncInstalledAppsUseCase = get(),
                favouritesRepository = get(),
                starredRepository = get(),
                logger = get(),
                shareManager = get(),
                platform = get(),
                clipboardHelper = get(),
                tweaksRepository = get(),
                seenReposRepository = get(),
                searchHistoryRepository = get(),
                hiddenReposRepository = get(),
                userSessionRepository = get(),
                browseFilterStore = get(),
                initialPlatform = params.getOrNull<DiscoveryPlatform>(),
            )
        }
        viewModelOf(::ProfileViewModel)
        factoryOf(::LookAndFeelHandler)
        factoryOf(::ConnectionHandler)
        factoryOf(::InstallsHandler)
        factoryOf(::TranslationHandler)
        factoryOf(::PrivacyHandler)
        factoryOf(::DiagnosticsHandler)
        viewModelOf(::TweaksViewModel)
        viewModelOf(::FeedbackViewModel)
        viewModelOf(::StarredReposViewModel)
        viewModelOf(::StarredPickerViewModel)
        viewModelOf(::SkippedUpdatesViewModel)
        viewModelOf(::HiddenRepositoriesViewModel)
        viewModelOf(::HostTokensViewModel)
        viewModelOf(::LicensesViewModel)
        viewModelOf(::AppInfoViewModel)
        viewModelOf(::WhatsNewViewModel)
        viewModelOf(::AnnouncementsViewModel)
        viewModel { params ->
            CategoryListViewModel(
                category = params.get(),
                homeRepository = get(),
                tweaksRepository = get(),
            )
        }
        viewModelOf(::MirrorPickerViewModel)
    }
