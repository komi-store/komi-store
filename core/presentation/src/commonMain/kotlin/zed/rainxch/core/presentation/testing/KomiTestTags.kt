package zed.rainxch.core.presentation.testing

// Anchors for the instrumented tests in androidApp/src/androidTest
object KomiTestTags {
    const val SCREEN_EXPLORE = "screen_explore"
    const val TOP_BAR_CONTENT = "top_bar_content"
    const val WHATS_NEW_DISMISS = "whats_new_dismiss"
    const val RATE_LIMIT_DISMISS = "rate_limit_dismiss"
    const val CLASSIC_TAB_CARD = "classic_tab_card"
    const val DISCOVERY_CHIPS = "discovery_chips"

    fun navItem(id: String): String = "nav_item_$id"

    fun navLabel(id: String): String = "nav_label_$id"
}
