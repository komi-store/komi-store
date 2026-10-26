package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.ScanResult

interface ExternalImportScans {
    suspend fun scheduleInitialScanIfNeeded()

    suspend fun runFullScan(includeUnverified: Boolean = false): ScanResult

    suspend fun runDeltaScan(changedPackageNames: Set<String>): ScanResult

    suspend fun rescanSinglePackage(packageName: String): RepoMatchResult?

    suspend fun syncSigningFingerprintSeed()

    suspend fun pruneExpiredSkips()

    suspend fun isPermissionGranted(): Boolean
}
