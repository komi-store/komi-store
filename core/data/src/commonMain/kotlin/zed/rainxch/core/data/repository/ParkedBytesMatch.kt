package zed.rainxch.core.data.repository

// The decision behind ParkedInstallSettler: pure so the five matching rules can be tested without
// an InstalledAppDao. A missing installed hash is always "not proven".
internal fun parkedBytesMatch(
    installedHash: String?,
    parkedDigest: String?,
    parkedFileHash: String?,
    releaseDigest: String?,
): Boolean {
    if (installedHash == null) return false
    val installed = normalizeDigest(installedHash)
    return when {
        parkedDigest != null -> normalizeDigest(parkedDigest) == installed
        parkedFileHash != null && normalizeDigest(parkedFileHash) == installed -> true
        releaseDigest != null -> normalizeDigest(releaseDigest) == installed
        else -> false
    }
}

internal fun normalizeDigest(raw: String): String = raw.substringAfter(':', raw).trim().lowercase()
