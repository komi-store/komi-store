package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.FontTheme

interface DisplayTweaks {
    fun getFontTheme(): Flow<FontTheme>

    suspend fun setFontTheme(fontTheme: FontTheme)

    fun getPersonality(): Flow<AppPersonality>

    suspend fun setPersonality(personality: AppPersonality)

    fun getScrollbarEnabled(): Flow<Boolean>

    suspend fun setScrollbarEnabled(enabled: Boolean)

    fun getDisplayModeId(): Flow<Int>

    suspend fun setDisplayModeId(modeId: Int)

    fun getContentWidth(): Flow<ContentWidth>

    suspend fun setContentWidth(width: ContentWidth)
}
