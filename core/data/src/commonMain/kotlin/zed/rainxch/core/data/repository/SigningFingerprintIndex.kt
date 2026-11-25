package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import eu.anifantakis.lib.ksafe.KSafe
import zed.rainxch.core.data.dto.SigningFingerprintSeedResponse
import zed.rainxch.core.data.local.db.dao.SigningFingerprintDao
import zed.rainxch.core.data.local.db.entities.SigningFingerprintEntity
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.getSigningSeeds
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.error.LocalStoreException
import kotlin.time.Clock

// The local table of known signing certificates, seeded from the backend, that links an installed app to its repo.
internal class SigningFingerprintIndex(
    private val dao: SigningFingerprintDao,
    private val backendClient: BackendApiClient,
    private val ksafe: KSafe,
) {
    // A failed read counts as unknown: the fingerprint is only one source of evidence.
    suspend fun lookup(fingerprint: String): SigningFingerprintEntity? =
        try {
            localStore { dao.lookup(fingerprint) }
        } catch (e: LocalStoreException) {
            Logger.d { "signing fingerprint lookup failed: ${e.message}" }
            null
        }

    suspend fun syncSeed() {
        val lastSyncedAt = ksafe.safeGet<Long?>(K_SIGNING_SEED_SYNCED_AT, null)
        if (lastSyncedAt != null && nowMillis() - lastSyncedAt < SIGNING_SEED_SYNC_INTERVAL_MS) return
        if (fetchSeedPages(since = lastObservedAt())) ksafe.safePut(K_SIGNING_SEED_SYNCED_AT, nowMillis())
    }

    private suspend fun lastObservedAt(): Long? =
        try {
            localStore { dao.lastSyncTimestamp() }
        } catch (_: LocalStoreException) {
            null
        }

    // True once any page arrived; a failed page ends the walk, and a failed write skips only that page.
    private suspend fun fetchSeedPages(since: Long?): Boolean {
        var fetched = false
        var cursor: String? = null
        var pages = 0
        var more = true
        while (more && pages < MAX_SEED_PAGES) {
            pages++
            val response =
                backendClient.getSigningSeeds(since = since, cursor = cursor).getOrElse { error ->
                    Logger.w(error) { "signing-seeds fetch failed on page $pages; aborting" }
                    null
                }
            if (response != null) {
                fetched = true
                store(response.rows, pages)
                cursor = response.nextCursor
            }
            more = response != null && cursor != null
        }
        return fetched
    }

    private suspend fun store(
        rows: List<SigningFingerprintSeedResponse.Row>,
        page: Int,
    ) {
        if (rows.isEmpty()) return
        try {
            localStore {
                dao.upsertAll(
                    rows.map { row ->
                        SigningFingerprintEntity(
                            fingerprint = row.fingerprint,
                            repoOwner = row.owner,
                            repoName = row.repo,
                            source = SEED_SOURCE_BACKEND,
                            observedAt = row.observedAt,
                        )
                    },
                )
            }
        } catch (e: LocalStoreException) {
            Logger.w(e) { "signing-seeds upsert failed on page $page; continuing" }
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val K_SIGNING_SEED_SYNCED_AT = "signing_seed_synced_at"
        const val SIGNING_SEED_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val MAX_SEED_PAGES = 50
        const val SEED_SOURCE_BACKEND = "backend_seed"
    }
}
