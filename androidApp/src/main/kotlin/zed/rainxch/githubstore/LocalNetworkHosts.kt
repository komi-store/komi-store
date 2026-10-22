package zed.rainxch.githubstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.core.domain.network.isLocalNetworkAddress
import zed.rainxch.core.domain.repository.TweaksRepository
import java.net.InetAddress

internal fun localNetworkHosts(tweaksRepository: TweaksRepository): Flow<Set<String>> {
    val proxyHosts =
        combine(ProxyScope.entries.map(ProxyManager::configFlow)) { configs ->
            configs.mapNotNull { config ->
                when (config) {
                    is ProxyConfig.Http -> config.host
                    is ProxyConfig.Socks -> config.host
                    ProxyConfig.None, ProxyConfig.System -> null
                }
            }
        }
    val translationHost =
        combine(tweaksRepository.getTranslationProvider(), tweaksRepository.getLibreTranslateBaseUrl()) { provider, url ->
            url.takeIf { provider == TranslationProvider.LIBRE_TRANSLATE && it.isNotBlank() }
        }
    return combine(proxyHosts, tweaksRepository.getCustomForgeHosts(), translationHost) { proxies, forges, translation ->
        (proxies + forges + listOfNotNull(translation)).map(::hostOf).filter(::isOnLocalNetwork).toSet()
    }.flowOn(Dispatchers.IO)
}

private fun hostOf(raw: String): String {
    val authority =
        raw
            .trim()
            .lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringAfterLast('@')
    return when {
        authority.startsWith("[") -> authority.substringAfter('[').substringBefore(']')
        authority.count { it == ':' } == 1 -> authority.substringBefore(':')
        else -> authority
    }
}

// Resolving a .local name already needs the permission, so those can't be checked by address
private fun isOnLocalNetwork(host: String): Boolean =
    host.endsWith(".local") ||
        runCatching { InetAddress.getAllByName(host) }.getOrNull().orEmpty().any { isLocalNetworkAddress(it.address) }
