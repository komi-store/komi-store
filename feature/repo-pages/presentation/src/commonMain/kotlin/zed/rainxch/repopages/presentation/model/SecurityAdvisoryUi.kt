package zed.rainxch.repopages.presentation.model

import zed.rainxch.repopages.domain.model.AdvisorySeverity

data class SecurityAdvisoryUi(
    val ghsaId: String,
    val summary: String,
    val description: String?,
    val severity: AdvisorySeverity,
    val cveId: String?,
    val publishedAt: String?,
)
