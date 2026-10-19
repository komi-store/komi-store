package zed.rainxch.core.domain.model.installation

data class ManifestHint(
    val owner: String,
    val repo: String,
    val source: ManifestHintSource,
    val confidence: Double,
)
