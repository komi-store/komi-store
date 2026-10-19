package zed.rainxch.repopages.domain.model

data class SecurityAdvisory(
    val ghsaId: String,
    val summary: String,
    val description: String?,
    val severity: AdvisorySeverity,
    val cveId: String?,
    val publishedAt: String?,
    val htmlUrl: String?,
)
