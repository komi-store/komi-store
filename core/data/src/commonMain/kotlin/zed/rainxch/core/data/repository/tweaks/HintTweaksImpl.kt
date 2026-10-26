package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.HintTweaks

internal class HintTweaksImpl(
    private val store: TweakStore,
) : HintTweaks {
    override fun getApkInspectCoachmarkShown(): Flow<Boolean> = store.gatedGetFlow(K_APK_INSPECT_COACHMARK_SHOWN, false)

    override suspend fun setApkInspectCoachmarkShown(shown: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_APK_INSPECT_COACHMARK_SHOWN, shown)
    }

    override fun getChannelChipCoachmarkShown(): Flow<Boolean> = store.gatedGetFlow(K_CHANNEL_CHIP_COACHMARK_SHOWN, false)

    override suspend fun setChannelChipCoachmarkShown(shown: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_CHANNEL_CHIP_COACHMARK_SHOWN, shown)
    }

    override fun getBatteryOptimizationPromptDismissed(): Flow<Boolean> = store.gatedGetFlow(K_BATTERY_OPT_PROMPT_DISMISSED, false)

    override suspend fun setBatteryOptimizationPromptDismissed(dismissed: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_BATTERY_OPT_PROMPT_DISMISSED, dismissed)
    }

    override fun getLastSeenWhatsNewVersionCode(): Flow<Int?> = store.gatedGetFlow<Int?>(K_LAST_SEEN_WHATS_NEW_VERSION_CODE, null)

    override suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int) {
        store.awaitMigration()
        store.rmwLock.withLock {
            val current = store.ksafe.safeGet<Int?>(K_LAST_SEEN_WHATS_NEW_VERSION_CODE, null) ?: Int.MIN_VALUE
            if (versionCode > current) {
                store.ksafe.safePut(K_LAST_SEEN_WHATS_NEW_VERSION_CODE, versionCode)
            }
        }
    }
}
