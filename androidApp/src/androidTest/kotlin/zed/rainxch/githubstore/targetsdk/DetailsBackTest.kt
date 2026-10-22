package zed.rainxch.githubstore.targetsdk

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import zed.rainxch.githubstore.MainActivity

// Starts through the real deep-link entry point and avoids Compose idling: Details shows an indeterminate
// progress indicator while loading, which would keep a Compose test rule busy.
@RunWith(AndroidJUnit4::class)
class DetailsBackTest : TargetSdk37TestBase() {
    @get:Rule
    val scenarioRule =
        ActivityScenarioRule<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("githubstore://repo/komi-store/komi-store")
            },
        )

    @Test
    fun systemBackFromDeepLinkedDetailsKeepsTheAppInTheForeground() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(
            "app window did not appear",
            device.wait(Until.hasObject(By.pkg(context.packageName)), 20_000),
        )
        SystemClock.sleep(2_000)

        var hasBackCallback = false
        scenarioRule.scenario.onActivity { activity ->
            hasBackCallback = activity.onBackPressedDispatcher.hasEnabledCallbacks()
        }
        assertTrue("a pushed details destination must have an enabled back callback", hasBackCallback)

        device.pressBack()
        SystemClock.sleep(2_000)

        assertEquals(
            "back from details must not leave the app",
            context.packageName,
            device.currentPackageName,
        )
        scenarioRule.scenario.onActivity { activity ->
            assertFalse("back must not finish the activity", activity.isFinishing)
        }
        assertEquals(Lifecycle.State.RESUMED, scenarioRule.scenario.state)
    }
}
