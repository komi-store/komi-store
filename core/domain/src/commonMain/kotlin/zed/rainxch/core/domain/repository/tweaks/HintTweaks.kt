package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface HintTweaks {
    fun getApkInspectCoachmarkShown(): Flow<Boolean>

    suspend fun setApkInspectCoachmarkShown(shown: Boolean)

    fun getChannelChipCoachmarkShown(): Flow<Boolean>

    suspend fun setChannelChipCoachmarkShown(shown: Boolean)

    fun getBatteryOptimizationPromptDismissed(): Flow<Boolean>

    suspend fun setBatteryOptimizationPromptDismissed(dismissed: Boolean)

    fun getLastSeenWhatsNewVersionCode(): Flow<Int?>

    suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int)
}
