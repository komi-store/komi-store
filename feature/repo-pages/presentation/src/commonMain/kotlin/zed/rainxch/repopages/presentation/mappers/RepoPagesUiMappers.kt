package zed.rainxch.repopages.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.repopages.domain.model.IssueComment
import zed.rainxch.repopages.domain.model.IssueLabel
import zed.rainxch.repopages.domain.model.RepoIssue
import zed.rainxch.repopages.domain.model.RepoIssueDetail
import zed.rainxch.repopages.domain.model.RepoPullRequest
import zed.rainxch.repopages.domain.model.SecurityAdvisory
import zed.rainxch.repopages.domain.model.SecurityOverview
import zed.rainxch.repopages.presentation.model.IssueCommentUi
import zed.rainxch.repopages.presentation.model.IssueDetailUi
import zed.rainxch.repopages.presentation.model.IssueLabelUi
import zed.rainxch.repopages.presentation.model.RepoIssueUi
import zed.rainxch.repopages.presentation.model.RepoPullRequestUi
import zed.rainxch.repopages.presentation.model.SecurityAdvisoryUi
import zed.rainxch.repopages.presentation.model.SecurityOverviewUi
import zed.rainxch.repopages.presentation.utils.parseLabelColor

internal fun IssueLabel.toUi(): IssueLabelUi = IssueLabelUi(name = name, color = parseLabelColor(color))

internal fun RepoIssue.toUi(): RepoIssueUi =
    RepoIssueUi(
        issueId = issueId,
        title = title,
        state = state,
        authorLogin = authorLogin,
        labels = labels.map { it.toUi() }.toImmutableList(),
    )

internal fun RepoPullRequest.toUi(): RepoPullRequestUi =
    RepoPullRequestUi(
        number = number,
        title = title,
        state = state,
        authorLogin = authorLogin,
        isDraft = isDraft,
    )

internal fun IssueComment.toUi(): IssueCommentUi =
    IssueCommentUi(
        id = id,
        authorLogin = authorLogin,
        bodyMarkdown = bodyMarkdown,
        reactionThumbsUp = reactionThumbsUp,
    )

internal fun RepoIssueDetail.toUi(): IssueDetailUi =
    IssueDetailUi(
        number = number,
        title = title,
        authorLogin = authorLogin,
        bodyMarkdown = bodyMarkdown,
        reactionThumbsUp = reactionThumbsUp,
        comments = comments.map { it.toUi() }.toImmutableList(),
    )

internal fun SecurityAdvisory.toUi(): SecurityAdvisoryUi =
    SecurityAdvisoryUi(
        ghsaId = ghsaId,
        summary = summary,
        description = description,
        severity = severity,
        cveId = cveId,
        publishedAt = publishedAt,
    )

internal fun SecurityOverview.toUi(): SecurityOverviewUi =
    SecurityOverviewUi(
        advisories = advisories.map { it.toUi() }.toImmutableList(),
        securityPolicyMarkdown = securityPolicyMarkdown,
    )
