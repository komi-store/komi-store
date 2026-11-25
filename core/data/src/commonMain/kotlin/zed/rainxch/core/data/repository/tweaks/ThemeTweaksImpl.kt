package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.repository.tweaks.ThemeTweaks

internal class ThemeTweaksImpl(
    private val store: TweakStore,
) : ThemeTweaks {
    override fun getThemeColor(): Flow<AppTheme> = store.gatedGetFlow(K_THEME, "").map { AppTheme.fromName(it.ifEmpty { null }) }

    override suspend fun setThemeColor(theme: AppTheme) {
        store.awaitMigration()
        store.ksafe.safePut(K_THEME, theme.name)
    }

    override fun getIsDarkTheme(): Flow<Boolean?> = store.gatedGetFlow<Boolean?>(K_IS_DARK, null)

    override suspend fun setDarkTheme(isDarkTheme: Boolean?) {
        store.awaitMigration()
        if (isDarkTheme == null) store.ksafe.safeDelete(K_IS_DARK) else store.ksafe.safePut(K_IS_DARK, isDarkTheme)
    }

    override fun getAmoledTheme(): Flow<Boolean> = store.gatedGetFlow(K_AMOLED, false)

    override suspend fun setAmoledTheme(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_AMOLED, enabled)
    }

    override fun getMangaPaper(): Flow<MangaPaperId> =
        store.gatedGetFlow(K_MANGA_PAPER, "").map { MangaPaperId.fromName(it.ifEmpty { null }) }

    override suspend fun setMangaPaper(paper: MangaPaperId) {
        store.awaitMigration()
        store.ksafe.safePut(K_MANGA_PAPER, paper.name)
    }

    override fun getAccentId(): Flow<AccentId> = store.gatedGetFlow(K_ACCENT, "").map { AccentId.fromName(it.ifEmpty { null }) }

    override suspend fun setAccentId(accentId: AccentId) {
        store.awaitMigration()
        store.ksafe.safePut(K_ACCENT, accentId.name)
    }
}
