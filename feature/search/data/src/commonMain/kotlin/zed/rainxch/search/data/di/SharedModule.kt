package zed.rainxch.search.data.di

import org.koin.dsl.module
import zed.rainxch.search.data.repository.SearchHistoryRepositoryImpl
import zed.rainxch.search.data.repository.SearchRepositoryImpl
import zed.rainxch.search.domain.repository.SearchHistoryRepository
import zed.rainxch.search.domain.repository.SearchRepository

val searchModule =
    module {
        single<SearchRepository> {
            SearchRepositoryImpl(
                clientProvider = get(),
                backendApiClient = get(),
                cacheManager = get(),
                forgejoClientRegistry = get(),
                tokenStore = get(),
            )
        }

        single<SearchHistoryRepository> {
            SearchHistoryRepositoryImpl(
                searchHistoryDao = get(),
            )
        }
    }
