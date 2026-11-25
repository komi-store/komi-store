package zed.rainxch.tweaks.presentation.handlers

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.invalid_proxy_port
import zed.rainxch.githubstore.core.presentation.res.proxy_host_invalid
import zed.rainxch.githubstore.core.presentation.res.proxy_host_required
import zed.rainxch.tweaks.presentation.model.MAX_PROXY_PORT
import zed.rainxch.tweaks.presentation.model.ProxyScopeFormState
import zed.rainxch.tweaks.presentation.model.ProxyType

internal sealed interface ProxyDraft {
    data class Ready(
        val config: ProxyConfig,
    ) : ProxyDraft

    data class Rejected(
        val reason: StringResource,
    ) : ProxyDraft
}

private const val MAX_HOST_LENGTH = 253
private const val URL_DELIMITERS = "/?#"

private val IPV4_PATTERN =
    Regex(
        "^(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)" +
            "(\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)){3}$",
    )

private val IPV6_PATTERN = Regex("^[0-9A-Fa-f:]+$")

private val HOSTNAME_PATTERN =
    Regex(
        "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$",
    )

internal fun ProxyScopeFormState.toDraft(): ProxyDraft =
    when (type) {
        ProxyType.NONE -> ProxyDraft.Ready(ProxyConfig.None)
        ProxyType.SYSTEM -> ProxyDraft.Ready(ProxyConfig.System)
        ProxyType.HTTP, ProxyType.SOCKS -> endpointDraft()
    }

// The main proxy's test skips validation: it only needs a host and a numeric port.
internal fun ProxyScopeFormState.toUncheckedConfig(): ProxyConfig? =
    when (type) {
        ProxyType.NONE -> {
            ProxyConfig.None
        }

        ProxyType.SYSTEM -> {
            ProxyConfig.System
        }

        ProxyType.HTTP, ProxyType.SOCKS -> {
            val trimmedHost = host.trim()
            val parsedPort = port.toIntOrNull()
            if (trimmedHost.isNotEmpty() && parsedPort != null) endpointConfig(trimmedHost, parsedPort) else null
        }
    }

internal fun ProxyScopeFormState.withConfig(config: ProxyConfig): ProxyScopeFormState =
    when (config) {
        ProxyConfig.None -> {
            copy(type = ProxyType.NONE)
        }

        ProxyConfig.System -> {
            copy(type = ProxyType.SYSTEM)
        }

        is ProxyConfig.Http -> {
            copy(
                type = ProxyType.HTTP,
                host = config.host,
                port = config.port.toString(),
                username = config.username.orEmpty(),
                password = config.password.orEmpty(),
            )
        }

        is ProxyConfig.Socks -> {
            copy(
                type = ProxyType.SOCKS,
                host = config.host,
                port = config.port.toString(),
                username = config.username.orEmpty(),
                password = config.password.orEmpty(),
            )
        }
    }

private fun ProxyScopeFormState.endpointDraft(): ProxyDraft {
    val validPort = port.toIntOrNull()?.takeIf { it in 1..MAX_PROXY_PORT }
    val validHost = host.trim().takeIf { isValidProxyHost(it) }
    return when {
        validPort == null -> ProxyDraft.Rejected(Res.string.invalid_proxy_port)
        validHost == null -> ProxyDraft.Rejected(if (host.isBlank()) Res.string.proxy_host_required else Res.string.proxy_host_invalid)
        else -> ProxyDraft.Ready(endpointConfig(validHost, validPort))
    }
}

private fun ProxyScopeFormState.endpointConfig(
    host: String,
    port: Int,
): ProxyConfig {
    val user = username.takeIf { it.isNotBlank() }
    val pass = password.takeIf { it.isNotBlank() }
    return if (type == ProxyType.HTTP) ProxyConfig.Http(host, port, user, pass) else ProxyConfig.Socks(host, port, user, pass)
}

private fun isValidProxyHost(host: String): Boolean {
    val plausible =
        host.isNotBlank() &&
            host.length <= MAX_HOST_LENGTH &&
            host.none { it.isWhitespace() || it in URL_DELIMITERS }
    return plausible && (IPV4_PATTERN.matches(host) || isIpv6(host) || HOSTNAME_PATTERN.matches(host))
}

private fun isIpv6(host: String): Boolean {
    val candidate = host.trim('[', ']')
    return ':' in candidate && IPV6_PATTERN.matches(candidate)
}
