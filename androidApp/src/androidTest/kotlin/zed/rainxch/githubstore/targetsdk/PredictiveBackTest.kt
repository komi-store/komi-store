package zed.rainxch.githubstore.targetsdk

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.MainActivity

@RunWith(AndroidJUnit4::class)
class PredictiveBackTest : TargetSdk37TestBase() {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemBackPopsTheNavStackInsteadOfFinishingTheApp() {
        rule.waitForTag(KomiTestTags.SCREEN_EXPLORE)
        rule.waitForIdle()
        rule.dismissBlockingOverlays()

        rule
            .onNodeWithTag(KomiTestTags.navItem("AppsScreen"), useUnmergedTree = true)
            .performClick()

        rule.waitUntil(20_000) {
            !rule.hasTag(KomiTestTags.SCREEN_EXPLORE)
        }
        rule.waitForIdle()

        var hasBackCallback = false
        rule.activityRule.scenario.onActivity { activity ->
            hasBackCallback = activity.onBackPressedDispatcher.hasEnabledCallbacks()
        }
        assertTrue("a pushed destination must have an enabled back callback", hasBackCallback)

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        rule.waitForTag(KomiTestTags.SCREEN_EXPLORE)
        rule.waitForIdle()

        assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
        assertFalse("back must not finish the activity", rule.activity.isFinishing)
    }
}
