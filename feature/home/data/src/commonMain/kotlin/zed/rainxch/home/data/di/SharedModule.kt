package zed.rainxch.home.data.di

import org.koin.dsl.module
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.home.data.datasource.CachedRepositoriesDataSource
import zed.rainxch.home.data.datasource.impl.CachedRepositoriesDataSourceImpl
import zed.rainxch.home.data.repository.HomeRepositoryImpl
import zed.rainxch.home.domain.repository.HomeRepository

val homeModule =
    module {
        single<HomeRepository> {
            HomeRepositoryImpl(
                cachedDataSource = get(),
                clientProvider = get(),
                devicePlatform = get(),
                logger = get(),
                cacheManager = get(),
            )
        }

        single<CachedRepositoriesDataSource> {
            CachedRepositoriesDataSourceImpl(
                backendApiClient = get(),
                logger = get(),
            )
        }
    }
