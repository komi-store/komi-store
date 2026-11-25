package zed.rainxch.details.data.utils

internal fun detectReadmeLanguage(content: String): String? {
    val sample = content.take(LANGUAGE_SAMPLE_CHARS)
    val sampleLower = sample.lowercase()
    val minScriptChars = sample.length * SCRIPT_SHARE_THRESHOLD
    return when {
        sample.count { it in '一'..'鿿' } > minScriptChars -> "zh"
        sample.count { it in '぀'..'ゟ' || it in '゠'..'ヿ' } > minScriptChars -> "ja"
        sample.count { it in '가'..'힯' } > minScriptChars -> "ko"
        sample.count { it in '؀'..'ۿ' } > minScriptChars -> "ar"
        sample.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' } > minScriptChars -> "ru"
        ENGLISH_INDICATORS.count { it.containsMatchIn(sampleLower) } >= MIN_ENGLISH_MATCHES -> "en"
        else -> null
    }
}

private const val LANGUAGE_SAMPLE_CHARS = 1000
private const val SCRIPT_SHARE_THRESHOLD = 0.15
private const val MIN_ENGLISH_MATCHES = 4

private val ENGLISH_INDICATORS =
    listOf("the", "and", "for", "with", "this", "that", "from", "are", "was", "have", "has", "will", "you", "can", "use", "install")
        .map { Regex("""\b$it\b""", RegexOption.IGNORE_CASE) }
