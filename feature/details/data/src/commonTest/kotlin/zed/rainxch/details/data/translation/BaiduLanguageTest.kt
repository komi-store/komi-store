package zed.rainxch.details.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BaiduLanguageTest {
    @Test
    fun maps_every_supported_language_to_a_baidu_code() {
        val expected =
            mapOf(
                "ar" to "ara",
                "bn" to "ben",
                "zh-CN" to "zh",
                "zh-TW" to "cht",
                "cs" to "cs",
                "da" to "dan",
                "nl" to "nl",
                "en" to "en",
                "fi" to "fin",
                "fr" to "fra",
                "de" to "de",
                "el" to "el",
                "he" to "he",
                "hi" to "hi",
                "hu" to "hu",
                "id" to "id",
                "it" to "it",
                "ja" to "jp",
                "ko" to "kor",
                "ms" to "may",
                "no" to "nor",
                "pl" to "pl",
                "pt-BR" to "pt",
                "ro" to "rom",
                "ru" to "ru",
                "es" to "spa",
                "sv" to "swe",
                "th" to "th",
                "tr" to "tr",
                "uk" to "uk",
                "uz" to "uz",
                "vi" to "vie",
            )

        expected.forEach { (appCode, baiduCode) ->
            assertEquals(baiduCode, baiduLanguageCode(appCode, allowAuto = false), appCode)
        }
    }

    @Test
    fun source_language_can_be_auto_but_target_cannot() {
        assertEquals("auto", baiduLanguageCode("auto", allowAuto = true))
        assertFailsWith<TranslationUnsupportedLanguageException> {
            baiduLanguageCode("auto", allowAuto = false)
        }
    }

    @Test
    fun unknown_language_fails_explicitly() {
        assertFailsWith<TranslationUnsupportedLanguageException> {
            baiduLanguageCode("xx", allowAuto = false)
        }
    }

    @Test
    fun maps_detected_baidu_codes_back_to_app_codes() {
        assertEquals("ja", baiduLanguageFromCode("jp"))
        assertEquals("ko", baiduLanguageFromCode("kor"))
        assertEquals("zh-TW", baiduLanguageFromCode("cht"))
        assertEquals("ms", baiduLanguageFromCode("may"))
    }
}
