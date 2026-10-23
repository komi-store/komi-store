package zed.rainxch.details.presentation.utils

internal fun autoTranslateTargetLanguage(
    explicitTarget: String?,
    appLanguage: String?,
    deviceLanguage: String?,
): String? =
    sequenceOf(explicitTarget, appLanguage, deviceLanguage)
        .mapNotNull { it?.trim() }
        .firstOrNull { it.isNotEmpty() }

internal fun shouldAutoTranslate(
    text: String?,
    targetLanguage: String?,
    sourceLanguage: String?,
    alreadyTranslated: Boolean,
): Boolean {
    val target = targetLanguage?.trim().orEmpty()
    val source = sourceLanguage?.trim().orEmpty()
    val changesLanguage =
        target.isNotEmpty() && (source.isEmpty() || !source.equals(target, ignoreCase = true))
    return !alreadyTranslated && !text.isNullOrBlank() && changesLanguage
}
