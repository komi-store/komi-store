package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.repository.SYSTEM_DEFAULT_DISPLAY_MODE_ID
import zed.rainxch.core.domain.repository.tweaks.DisplayTweaks

internal class DisplayTweaksImpl(
    private val store: TweakStore,
) : DisplayTweaks {
    override fun getFontTheme(): Flow<FontTheme> = store.gatedGetFlow(K_FONT, "").map { FontTheme.fromName(it.ifEmpty { null }) }

    override suspend fun setFontTheme(fontTheme: FontTheme) {
        store.awaitMigration()
        store.ksafe.safePut(K_FONT, fontTheme.name)
    }

    override fun getPersonality(): Flow<AppPersonality> =
        store.gatedGetFlow(K_PERSONALITY, "").map { AppPersonality.fromName(it.ifEmpty { null }) }

    override suspend fun setPersonality(personality: AppPersonality) {
        store.awaitMigration()
        store.ksafe.safePut(K_PERSONALITY, personality.name)
    }

    override fun getScrollbarEnabled(): Flow<Boolean> = store.gatedGetFlow(K_SCROLLBAR_ENABLED, false)

    override suspend fun setScrollbarEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_SCROLLBAR_ENABLED, enabled)
    }

    override fun getDisplayModeId(): Flow<Int> = store.gatedGetFlow(K_DISPLAY_MODE_ID, SYSTEM_DEFAULT_DISPLAY_MODE_ID)

    override suspend fun setDisplayModeId(modeId: Int) {
        store.awaitMigration()
        store.ksafe.safePut(K_DISPLAY_MODE_ID, modeId)
    }

    override fun getContentWidth(): Flow<ContentWidth> =
        store.gatedGetFlow(K_CONTENT_WIDTH, "").map { ContentWidth.fromName(it.ifEmpty { null }) }

    override suspend fun setContentWidth(width: ContentWidth) {
        store.awaitMigration()
        store.ksafe.safePut(K_CONTENT_WIDTH, width.name)
    }
}
