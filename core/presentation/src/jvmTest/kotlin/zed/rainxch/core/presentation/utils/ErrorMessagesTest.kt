package zed.rainxch.core.presentation.utils

import kotlinx.coroutines.runBlocking
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RateLimitInfo
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.rate_limit_try_again
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ErrorMessagesTest {
    @Test
    fun a_rate_limit_shows_the_rate_limit_message() {
        val error = RateLimitException(RateLimitInfo(limit = 60, remaining = 0, resetTimestamp = 0))

        assertSame(Res.string.rate_limit_try_again, error.userMessageResource(Res.string.failed_to_load))
    }

    @Test
    fun an_error_with_a_message_keeps_it() =
        runBlocking {
            val error = Exception("Not Found")

            assertNull(error.userMessageResource(Res.string.failed_to_load))
            assertEquals("Not Found", error.toUserMessage(Res.string.failed_to_load))
        }

    @Test
    fun an_error_without_a_message_falls_back() {
        assertSame(Res.string.failed_to_load, Exception().userMessageResource(Res.string.failed_to_load))
    }
}
