package zed.rainxch.details.data.model

import kotlinx.serialization.Serializable

@Serializable
internal data class CachedReadme(
    val content: String,
    val languageCode: String?,
    val path: String,
) {
    fun toTriple(): Triple<String, String?, String> = Triple(content, languageCode, path)
}

internal fun Triple<String, String?, String>.toCachedReadme(): CachedReadme =
    CachedReadme(content = first, languageCode = second, path = third)
