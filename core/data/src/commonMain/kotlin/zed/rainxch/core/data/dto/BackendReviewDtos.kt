package zed.rainxch.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class BackendReviewsPageDto(
    val summary: BackendRatingSummaryDto,
    val reviews: List<BackendReviewDto> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
)

@Serializable
data class BackendRatingSummaryDto(
    val average: Double? = null,
    val count: Int = 0,
    val histogram: Map<String, Int> = emptyMap(),
)

@Serializable
data class BackendReviewDto(
    val id: String,
    val rating: Int,
    val text: String? = null,
    val version: String? = null,
    val platform: String? = null,
    val appLabel: String? = null,
    val author: BackendReviewAuthorDto,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val edited: Boolean = false,
    val score: Int = 0,
    val hidden: Boolean = false,
    val reply: BackendReviewReplyDto? = null,
)

@Serializable
data class BackendReviewAuthorDto(
    val id: Long,
    val login: String,
    val avatarUrl: String? = null,
)

@Serializable
data class BackendReviewReplyDto(
    val author: BackendReviewAuthorDto,
    val text: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class BackendRepoRatingDto(
    val average: Double,
    val count: Int,
)

@Serializable
data class BackendReviewSummariesDto(
    val summaries: Map<String, BackendRepoRatingDto> = emptyMap(),
)

@Serializable
data class BackendMyReviewDto(
    val review: BackendReviewDto? = null,
    val votes: Map<String, Int> = emptyMap(),
    val canReview: Boolean = false,
    val cannotReviewReason: String? = null,
    val canReply: Boolean = false,
    val isModerator: Boolean = false,
)

@Serializable
data class BackendReviewDraftDto(
    val rating: Int,
    val text: String? = null,
    val version: String? = null,
    val platform: String? = null,
    val appLabel: String? = null,
)

@Serializable
data class BackendReviewReportDto(
    val reason: String,
    val note: String? = null,
)

@Serializable
data class BackendReviewVoteDto(
    val value: Int,
)

@Serializable
data class BackendReviewReplyRequestDto(
    val text: String,
)

@Serializable
data class BackendModerationQueueDto(
    val items: List<BackendModerationItemDto> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
)

@Serializable
data class BackendModerationItemDto(
    val review: BackendReviewDto,
    val repo: BackendModerationRepoDto,
    val reportCount: Int = 0,
    val reports: List<BackendModerationReportDto> = emptyList(),
    val lastReportedAt: String? = null,
    val hidden: Boolean = false,
    val hiddenReason: String? = null,
)

@Serializable
data class BackendModerationRepoDto(
    val host: String? = null,
    val owner: String,
    val name: String,
)

@Serializable
data class BackendModerationReportDto(
    val reason: String? = null,
    val note: String? = null,
    val createdAt: String? = null,
)
