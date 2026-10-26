package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.anifantakis.lib.ksafe.KSafe
import zed.rainxch.core.data.repository.tweaks.AnnouncementTweaksImpl
import zed.rainxch.core.data.repository.tweaks.DiscoveryTweaksImpl
import zed.rainxch.core.data.repository.tweaks.DisplayTweaksImpl
import zed.rainxch.core.data.repository.tweaks.ExternalImportTweaksImpl
import zed.rainxch.core.data.repository.tweaks.ForgeHostTweaksImpl
import zed.rainxch.core.data.repository.tweaks.HintTweaksImpl
import zed.rainxch.core.data.repository.tweaks.InstallerTweaksImpl
import zed.rainxch.core.data.repository.tweaks.LanguageTweaksImpl
import zed.rainxch.core.data.repository.tweaks.LibreTranslateTweaksImpl
import zed.rainxch.core.data.repository.tweaks.SortRuleTweaksImpl
import zed.rainxch.core.data.repository.tweaks.ThemeTweaksImpl
import zed.rainxch.core.data.repository.tweaks.TranslatorKeyTweaksImpl
import zed.rainxch.core.data.repository.tweaks.TweakStore
import zed.rainxch.core.data.repository.tweaks.UpdateTweaksImpl
import zed.rainxch.core.domain.repository.TweaksRepository
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

class TweaksRepositoryImpl private constructor(
    store: TweakStore,
) : TweaksRepository,
    ThemeTweaks by ThemeTweaksImpl(store),
    DisplayTweaks by DisplayTweaksImpl(store),
    InstallerTweaks by InstallerTweaksImpl(store),
    UpdateTweaks by UpdateTweaksImpl(store),
    DiscoveryTweaks by DiscoveryTweaksImpl(store),
    LanguageTweaks by LanguageTweaksImpl(store),
    TranslatorKeyTweaks by TranslatorKeyTweaksImpl(store),
    LibreTranslateTweaks by LibreTranslateTweaksImpl(store),
    ExternalImportTweaks by ExternalImportTweaksImpl(store),
    HintTweaks by HintTweaksImpl(store),
    AnnouncementTweaks by AnnouncementTweaksImpl(store),
    SortRuleTweaks by SortRuleTweaksImpl(store),
    ForgeHostTweaks by ForgeHostTweaksImpl(store) {
    constructor(
        ksafe: KSafe,
        legacyDataStore: DataStore<Preferences>,
    ) : this(TweakStore(ksafe, legacyDataStore))
}
