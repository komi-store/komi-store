package zed.rainxch.core.presentation.components.lists

import androidx.compose.runtime.Immutable

@Immutable
sealed interface KomiListTrailing {
    data object Chevron : KomiListTrailing

    data object None : KomiListTrailing

    data object UnreadDot : KomiListTrailing

    data class Value(
        val text: String,
    ) : KomiListTrailing

    data class Badge(
        val count: Int,
    ) : KomiListTrailing

    data class Toggle(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : KomiListTrailing
}
