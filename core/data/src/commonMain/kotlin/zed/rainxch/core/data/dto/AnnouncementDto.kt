package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementDto(
    @SerialName("id") val id: String,
    @SerialName("publishedAt") val publishedAt: String,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("severity") val severity: String,
    @SerialName("category") val category: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("ctaUrl") val ctaUrl: String? = null,
    @SerialName("ctaLabel") val ctaLabel: String? = null,
    @SerialName("dismissible") val dismissible: Boolean = true,
    @SerialName("requiresAcknowledgment") val requiresAcknowledgment: Boolean = false,
    @SerialName("minVersionCode") val minVersionCode: Int? = null,
    @SerialName("maxVersionCode") val maxVersionCode: Int? = null,
    @SerialName("platforms") val platforms: List<String>? = null,
    @SerialName("installerTypes") val installerTypes: List<String>? = null,
    @SerialName("iconHint") val iconHint: String? = null,
    @SerialName("i18n") val i18n: Map<String, AnnouncementLocaleDto> = emptyMap(),
)
