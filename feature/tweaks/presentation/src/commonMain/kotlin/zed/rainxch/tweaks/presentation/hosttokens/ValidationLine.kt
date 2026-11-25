package zed.rainxch.tweaks.presentation.hosttokens

import kotlinx.collections.immutable.ImmutableList

data class ValidationLine(
    val login: String?,
    val scopes: ImmutableList<String>,
    val rateLimitRemaining: Int?,
    val errorMessage: String?,
) {
    val isSuccess: Boolean get() = errorMessage == null
}
