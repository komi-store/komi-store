package zed.rainxch.repopages.data.mappers

import zed.rainxch.repopages.data.dto.IssueCommentDto
import zed.rainxch.repopages.data.dto.IssueDto
import zed.rainxch.repopages.data.dto.LabelDto
import zed.rainxch.repopages.data.dto.PullRequestDto
import zed.rainxch.repopages.data.dto.SecurityAdvisoryDto
import zed.rainxch.repopages.domain.model.AdvisorySeverity
import zed.rainxch.repopages.domain.model.IssueComment
import zed.rainxch.repopages.domain.model.IssueLabel
import zed.rainxch.repopages.domain.model.IssueState
import zed.rainxch.repopages.domain.model.PullRequestState
import zed.rainxch.repopages.domain.model.RepoIssue
import zed.rainxch.repopages.domain.model.RepoPullRequest
import zed.rainxch.repopages.domain.model.SecurityAdvisory

fun IssueDto.toRepoIssue(): RepoIssue =
    RepoIssue(
        issueId = number,
        title = title,
        state = state.toIssueState(),
        authorLogin = user?.login.orEmpty(),
        authorAvatarUrl = user?.avatarUrl,
        commentCount = comments,
        createdAt = createdAt,
        labels = labels.map { it.toIssueLabel() },
    )

fun LabelDto.toIssueLabel(): IssueLabel = IssueLabel(name = name, color = color)

fun IssueCommentDto.toIssueComment(): IssueComment =
    IssueComment(
        id = id,
        authorLogin = user?.login.orEmpty(),
        authorAvatarUrl = user?.avatarUrl,
        bodyMarkdown = body.orEmpty(),
        createdAt = createdAt,
        reactionThumbsUp = reactions?.plusOne ?: 0,
    )

fun PullRequestDto.toRepoPullRequest(): RepoPullRequest =
    RepoPullRequest(
        number = number,
        title = title,
        state =
            when {
                mergedAt != null -> PullRequestState.MERGED
                state.lowercase() == "closed" -> PullRequestState.CLOSED
                else -> PullRequestState.OPEN
            },
        authorLogin = user?.login.orEmpty(),
        authorAvatarUrl = user?.avatarUrl,
        isDraft = draft,
        commentCount = comments,
        createdAt = createdAt,
        labels = labels.map { it.toIssueLabel() },
    )

fun SecurityAdvisoryDto.toSecurityAdvisory(): SecurityAdvisory =
    SecurityAdvisory(
        ghsaId = ghsaId,
        summary = summary,
        description = description,
        severity = AdvisorySeverity.fromApi(severity),
        cveId = cveId,
        publishedAt = publishedAt,
        htmlUrl = htmlUrl,
    )

fun String.toIssueState(): IssueState =
    when (lowercase()) {
        "closed" -> IssueState.CLOSED
        else -> IssueState.OPEN
    }

fun IssueState.toApiValue(): String =
    when (this) {
        IssueState.OPEN -> "open"
        IssueState.CLOSED -> "closed"
    }
