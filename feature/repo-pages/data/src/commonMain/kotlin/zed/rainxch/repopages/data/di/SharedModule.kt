package zed.rainxch.repopages.data.di

import org.koin.dsl.module
import zed.rainxch.repopages.data.repository.RepoPagesRepositoryImpl
import zed.rainxch.repopages.domain.repository.RepoPagesRepository

val repoPagesModule =
    module {
        single<RepoPagesRepository> {
            RepoPagesRepositoryImpl(
                clientProvider = get(),
                logger = get(),
            )
        }
    }
