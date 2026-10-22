package zed.rainxch.githubstore.app.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import zed.rainxch.githubstore.MainViewModel

val mainModule: Module =
    module {
        viewModel {
            MainViewModel(
                tweaksRepository = get(),
                installedAppsRepository = get(),
                rateLimitRepository = get(),
                syncUseCase = get(),
                userSessionRepository = get(),
                logger = get(),
                localizationManager = get(),
            )
        }
    }
