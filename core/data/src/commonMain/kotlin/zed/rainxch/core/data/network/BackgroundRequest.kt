package zed.rainxch.core.data.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class BackgroundRequest : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BackgroundRequest>
}

// Same marker on every install, no identifiers. The User-Agent copy is what Cloudflare
// analytics can filter on; it can't filter on custom headers.
internal val BackgroundRequestMarker = createClientPlugin("BackgroundRequestMarker") {
    onRequest { request, _ ->
        if (currentCoroutineContext()[BackgroundRequest] != null) {
            request.headers.append(BACKGROUND_HEADER, "1")
            request.headers[HttpHeaders.UserAgent] = BACKGROUND_USER_AGENT
        }
    }
}

private const val BACKGROUND_HEADER = "X-Komi-Background"
private const val BACKGROUND_USER_AGENT = "KomiStore-Background"
