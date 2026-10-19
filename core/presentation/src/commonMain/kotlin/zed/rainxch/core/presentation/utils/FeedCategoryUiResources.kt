package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.FeedCategory
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.category_all
import zed.rainxch.githubstore.core.presentation.res.feed_category_ai
import zed.rainxch.githubstore.core.presentation.res.feed_category_media
import zed.rainxch.githubstore.core.presentation.res.feed_category_networking
import zed.rainxch.githubstore.core.presentation.res.feed_category_privacy
import zed.rainxch.githubstore.core.presentation.res.feed_category_reading
import zed.rainxch.githubstore.core.presentation.res.feed_category_social
import zed.rainxch.githubstore.core.presentation.res.feed_category_tools

@Composable
fun FeedCategory.toLabel(): String =
    stringResource(
        when (this) {
            FeedCategory.All -> Res.string.category_all
            FeedCategory.Ai -> Res.string.feed_category_ai
            FeedCategory.Privacy -> Res.string.feed_category_privacy
            FeedCategory.Networking -> Res.string.feed_category_networking
            FeedCategory.Media -> Res.string.feed_category_media
            FeedCategory.Social -> Res.string.feed_category_social
            FeedCategory.Reading -> Res.string.feed_category_reading
            FeedCategory.Tools -> Res.string.feed_category_tools
        },
    )
