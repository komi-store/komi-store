package zed.rainxch.core.presentation.utils

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.rate_limit_try_again

suspend fun Throwable.toUserMessage(fallback: StringResource): String =
    userMessageResource(fallback)?.let { getString(it) } ?: message.orEmpty()

internal fun Throwable.userMessageResource(fallback: StringResource): StringResource? =
    when {
        this is RateLimitException -> Res.string.rate_limit_try_again
        message == null -> fallback
        else -> null
    }
