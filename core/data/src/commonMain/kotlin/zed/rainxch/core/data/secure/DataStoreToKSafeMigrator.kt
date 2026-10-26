package zed.rainxch.core.data.secure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.anifantakis.lib.ksafe.KSafe
import zed.rainxch.core.data.local.datastore.removeKeys
import zed.rainxch.core.data.local.datastore.snapshotOrNull

suspend fun migrateDataStoreToKSafe(
    legacy: DataStore<Preferences>,
    ksafe: KSafe,
    markerKey: String,
    entries: List<MigrationEntry>,
) {
    if (ksafe.safeGet(markerKey, false)) return
    val snapshot = legacy.snapshotOrNull() ?: return

    val movedKeys = mutableListOf<Preferences.Key<*>>()
    var anyFailure = false
    entries.forEach { entry ->
        when (copyOrNull(entry, snapshot, ksafe)) {
            null -> anyFailure = true
            true -> movedKeys += entry.legacyKeys
            false -> Unit
        }
    }

    if (movedKeys.isNotEmpty() && !legacy.removeKeys(movedKeys)) anyFailure = true

    if (!anyFailure) ksafe.safePut(markerKey, true)
}

// Null when the copy failed, so the marker stays unset and the next start retries.
private suspend fun copyOrNull(
    entry: MigrationEntry,
    snapshot: Preferences,
    ksafe: KSafe,
): Boolean? = guardStorage({ "migrating ${entry.legacyKeys} failed" }, { null }) { entry.copy(snapshot, ksafe) }

class MigrationEntry(
    val legacyKeys: List<Preferences.Key<*>>,
    private val copyBlock: suspend (Preferences, KSafe) -> Boolean,
) {
    suspend fun copy(
        snapshot: Preferences,
        ksafe: KSafe,
    ): Boolean = copyBlock(snapshot, ksafe)

    companion object {
        operator fun invoke(
            legacyKey: Preferences.Key<*>,
            ksafeKey: String,
        ): MigrationEntry =
            MigrationEntry(
                legacyKeys = listOf(legacyKey),
                copyBlock = { snapshot, ksafe ->
                    val value: Any = snapshot[legacyKey] ?: return@MigrationEntry false
                    when (value) {
                        is Boolean -> {
                            ksafe.put<Boolean>(ksafeKey, value)
                        }

                        is Int -> {
                            ksafe.put<Int>(ksafeKey, value)
                        }

                        is Long -> {
                            ksafe.put<Long>(ksafeKey, value)
                        }

                        is Float -> {
                            ksafe.put<Float>(ksafeKey, value)
                        }

                        is Double -> {
                            ksafe.put<Double>(ksafeKey, value)
                        }

                        is String -> {
                            ksafe.put<String>(ksafeKey, value)
                        }

                        is Set<*> -> {
                            val asStrings = value.filterIsInstance<String>()
                            ksafe.put<List<String>>(ksafeKey, asStrings)
                        }

                        else -> {
                            return@MigrationEntry false
                        }
                    }
                    true
                },
            )
    }
}
