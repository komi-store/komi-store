package zed.rainxch.githubstore.targetsdk

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Before
import zed.rainxch.core.presentation.testing.KomiTestTags

abstract class TargetSdk37TestBase {
    protected val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun requireAndroid17() {
        assumeTrue("requires Android 17 (API 37)", Build.VERSION.SDK_INT >= 37)
        assumeTrue(
            "activates once the app targets SDK 37",
            context.applicationInfo.targetSdkVersion >= 37,
        )
    }

    protected fun ComposeTestRule.waitForTag(
        tag: String,
        timeoutMs: Long = 20_000,
    ) {
        waitUntil(timeoutMs) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun ComposeTestRule.hasTag(tag: String): Boolean =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun ComposeTestRule.dismissBlockingOverlays(timeoutMs: Long = 5_000) {
        listOf(KomiTestTags.WHATS_NEW_DISMISS, KomiTestTags.RATE_LIMIT_DISMISS).forEach { tag ->
            if (hasTag(tag)) {
                onNodeWithTag(tag, useUnmergedTree = true).performClick()
                waitUntil(timeoutMs) { !hasTag(tag) }
            }
        }
    }
}
