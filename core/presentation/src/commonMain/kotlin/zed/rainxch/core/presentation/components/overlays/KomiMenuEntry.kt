package zed.rainxch.core.presentation.components.overlays

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

sealed interface KomiMenuEntry

@Immutable
data class KomiMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val subtitle: String? = null,
    val tone: KomiMenuTone = KomiMenuTone.Default,
    val enabled: Boolean = true,
    // An on/off option: a check shows while it's on, and screen readers hear the state.
    val checked: Boolean? = null,
) : KomiMenuEntry

data object KomiMenuDivider : KomiMenuEntry
