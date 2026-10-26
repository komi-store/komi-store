package zed.rainxch.core.data.network

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.http.BadContentTypeFormatException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

// How a Ktor call fails short of cancellation: network, a closed client, a URL or header OkHttp rejects, a body that won't decode.
@PublishedApi
internal inline fun <T> recoverHttpFailure(
    recover: (Exception) -> T,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        recover(e)
    } catch (e: IllegalStateException) {
        recover(e)
    } catch (e: IllegalArgumentException) {
        recover(e)
    } catch (e: ContentConvertException) {
        recover(e)
    } catch (e: NoTransformationFoundException) {
        recover(e)
    } catch (e: BadContentTypeFormatException) {
        recover(e)
    }
