package zed.rainxch.search.data.di

import org.koin.dsl.module
import zed.rainxch.search.data.repository.BackendSearch
import zed.rainxch.search.data.repository.GithubSearch
import zed.rainxch.search.data.repository.InstallerReleaseCheck
import zed.rainxch.search.data.repository.SearchHistoryRepositoryImpl
import zed.rainxch.search.data.repository.SearchRepositoryImpl
import zed.rainxch.search.domain.repository.SearchHistoryRepository
import zed.rainxch.search.domain.repository.SearchRepository

val searchModule =
    module {
        single { InstallerReleaseCheck(clientProvider = get()) }

        single {
            GithubSearch(
                clientProvider = get(),
                cacheManager = get(),
                tokenStore = get(),
                installerCheck = get(),
            )
        }

        single { BackendSearch(backendApiClient = get(), tokenStore = get()) }

        single<SearchRepository> {
            SearchRepositoryImpl(
                cacheManager = get(),
                forgejoClientRegistry = get(),
                backendApiClient = get(),
                backendSearch = get(),
                githubSearch = get(),
            )
        }

        single<SearchHistoryRepository> {
            SearchHistoryRepositoryImpl(
                searchHistoryDao = get(),
            )
        }
    }
