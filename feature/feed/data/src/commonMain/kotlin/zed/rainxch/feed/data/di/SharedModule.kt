package zed.rainxch.feed.data.di

import org.koin.dsl.module
import zed.rainxch.feed.data.repository.FeedRepositoryImpl
import zed.rainxch.feed.domain.repository.FeedRepository

val feedModule =
    module {
        single<FeedRepository> {
            FeedRepositoryImpl(
                backendApiClient = get(),
                cacheManager = get(),
                logger = get(),
            )
        }
    }
