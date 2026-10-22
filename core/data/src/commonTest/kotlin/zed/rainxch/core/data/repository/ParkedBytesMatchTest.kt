package zed.rainxch.core.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkedBytesMatchTest {
    @Test
    fun parkedDigestEqualInstalledSettles() {
        assertTrue(
            parkedBytesMatch(
                installedHash = "sha256:abc",
                parkedDigest = "sha256:ABC",
                parkedFileHash = null,
                releaseDigest = null,
            ),
        )
    }

    @Test
    fun parkedDigestDifferentKeepsPark() {
        assertFalse(
            parkedBytesMatch(
                installedHash = "sha256:abc",
                parkedDigest = "sha256:def",
                parkedFileHash = "sha256:abc",
                releaseDigest = "sha256:abc",
            ),
        )
    }

    @Test
    fun parkedFileEqualInstalledSettlesWithoutParkedDigest() {
        assertTrue(
            parkedBytesMatch(
                installedHash = "sha256:abc",
                parkedDigest = null,
                parkedFileHash = "sha256:abc",
                releaseDigest = "sha256:def",
            ),
        )
    }

    @Test
    fun releaseDigestEqualInstalledSettlesAsLastResort() {
        assertTrue(
            parkedBytesMatch(
                installedHash = "sha256:abc",
                parkedDigest = null,
                parkedFileHash = "sha256:def",
                releaseDigest = "sha256:abc",
            ),
        )
    }

    @Test
    fun noInstalledHashNeverSettles() {
        assertFalse(
            parkedBytesMatch(
                installedHash = null,
                parkedDigest = "sha256:abc",
                parkedFileHash = "sha256:abc",
                releaseDigest = "sha256:abc",
            ),
        )
    }
}
