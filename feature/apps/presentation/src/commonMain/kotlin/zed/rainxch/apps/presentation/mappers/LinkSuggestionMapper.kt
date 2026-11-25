package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.presentation.model.LinkSuggestionUi
import zed.rainxch.core.domain.system.RepoMatchSuggestion

private const val PERCENT = 100

fun RepoMatchSuggestion.toLinkSuggestionUi(): LinkSuggestionUi =
    LinkSuggestionUi(
        owner = owner,
        repo = repo,
        confidencePercent = (confidence * PERCENT).toInt(),
        source = source,
        stars = stars,
        description = description,
        sourceHost = sourceHost,
    )
