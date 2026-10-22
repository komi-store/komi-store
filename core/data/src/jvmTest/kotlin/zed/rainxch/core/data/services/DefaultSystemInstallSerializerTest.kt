package zed.rainxch.core.data.services

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

class DefaultSystemInstallSerializerTest {
    @Test
    fun a_resume_releases_a_cancelled_handoff_so_the_next_install_does_not_wait() =
        runBlocking {
            val serializer = DefaultSystemInstallSerializer()
            serializer.awaitFreeAndMarkPending("com.example.cancelled", timeoutMs = 0L)

            serializer.onAppResumed()

            withTimeout(1_000L) { serializer.awaitFreeAndMarkPending("com.example.next") }
        }
}
