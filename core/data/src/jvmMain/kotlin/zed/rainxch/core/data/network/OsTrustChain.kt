package zed.rainxch.core.data.network

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.system.DesktopOs
import java.io.IOException
import java.security.GeneralSecurityException
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
    } catch (e: GeneralSecurityException) {
        Logger.w(e) { "Could not build a TLS context over the OS trust store" }
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
    } catch (e: GeneralSecurityException) {
        Logger.w(e) { "Could not load the $type key store" }
        null
    } catch (e: IOException) {
        Logger.w(e) { "Could not load the $type key store" }
        null
    }

private fun trustManagerFromKeyStore(keyStore: KeyStore?): X509TrustManager? =
    try {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    } catch (e: GeneralSecurityException) {
        Logger.w(e) { "Could not build a trust manager for ${keyStore?.type ?: "the JDK default"} key store" }
        null
    }

private class CompositeX509TrustManager(
    private val delegates: List<X509TrustManager>,
) : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        var lastError: Exception? = null
        for (tm in delegates) {
            try {
                tm.checkClientTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                lastError = e
            } catch (e: IllegalArgumentException) {
                lastError = e
            }
        }
        throw CertificateException("No trust manager accepted the chain", lastError)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        var lastError: Exception? = null
        for (tm in delegates) {
            try {
                tm.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                lastError = e
            } catch (e: IllegalArgumentException) {
                lastError = e
            }
        }
        throw CertificateException("No trust manager accepted the chain", lastError)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegates.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
}
