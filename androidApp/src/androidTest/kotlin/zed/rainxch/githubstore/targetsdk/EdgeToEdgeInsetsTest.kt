package zed.rainxch.githubstore.targetsdk

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.MainActivity

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeInsetsTest : TargetSdk37TestBase() {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun contentStaysInsideTheReportedSystemBars() {
        rule.waitForTag(KomiTestTags.SCREEN_EXPLORE)
        rule.waitForIdle()

        val decor = rule.activity.window.decorView
        assertFalse("decor must not fit system windows", decor.fitsSystemWindows)

        var insets: WindowInsetsCompat? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            insets = ViewCompat.getRootWindowInsets(decor)
        }
        val bars = insets
        assertNotNull("root window insets must be available", bars)
        bars ?: return

        val statusTop = bars.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val navBottom = bars.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        assumeTrue("device must report a status bar inset", statusTop > 0)

        val topBar =
            rule
                .onNodeWithTag(KomiTestTags.TOP_BAR_CONTENT, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInWindow
        assertTrue(
            "top bar content is under the status bar: top=${topBar.top}, inset=$statusTop",
            topBar.top >= statusTop,
        )

        val label =
            rule
                .onNodeWithTag(KomiTestTags.navLabel("ExploreScreen"), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInWindow
        val limit = decor.height - navBottom
        assertTrue(
            "bottom bar label is under the navigation bar: bottom=${label.bottom}, limit=$limit",
            label.bottom <= limit,
        )
    }
}
