package zed.rainxch.auth.presentation.mapper

import zed.rainxch.auth.presentation.model.GithubDeviceStartUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevicePromptMapperTest {
    private val start =
        GithubDeviceStartUi(
            deviceCode = "device",
            userCode = "2102-UHHUF",
            verificationUri = "https://github.com/login/device",
            expiresInSec = 900,
        )

    @Test
    fun the_timer_shows_minutes_and_seconds() {
        val prompt = start.toDevicePrompt(remainingSeconds = 847)

        assertEquals("14:07", prompt.formattedTimer)
        assertEquals(847f / 900f, prompt.progressFraction)
        assertFalse(prompt.isUrgent)
    }

    @Test
    fun the_last_minute_is_urgent_but_zero_is_not() {
        assertTrue(start.toDevicePrompt(remainingSeconds = 59).isUrgent)
        assertFalse(start.toDevicePrompt(remainingSeconds = 0).isUrgent)
    }
}
