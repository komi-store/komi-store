package zed.rainxch.details.domain.model

// The chosen provider lacks the key, URL or target language it needs, so no request was sent.
class TranslationProviderNotConfiguredException(
    message: String,
) : Exception(message)
