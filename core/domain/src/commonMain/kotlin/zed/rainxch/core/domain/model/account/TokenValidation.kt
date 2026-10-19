package zed.rainxch.core.domain.model.account

data class TokenValidation(
    val login: String?,
    val scopes: List<String>,
    val rateLimitRemaining: Int?,
)
