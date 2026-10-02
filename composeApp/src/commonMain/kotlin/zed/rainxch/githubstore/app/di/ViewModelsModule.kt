package zed.rainxch.githubstore.app.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.apps.presentation.import.ExternalImportViewModel
import zed.rainxch.apps.presentation.starred.StarredPickerViewModel
import zed.rainxch.auth.presentation.AuthenticationViewModel
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
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
import zed.rainxch.search.presentation.SearchViewModel
import zed.rainxch.starred.presentation.StarredReposViewModel
import zed.rainxch.tweaks.presentation.TweaksViewModel
import zed.rainxch.tweaks.presentation.appinfo.AppInfoViewModel
import zed.rainxch.tweaks.presentation.feedback.FeedbackViewModel
import zed.rainxch.tweaks.presentation.hidden.HiddenRepositoriesViewModel
import zed.rainxch.tweaks.presentation.hosttokens.HostTokensViewModel
import zed.rainxch.tweaks.presentation.licenses.LicensesViewModel
import zed.rainxch.tweaks.presentation.mirror.MirrorPickerViewModel
import zed.rainxch.tweaks.presentation.skipped.SkippedUpdatesViewModel

val viewModelsModule =
    module {
        viewModelOf(::AppsViewModel)
        viewModelOf(::ExternalImportViewModel)
        viewModelOf(::AuthenticationViewModel)
        viewModel { params ->
            DetailsViewModel(
                repositoryId = params[0],
                ownerParam = params[1],
                repoParam = params[2],
                isComingFromUpdate = params[3],
                sourceHostParam = if (params.size() > 4) params[4] else null,
                initialAssetNameParam = if (params.size() > 5) params[5] else null,
                packageNameParam = if (params.size() > 6) params[6] else null,
                detailsRepository = get(),
                downloader = get(),
                installer = get(),
                platform = get(),
                helper = get(),
                shareManager = get(),
                installedAppsRepository = get(),
                favouritesRepository = get(),
                starredRepository = get(),
                packageMonitor = get(),
                syncInstalledAppsUseCase = get(),
                translationRepository = get(),
                logger = get(),
                tweaksRepository = get(),
                seenReposRepository = get(),
                installationManager = get(),
                attestationVerifier = get(),
                downloadOrchestrator = get(),
                externalImportRepository = get(),
                apkInspector = get(),
                userSessionRepository = get(),
                systemInstallSerializer = get(),
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
            )
        }
        viewModel {
            MirrorPickerViewModel(
                mirrorRepository = get(),
                testHttpClient = get(qualifier = named("test")),
            )
        }
    }
