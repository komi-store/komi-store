package zed.rainxch.core.domain.model.reviews

import kotlinx.serialization.Serializable

// The full rating of one repo: shown on its Details page and the Reviews screen.
data class RatingSummary(
    val average: Double?,
    val count: Int,
    val histogram: Map<Int, Int>,
)

// The short form carried on catalog repos and Library rows, for the stars on cards.
@Serializable
data class RepoRating(
    val average: Double,
    val count: Int,
)
