package zed.rainxch.core.domain.use_cases

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.SystemPackageInfo

// The surviving proof's facts, one arm each: the file belongs to the row's package, the system
// runs the file's build or a newer one, and the system was installed after the file finished.
// Any missing or contradicted fact keeps the file for the user.
class ParkedFileSupersededTest {

    @Test
    fun theSystemsOwnBuildInstalledAfterTheFileIsSuperseded() {
        assertTrue(
            parkedFileSuperseded(
                "com.example.app",
                file(code = 22L),
                system(code = 22L, installedAt = 2_000L),
            ),
        )
    }

    @Test
    fun aNewerBuildThanTheFileIsSupersededToo() {
        assertTrue(
            parkedFileSuperseded(
                "com.example.app",
                file(code = 22L),
                system(code = 23L, installedAt = 2_000L),
            ),
        )
    }

    @Test
    fun anInstallOlderThanTheFileKeepsIt() {
        assertFalse(
            parkedFileSuperseded(
                "com.example.app",
                file(code = 22L),
                system(code = 22L, installedAt = 500L),
            ),
        )
    }

    @Test
    fun aFileNewerThanTheSystemKeepsIt() {
        assertFalse(
            parkedFileSuperseded(
                "com.example.app",
                file(code = 22L),
                system(code = 21L, installedAt = 2_000L),
            ),
        )
    }

    @Test
    fun aFileForAnotherPackageKeepsIt() {
        assertFalse(
            parkedFileSuperseded(
                "com.example.other",
                file(code = 22L),
                system(code = 22L, installedAt = 2_000L),
            ),
        )
    }

    @Test
    fun anUnknownCodeOrTimeOnEitherSideKeepsIt() {
        val unknownFile = ApkPackageInfo(
            packageName = "com.example.app",
            versionName = "1.9.3",
            versionCode = 0L,
            appName = "App",
            signingFingerprint = "SHA",
            fileLastModified = 1_000L,
        )
        val noFileTime = unknownFile.copy(versionCode = 22L, fileLastModified = null)

        assertFalse(parkedFileSuperseded("com.example.app", unknownFile, system(22L, 2_000L)))
        assertFalse(parkedFileSuperseded("com.example.app", noFileTime, system(22L, 2_000L)))
        assertFalse(
            parkedFileSuperseded(
                "com.example.app",
                file(22L),
                system(22L, installedAt = null),
            ),
        )
        assertFalse(parkedFileSuperseded("com.example.app", file(22L), null))
    }

    private fun file(
        code: Long,
        fileAt: Long? = 1_000L,
    ): ApkPackageInfo = ApkPackageInfo(
        packageName = "com.example.app",
        versionName = "1.9.3",
        versionCode = code,
        appName = "App",
        signingFingerprint = "SHA",
        fileLastModified = fileAt,
    )

    private fun system(
        code: Long,
        installedAt: Long?,
    ): SystemPackageInfo = SystemPackageInfo(
        packageName = "com.example.app",
        versionName = "1.9.3",
        versionCode = code,
        isInstalled = true,
        signingFingerprint = "SHA",
        lastUpdateTime = installedAt,
    )
}
