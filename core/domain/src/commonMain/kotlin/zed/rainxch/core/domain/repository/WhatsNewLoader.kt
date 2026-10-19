package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.model.announcement.WhatsNewEntry

interface WhatsNewLoader {
    suspend fun loadAll(languageTag: String? = null): List<WhatsNewEntry>

    suspend fun forVersionCode(
        versionCode: Int,
        languageTag: String? = null,
    ): WhatsNewEntry?
}
