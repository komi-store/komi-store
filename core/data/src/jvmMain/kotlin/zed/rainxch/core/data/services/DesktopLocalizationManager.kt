package zed.rainxch.core.data.services

import java.util.Locale

class DesktopLocalizationManager : LocalizationManager {
    private val systemDefault: Locale = Locale.getDefault()

    override fun getCurrentLanguageCode(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        return if (country.isNotEmpty()) {
            "$language-$country"
        } else {
            language
        }
    }

    override fun getPrimaryLanguageCode(): String = Locale.getDefault().language

    override fun setActiveLanguageTag(tag: String?) {
        val normalized = tag?.trim().orEmpty()
        val target =
            if (normalized.isEmpty()) {
                systemDefault
            } else {
                Locale.forLanguageTag(normalized)
            }
        Locale.setDefault(target)
    }
}
