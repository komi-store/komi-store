package zed.rainxch.core.domain.network

interface MirrorConnectionTester {
    // A null template probes GitHub directly.
    suspend fun test(urlTemplate: String?): MirrorTestOutcome
}
