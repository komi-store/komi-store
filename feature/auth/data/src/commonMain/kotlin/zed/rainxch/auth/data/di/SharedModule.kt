package zed.rainxch.auth.data.di

import org.koin.dsl.module
import zed.rainxch.auth.data.network.GitHubAuthApi
import zed.rainxch.auth.data.network.WebAuthApi
import zed.rainxch.auth.data.repository.AuthenticationRepositoryImpl
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.domain.model.settings.ProxyScope

val authModule =
    module {
        single<GitHubAuthApi> {
            GitHubAuthApi(
                proxyConfigFlow = ProxyManager.configFlow(ProxyScope.DISCOVERY),
                appScope = get(),
            )
        }

        single<WebAuthApi> {
            WebAuthApi(
                gitHubAuthApi = get(),
            )
        }

        single<AuthenticationRepository> {
            AuthenticationRepositoryImpl(
                tokenStore = get(),
                logger = get(),
                gitHubAuthApi = get(),
                webAuthApi = get(),
            )
        }
    }
