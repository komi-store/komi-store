package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.MangaPaperId

interface ThemeTweaks {
    fun getThemeColor(): Flow<AppTheme>

    suspend fun setThemeColor(theme: AppTheme)

    fun getIsDarkTheme(): Flow<Boolean?>

    suspend fun setDarkTheme(isDarkTheme: Boolean?)

    fun getAmoledTheme(): Flow<Boolean>

    suspend fun setAmoledTheme(enabled: Boolean)

    fun getMangaPaper(): Flow<MangaPaperId>

    suspend fun setMangaPaper(paper: MangaPaperId)

    fun getAccentId(): Flow<AccentId>

    suspend fun setAccentId(accentId: AccentId)
}
