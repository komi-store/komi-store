package zed.rainxch.core.data.repository.tweaks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import zed.rainxch.core.data.secure.MigrationEntry
import zed.rainxch.core.data.secure.migrateDataStoreToKSafe
import zed.rainxch.core.data.secure.safeGetFlow

// The one KSafe store every tweak role shares, so they all wait on the same DataStore migration and lock.
internal class TweakStore(
    val ksafe: KSafe,
    legacyDataStore: DataStore<Preferences>,
) {
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val migrationDeferred = CompletableDeferred<Unit>()
    val rmwLock = Mutex()

    init {
        migrationScope.launch {
            try {
                migrateDataStoreToKSafe(
                    legacy = legacyDataStore,
                    ksafe = ksafe,
                    markerKey = MIGRATION_MARKER,
                    entries = legacyEntries(),
                )
            } finally {
                migrationDeferred.complete(Unit)
            }
        }
    }

    suspend fun awaitMigration() {
        migrationDeferred.await()
    }

    inline fun <reified T> gatedGetFlow(
        key: String,
        default: T,
    ): Flow<T> =
        flow {
            awaitMigration()
            emitAll(ksafe.safeGetFlow(key, default))
        }

    private fun legacyEntries(): List<MigrationEntry> =
        listOf(
            MigrationEntry(stringPreferencesKey("app_theme"), K_THEME),
            MigrationEntry(booleanPreferencesKey("amoled_theme"), K_AMOLED),
            MigrationEntry(booleanPreferencesKey("is_dark_theme"), K_IS_DARK),
            MigrationEntry(stringPreferencesKey("font_theme"), K_FONT),
            MigrationEntry(stringSetPreferencesKey("discovery_platforms"), K_DISCOVERY_PLATFORMS),
            MigrationEntry(booleanPreferencesKey("auto_detect_clipboard_links"), K_AUTO_DETECT_CLIPBOARD),
            MigrationEntry(stringPreferencesKey("installer_type"), K_INSTALLER_TYPE),
            MigrationEntry(stringPreferencesKey("installer_attribution"), K_INSTALLER_ATTRIBUTION),
            MigrationEntry(booleanPreferencesKey("auto_update_enabled"), K_AUTO_UPDATE),
            MigrationEntry(booleanPreferencesKey("update_check_enabled"), K_UPDATE_CHECK_ENABLED),
            MigrationEntry(longPreferencesKey("update_check_interval_hours"), K_UPDATE_CHECK_INTERVAL),
            MigrationEntry(booleanPreferencesKey("include_pre_releases"), K_INCLUDE_PRE_RELEASES),
            MigrationEntry(booleanPreferencesKey("hide_seen_enabled"), K_HIDE_SEEN_ENABLED),
            MigrationEntry(booleanPreferencesKey("scrollbar_enabled"), K_SCROLLBAR_ENABLED),
            MigrationEntry(stringPreferencesKey("translation_provider"), K_TRANSLATION_PROVIDER),
            MigrationEntry(stringPreferencesKey("youdao_app_key"), K_YOUDAO_APP_KEY),
            MigrationEntry(stringPreferencesKey("youdao_app_secret"), K_YOUDAO_APP_SECRET),
            MigrationEntry(stringPreferencesKey("app_language"), K_APP_LANGUAGE),
            MigrationEntry(booleanPreferencesKey("auto_translate_enabled"), K_AUTO_TRANSLATE_ENABLED),
            MigrationEntry(stringPreferencesKey("auto_translate_target_lang"), K_AUTO_TRANSLATE_TARGET),
            MigrationEntry(booleanPreferencesKey("external_import_enabled"), K_EXTERNAL_IMPORT_ENABLED),
            MigrationEntry(booleanPreferencesKey("external_match_search_enabled"), K_EXTERNAL_MATCH_SEARCH_ENABLED),
            MigrationEntry(intPreferencesKey("external_import_banner_dismissed_at"), K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT),
            MigrationEntry(booleanPreferencesKey("apk_inspect_coachmark_shown"), K_APK_INSPECT_COACHMARK_SHOWN),
            MigrationEntry(booleanPreferencesKey("channel_chip_coachmark_shown"), K_CHANNEL_CHIP_COACHMARK_SHOWN),
            MigrationEntry(booleanPreferencesKey("show_all_platforms"), K_SHOW_ALL_PLATFORMS),
            MigrationEntry(booleanPreferencesKey("battery_opt_prompt_dismissed"), K_BATTERY_OPT_PROMPT_DISMISSED),
            MigrationEntry(intPreferencesKey("last_seen_whats_new_version_code"), K_LAST_SEEN_WHATS_NEW_VERSION_CODE),
            MigrationEntry(stringSetPreferencesKey("announcements_dismissed_ids"), K_ANNOUNCEMENTS_DISMISSED_IDS),
            MigrationEntry(stringSetPreferencesKey("announcements_acknowledged_ids"), K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS),
            MigrationEntry(stringSetPreferencesKey("announcements_muted_categories"), K_ANNOUNCEMENTS_MUTED_CATEGORIES),
            MigrationEntry(longPreferencesKey("announcements_last_fetched_at"), K_ANNOUNCEMENTS_LAST_FETCHED_AT),
            MigrationEntry(stringPreferencesKey("apps_sort_rule"), K_APPS_SORT_RULE),
            MigrationEntry(stringPreferencesKey("starred_sort_rule"), K_STARRED_SORT_RULE),
            MigrationEntry(stringPreferencesKey("favourites_sort_rule"), K_FAVOURITES_SORT_RULE),
            MigrationEntry(stringSetPreferencesKey("custom_forge_hosts"), K_CUSTOM_FORGE_HOSTS),
        )

    private companion object {
        const val MIGRATION_MARKER = "__migrated_from_datastore_v1__"
    }
}
