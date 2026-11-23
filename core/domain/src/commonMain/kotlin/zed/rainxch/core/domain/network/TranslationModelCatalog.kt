package zed.rainxch.core.domain.network

interface TranslationModelCatalog {
    suspend fun listModels(
        baseUrl: String,
        apiKey: String?,
        allowInsecureHttp: Boolean = false,
    ): List<String>
}
