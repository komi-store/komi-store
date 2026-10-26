package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.repository.tweaks.AnnouncementTweaks
import zed.rainxch.core.domain.repository.tweaks.DiscoveryTweaks
import zed.rainxch.core.domain.repository.tweaks.DisplayTweaks
import zed.rainxch.core.domain.repository.tweaks.ExternalImportTweaks
import zed.rainxch.core.domain.repository.tweaks.ForgeHostTweaks
import zed.rainxch.core.domain.repository.tweaks.HintTweaks
import zed.rainxch.core.domain.repository.tweaks.InstallerTweaks
import zed.rainxch.core.domain.repository.tweaks.LanguageTweaks
import zed.rainxch.core.domain.repository.tweaks.LibreTranslateTweaks
import zed.rainxch.core.domain.repository.tweaks.SortRuleTweaks
import zed.rainxch.core.domain.repository.tweaks.ThemeTweaks
import zed.rainxch.core.domain.repository.tweaks.TranslatorKeyTweaks
import zed.rainxch.core.domain.repository.tweaks.UpdateTweaks

interface TweaksRepository :
    ThemeTweaks,
    DisplayTweaks,
    InstallerTweaks,
    UpdateTweaks,
    DiscoveryTweaks,
    LanguageTweaks,
    TranslatorKeyTweaks,
    LibreTranslateTweaks,
    ExternalImportTweaks,
    HintTweaks,
    AnnouncementTweaks,
    SortRuleTweaks,
    ForgeHostTweaks

// Matches WindowManager.LayoutParams.preferredDisplayModeId: 0 leaves the system in charge.
const val SYSTEM_DEFAULT_DISPLAY_MODE_ID = 0
