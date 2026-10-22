package zed.rainxch.core.data.network

import zed.rainxch.core.domain.system.DesktopOs
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal data class OsTrustChain(
    val socketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
)

internal fun buildOsTrustChainOrNull(): OsTrustChain? {
    val managers =
        buildList {
            defaultTrustManagerOrNull()?.let { add(it) }
            osTrustManagerOrNull()?.let { add(it) }
        }
    if (managers.size < 2) return null
    val composite = CompositeX509TrustManager(managers)
    return try {
        val ctx =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(composite), null)
            }
        OsTrustChain(ctx.socketFactory, composite)
    } catch (_: Throwable) {
        null
    }
}

private fun defaultTrustManagerOrNull(): X509TrustManager? = trustManagerFromKeyStore(null)

private fun osTrustManagerOrNull(): X509TrustManager? {
    val keyStore =
        when {
            DesktopOs.isWindows -> loadKeyStore("Windows-ROOT")
            DesktopOs.isMac -> loadKeyStore("KeychainStore")
            else -> null
        } ?: return null
    return trustManagerFromKeyStore(keyStore)
}

private fun loadKeyStore(type: String): KeyStore? =
    try {
        KeyStore.getInstance(type).apply { load(null, null) }
    } catch (_: Throwable) {
        null
    }

private fun trustManagerFromKeyStore(keyStore: KeyStore?): X509TrustManager? =
    try {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    } catch (_: Throwable) {
        null
    }

private class CompositeX509TrustManager(
    private val delegates: List<X509TrustManager>,
) : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        var lastError: Throwable? = null
        for (tm in delegates) {
            try {
                tm.checkClientTrusted(chain, authType)
                return
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw CertificateException("No trust manager accepted the chain", lastError)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        var lastError: Throwable? = null
        for (tm in delegates) {
            try {
                tm.checkServerTrusted(chain, authType)
                return
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw CertificateException("No trust manager accepted the chain", lastError)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegates.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
}
