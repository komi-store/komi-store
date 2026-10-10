package zed.rainxch.core.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import zed.rainxch.core.domain.network.AssetIdentity

class PartialDownloadLogicTest {

    @Test
    fun a_partial_is_named_from_the_deterministic_asset_name_with_no_uuid() {
        assertEquals("owner_repo_app.apk.part", PartialNaming.part("owner_repo_app.apk"))
        assertEquals("owner_repo_app.apk.part.meta", PartialNaming.meta("owner_repo_app.apk"))
    }

    @Test
    fun a_sidecar_is_never_mistaken_for_a_partial() {
        assertFalse(
            PartialNaming.isPartFile("owner_repo_app.apk.part.meta"),
            "the sidecar must not be swept up as a partial, or cleanup eats a live download's meta",
        )
        assertTrue(PartialNaming.isMetaFile("owner_repo_app.apk.part.meta"))
        assertNull(PartialNaming.assetNameOfPart("owner_repo_app.apk.part.meta"))
    }

    @Test
    fun both_the_current_and_the_legacy_partial_names_are_recognised() {
        assertTrue(PartialNaming.isPartFile("owner_repo_app.apk.part"))
        assertTrue(
            PartialNaming.isPartFile("owner_repo_app.apk.part-3f2b1c4e-5a6d-4f80-9b1c-2d3e4f5a6b7c"),
            "legacy random-name partials must be visible to orphan collection (design E11)",
        )

        assertEquals("owner_repo_app.apk", PartialNaming.assetNameOfPart("owner_repo_app.apk.part"))
        assertEquals(
            "owner_repo_app.apk",
            PartialNaming.assetNameOfPart("owner_repo_app.apk.part-3f2b1c4e"),
        )
    }

    @Test
    fun an_ordinary_finished_file_is_not_treated_as_a_partial() {
        assertFalse(PartialNaming.isPartFile("owner_repo_app.apk"))
        assertFalse(PartialNaming.isMetaFile("owner_repo_app.apk"))
        assertNull(PartialNaming.assetNameOfPart("owner_repo_app.apk"))
    }

    @Test
    fun identity_survives_a_serialize_parse_round_trip() {
        val identity = PartialIdentity(assetId = 42L, digest = "sha256:abc123", size = 1_000L)

        assertEquals(identity, PartialIdentity.parse(identity.serialize()))
    }

    @Test
    fun a_missing_digest_round_trips_as_null_rather_than_an_empty_string() {
        val identity = PartialIdentity(assetId = 7L, digest = null, size = 10L)

        val parsed = PartialIdentity.parse(identity.serialize())

        assertEquals(identity, parsed)
        assertNull(parsed?.digest)
    }

    @Test
    fun parsing_tolerates_unknown_keys_extra_whitespace_and_reordering() {
        val parsed =
            PartialIdentity.parse(
                """
                |# written by an older build
                |size = 1000
                |
                |futureField=whatever
                |assetId=42
                |digest=sha256:abc
                """.trimMargin(),
            )

        assertEquals(PartialIdentity(42L, "sha256:abc", 1_000L), parsed)
    }

    @Test
    fun a_sidecar_that_cannot_be_trusted_parses_to_null() {
        assertNull(PartialIdentity.parse(""), "empty sidecar is unparsable")
        assertNull(PartialIdentity.parse("digest=sha256:abc"), "no assetId, no size")
        assertNull(PartialIdentity.parse("assetId=42\nsize=notanumber"), "size is garbage")
        assertNull(PartialIdentity.parse("assetId=alsobad\nsize=10"), "assetId is garbage")
    }

    private val incoming = PartialIdentity(assetId = 42L, digest = "sha256:new", size = 1_000L)

    @Test
    fun an_unknown_identity_never_resumes_a_valid_partial() {
        val onDisk = PartialIdentity(assetId = 42L, digest = "sha256:old", size = 1_000L)
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = onDisk,
                incoming = null,
            ),
        )
    }

    @Test
    fun a_matching_asset_identity_resumes_the_partial() {
        assertEquals(
            PartialReuse.REUSE,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 42L, digest = "sha256:same", size = 1_000L),
                incoming = AssetIdentity(assetId = 42L, digest = "sha256:same", size = 1_000L),
            ),
        )
    }

    @Test
    fun an_asset_identity_that_disagrees_discards_the_partial() {
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 42L, digest = "sha256:old", size = 1_000L),
                incoming = AssetIdentity(assetId = 42L, digest = "sha256:new", size = 1_000L),
            ),
        )
    }

    @Test
    fun a_missing_or_empty_partial_is_never_reused() {
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = false,
                partLength = 0L,
                meta = incoming,
                incoming = incoming,
            ),
        )
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 0L,
                meta = incoming,
                incoming = incoming,
            ),
            "a zero-byte partial has nothing to resume from",
        )
    }

    @Test
    fun a_partial_without_a_sidecar_is_never_reused() {
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = null,
                incoming = incoming,
            ),
            "without identity we cannot prove the bytes are still this asset",
        )
    }

    @Test
    fun matching_digests_reuse_even_when_the_asset_id_changed() {
        assertEquals(
            PartialReuse.REUSE,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 1L, digest = "sha256:same", size = 1_000L),
                incoming = PartialIdentity(assetId = 2L, digest = "sha256:same", size = 1_000L),
            ),
        )
    }

    @Test
    fun differing_digests_discard_even_when_the_asset_id_matched() {
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 42L, digest = "sha256:old", size = 1_000L),
                incoming = PartialIdentity(assetId = 42L, digest = "sha256:new", size = 1_000L),
            ),
            "resuming across a digest change produces a corrupt APK",
        )
    }

    @Test
    fun with_only_one_digest_available_the_asset_id_decides() {
        assertEquals(
            PartialReuse.REUSE,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 42L, digest = "sha256:old", size = 1_000L),
                incoming = PartialIdentity(assetId = 42L, digest = null, size = 1_000L),
            ),
        )
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 42L, digest = "sha256:old", size = 1_000L),
                incoming = PartialIdentity(assetId = 99L, digest = null, size = 1_000L),
            ),
        )
    }

    @Test
    fun the_asset_id_outranks_a_size_mismatch() {
        assertEquals(
            PartialReuse.REUSE,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 42L, digest = null, size = 1_000L),
                incoming = PartialIdentity(assetId = 42L, digest = null, size = 999L),
            ),
        )
    }

    @Test
    fun with_neither_digest_nor_id_the_size_is_the_last_resort() {
        assertEquals(
            PartialReuse.REUSE,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 0L, digest = null, size = 1_000L),
                incoming = PartialIdentity(assetId = 0L, digest = null, size = 1_000L),
            ),
        )
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(
                partExists = true,
                partLength = 500L,
                meta = PartialIdentity(assetId = 0L, digest = null, size = 1_000L),
                incoming = PartialIdentity(assetId = 0L, digest = null, size = 2_000L),
            ),
        )
    }

    @Test
    fun no_range_header_is_sent_when_there_is_nothing_on_disk() {
        assertNull(RangeRequest.headerValue(0L))
        assertEquals("bytes=500-", RangeRequest.headerValue(500L))
    }

    @Test
    fun a_206_appends_and_a_416_restarts() {
        assertEquals(
            RangeDecision.APPEND,
            RangeRequest.decide(
                statusCode = 206,
                partLength = 500L,
                contentRange = ContentRange(start = 500L, total = 2000L),
            ),
            "a 206 that starts exactly where we asked resumes correctly",
        )
        assertEquals(
            RangeDecision.RESTART,
            RangeRequest.decide(statusCode = 416, partLength = 500L, contentRange = null),
        )
    }

    @Test
    fun a_206_is_only_appended_when_its_offset_is_the_one_we_asked_for() {
        assertEquals(
            RangeDecision.RESTART,
            RangeRequest.decide(
                statusCode = 206,
                partLength = 500L,
                contentRange = ContentRange(start = 0L, total = 2000L),
            ),
            "the server restarted the span: appending would duplicate bytes we already hold",
        )
        assertEquals(
            RangeDecision.RESTART,
            RangeRequest.decide(
                statusCode = 206,
                partLength = 500L,
                contentRange = ContentRange(start = 800L, total = 2000L),
            ),
            "the server skipped ahead: appending would silently drop bytes 500..799",
        )
        assertEquals(
            RangeDecision.RESTART,
            RangeRequest.decide(statusCode = 206, partLength = 500L, contentRange = null),
            "a 206 with no Content-Range is unverifiable (RFC 7233 4.1), so it cannot be trusted",
        )
        assertEquals(
            RangeDecision.RESTART,
            RangeRequest.decide(
                statusCode = 206,
                partLength = 500L,
                contentRange = ContentRange(start = null, total = 2000L),
            ),
            "the unsatisfied form carries no offset, so it proves nothing",
        )
    }

    @Test
    fun content_range_is_parsed_and_refuses_anything_it_cannot_trust() {
        val resumed = ContentRange.parse("bytes 500-1999/2000")
        assertEquals(500L, resumed?.start)
        assertEquals(2000L, resumed?.total)
        assertEquals(500L, ContentRange.parse("bytes 500-1999/*")?.start)
        assertNull(
            ContentRange.parse("bytes */2000")?.start,
            "the unsatisfied form has no offset, and it must not be mistaken for byte zero",
        )
        assertEquals(2000L, ContentRange.parse("bytes */2000")?.total)
        assertNull(ContentRange.parse(null), "no header is no proof")
        assertNull(ContentRange.parse(""), "an empty header is no proof")
        assertNull(ContentRange.parse("items 0-99/200"), "a non-bytes unit is not a byte range")
        assertNull(ContentRange.parse("bytes nonsense"), "a malformed span yields nothing usable")
    }

    @Test
    fun a_200_rewrites_a_resumed_partial_but_is_normal_on_a_fresh_one() {
        assertEquals(
            RangeDecision.TRUNCATE_REWRITE,
            RangeRequest.decide(statusCode = 200, partLength = 500L, contentRange = null),
            "the server sent the whole asset: appending would duplicate the partial's prefix",
        )
        assertEquals(
            RangeDecision.APPEND,
            RangeRequest.decide(statusCode = 200, partLength = 0L, contentRange = null),
        )
    }

    @Test
    fun an_unplanned_status_is_surfaced_rather_than_guessed() {
        assertEquals(
            RangeDecision.UNEXPECTED,
            RangeRequest.decide(statusCode = 500, partLength = 1L, contentRange = null),
        )
        assertEquals(
            RangeDecision.UNEXPECTED,
            RangeRequest.decide(statusCode = 302, partLength = 1L, contentRange = null),
        )
    }

    private fun classify(
        partExists: Boolean = true,
        metaExists: Boolean = true,
        isClaimed: Boolean = false,
    ) = OrphanPolicy.classify(
        partExists = partExists,
        metaExists = metaExists,
        isClaimed = isClaimed,
    )

    @Test
    fun a_partial_without_a_sidecar_is_swept_away() {
        assertEquals(OrphanVerdict.DROP_PART_ONLY, classify(partExists = true, metaExists = false))
    }

    @Test
    fun a_sidecar_without_a_partial_is_swept_away() {
        assertEquals(OrphanVerdict.DROP_META_ONLY, classify(partExists = false, metaExists = true))
    }

    @Test
    fun nothing_to_clean_is_left_alone() {
        assertEquals(OrphanVerdict.KEEP, classify(partExists = false, metaExists = false))
    }

    @Test
    fun a_claimed_partial_is_kept() {
        assertEquals(OrphanVerdict.KEEP, classify(isClaimed = true))
    }

    @Test
    fun an_unclaimed_complete_pair_is_kept() {
        assertEquals(OrphanVerdict.KEEP, classify(isClaimed = false))
    }

    @Test
    fun an_empty_claim_set_keeps_every_complete_pair() {
        assertEquals(OrphanVerdict.KEEP, classify(isClaimed = false))
        assertEquals(
            OrphanVerdict.DROP_PART_ONLY,
            classify(partExists = true, metaExists = false, isClaimed = false),
        )
        assertEquals(
            OrphanVerdict.DROP_META_ONLY,
            classify(partExists = false, metaExists = true, isClaimed = false),
        )
    }

    @Test
    fun a_live_download_is_never_cleaned() {
        assertEquals(
            OrphanVerdict.KEEP,
            classify(isClaimed = true),
            "cleanup must not race a transfer that is currently writing",
        )
        assertEquals(
            OrphanVerdict.KEEP,
            classify(metaExists = false, isClaimed = true),
            "a partial whose sidecar is not flushed yet is still live, not an orphan",
        )
        assertEquals(
            OrphanVerdict.KEEP,
            classify(partExists = false, isClaimed = true),
            "a sidecar whose partial is not on disk yet is still live, not an orphan",
        )
    }

    @Test
    fun only_the_terminal_client_errors_may_delete_a_partial() {
        assertTrue(PartialDisposal.shouldDiscardPartial(statusCode = 404, explicitDiscard = false))
        assertTrue(PartialDisposal.shouldDiscardPartial(statusCode = 410, explicitDiscard = false))
    }

    @Test
    fun a_403_keeps_the_bytes_because_it_is_usually_a_rate_limit() {
        assertFalse(
            PartialDisposal.shouldDiscardPartial(statusCode = 403, explicitDiscard = false),
            "a 403 is more often a rate limit than a refusal",
        )
    }

    @Test
    fun transient_failures_keep_the_bytes() {
        assertFalse(PartialDisposal.shouldDiscardPartial(statusCode = null, explicitDiscard = false), "a timeout / cancellation has no status and must keep the partial")
        assertFalse(PartialDisposal.shouldDiscardPartial(statusCode = 408, explicitDiscard = false))
        assertFalse(PartialDisposal.shouldDiscardPartial(statusCode = 429, explicitDiscard = false))
        assertFalse(PartialDisposal.shouldDiscardPartial(statusCode = 500, explicitDiscard = false))
        assertFalse(PartialDisposal.shouldDiscardPartial(statusCode = 503, explicitDiscard = false))
    }

    @Test
    fun a_416_is_a_restart_signal_not_a_terminal_client_error() {
        assertFalse(PartialDisposal.shouldDiscardPartial(statusCode = 416, explicitDiscard = false))
    }

    @Test
    fun only_an_explicit_discard_forces_deletion() {
        assertTrue(PartialDisposal.shouldDiscardPartial(statusCode = 200, explicitDiscard = true))
    }

    @Test
    fun the_retry_policy_is_three_attempts_with_bounded_backoff() {
        assertEquals(3, PartialRetry.MAX_ATTEMPTS)

        assertTrue(PartialRetry.hasAttemptsLeft(0))
        assertTrue(PartialRetry.hasAttemptsLeft(1))
        assertTrue(PartialRetry.hasAttemptsLeft(2))
        assertFalse(PartialRetry.hasAttemptsLeft(3))

        assertEquals(1_000L, PartialRetry.backoffMillis(0))
        assertEquals(4_000L, PartialRetry.backoffMillis(1))
        assertEquals(16_000L, PartialRetry.backoffMillis(2))
        assertEquals(16_000L, PartialRetry.backoffMillis(99))
        assertEquals(1_000L, PartialRetry.backoffMillis(-1))
    }

    @Test
    fun a_finished_file_that_is_not_on_disk_is_never_a_reuse_candidate() {
        assertNull(
            FinishedFileReuse.digestToVerify(
                destinationExists = false,
                destinationLength = 1_000L,
                expectedDigest = "sha256:abc",
                expectedSize = 1_000L,
            ),
            "no file on disk means nothing to hash, whatever the identity claims",
        )
    }

    @Test
    fun a_missing_digest_is_never_a_reuse_candidate() {
        assertNull(
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 1_000L,
                expectedDigest = null,
                expectedSize = 1_000L,
            ),
            "a null identity collapses to a null digest and must fail closed",
        )
    }

    @Test
    fun a_blank_digest_is_never_a_reuse_candidate() {
        assertNull(
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 1_000L,
                expectedDigest = "",
                expectedSize = 1_000L,
            ),
        )
        assertNull(
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 1_000L,
                expectedDigest = "   ",
                expectedSize = 1_000L,
            ),
        )
    }

    @Test
    fun a_known_size_that_disagrees_disqualifies_the_file() {
        assertNull(
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 999L,
                expectedDigest = "sha256:abc",
                expectedSize = 1_000L,
            ),
            "a size mismatch means the file is not the wanted bytes; do not even spend a hash on it",
        )
    }

    @Test
    fun a_known_size_that_agrees_returns_the_digest_to_verify() {
        assertEquals(
            "sha256:abc",
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 1_000L,
                expectedDigest = "sha256:abc",
                expectedSize = 1_000L,
            ),
        )
    }

    @Test
    fun an_unknown_size_skips_the_length_check_instead_of_treating_it_as_empty() {
        assertEquals(
            "sha256:abc",
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 1_000L,
                expectedDigest = "sha256:abc",
                expectedSize = 0L,
            ),
        )
        assertEquals(
            "sha256:abc",
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 1_000L,
                expectedDigest = "sha256:abc",
                expectedSize = -1L,
            ),
        )
    }

    @Test
    fun the_digest_is_returned_verbatim_without_normalisation() {
        val digest = "sha256:AbC123"
        assertEquals(
            digest,
            FinishedFileReuse.digestToVerify(
                destinationExists = true,
                destinationLength = 10L,
                expectedDigest = digest,
                expectedSize = 10L,
            ),
            "the caller hashes with this exact string; the gate must not reshape, trim or lowercase it",
        )
    }
}
