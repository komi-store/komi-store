package zed.rainxch.githubstore.utils

import android.app.Activity
import zed.rainxch.core.domain.repository.SYSTEM_DEFAULT_DISPLAY_MODE_ID
import zed.rainxch.core.presentation.utils.DisplayModeOption
import zed.rainxch.core.presentation.utils.displayModeOptions

internal fun Activity.displayModes(): List<DisplayModeOption> = displayModeOptions(this)

// A stored mode id the current display no longer reports would be ignored or refused, so an
// unknown value falls back to the system default rather than being applied blindly.
internal fun Activity.applyDisplayModePreference(modeId: Int) {
    val target =
        displayModes().firstOrNull { it.id == modeId }?.id
            ?: SYSTEM_DEFAULT_DISPLAY_MODE_ID
    if (window.attributes.preferredDisplayModeId == target) return
    window.attributes = window.attributes.apply { preferredDisplayModeId = target }
}
