package zed.rainxch.tweaks.presentation.connection

import zed.rainxch.tweaks.presentation.model.MAX_PROXY_PORT
import zed.rainxch.tweaks.presentation.model.ProxyType

data class PastedProxy(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
)

private const val SCHEME_SEPARATOR = "://"

fun parseProxyUrl(raw: String): PastedProxy? {
    val trimmed = raw.trim()
    val type = proxyTypeForScheme(trimmed.substringBefore(SCHEME_SEPARATOR, missingDelimiterValue = "").lowercase())
    val authority =
        trimmed
            .substringAfter(SCHEME_SEPARATOR)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    val endpoint = parseEndpoint(authority.substringAfterLast('@'))
    if (type == null || endpoint == null) return null
    val credentials = if ('@' in authority) authority.substringBeforeLast('@') else null
    return PastedProxy(
        type = type,
        host = endpoint.first,
        port = endpoint.second,
        username = credentials?.substringBefore(':')?.takeIf { it.isNotBlank() },
        password = credentials?.substringAfter(':', missingDelimiterValue = "")?.takeIf { it.isNotBlank() },
    )
}

private fun proxyTypeForScheme(scheme: String): ProxyType? =
    when (scheme) {
        "http", "https" -> ProxyType.HTTP
        "socks", "socks4", "socks5", "socks5h" -> ProxyType.SOCKS
        else -> null
    }

private fun parseEndpoint(hostAndPort: String): Pair<String, Int>? {
    val colon = hostAndPort.lastIndexOf(':')
    val host = hostAndPort.substring(0, colon.coerceAtLeast(0))
    val port = hostAndPort.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..MAX_PROXY_PORT }
    return if (colon > 0 && host.isNotBlank() && port != null) host to port else null
}
