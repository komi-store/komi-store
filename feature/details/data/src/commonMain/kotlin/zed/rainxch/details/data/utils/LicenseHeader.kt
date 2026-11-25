package zed.rainxch.details.data.utils

internal fun spdxFromLicenseHeader(text: String): String? {
    val upper = text.uppercase()
    return LICENSE_RULES.firstOrNull { (_, matches) -> matches(upper) }?.first
}

private fun String.hasAll(vararg parts: String): Boolean = parts.all { contains(it) }

private fun String.hasAny(vararg parts: String): Boolean = parts.any { contains(it) }

// First match wins: an LGPL header also names the GPL.
private val LICENSE_RULES: List<Pair<String, (String) -> Boolean>> =
    listOf(
        "AGPL-3.0" to { it.hasAll("GNU AFFERO GENERAL PUBLIC LICENSE", "VERSION 3") },
        "LGPL-3.0" to { it.hasAll("GNU LESSER GENERAL PUBLIC LICENSE", "VERSION 3") },
        "LGPL-2.1" to { it.hasAll("GNU LESSER GENERAL PUBLIC LICENSE", "VERSION 2.1") },
        "GPL-3.0" to { it.hasAll("GNU GENERAL PUBLIC LICENSE", "VERSION 3") },
        "GPL-2.0" to { it.hasAll("GNU GENERAL PUBLIC LICENSE", "VERSION 2") },
        "Apache-2.0" to { it.hasAll("APACHE LICENSE", "VERSION 2.0") },
        "MIT" to { it.contains("MIT LICENSE") || (it.startsWith("MIT ") && it.contains("PERMISSION")) },
        "BSD-3-Clause" to { it.hasAny("BSD 3-CLAUSE", "BSD-3-CLAUSE") },
        "BSD-2-Clause" to { it.hasAny("BSD 2-CLAUSE", "BSD-2-CLAUSE") },
        "MPL-2.0" to { it.hasAll("MOZILLA PUBLIC LICENSE", "VERSION 2.0") },
        "Unlicense" to { it.hasAny("THE UNLICENSE", "UNLICENSE") },
        "CC0-1.0" to { it.hasAll("CREATIVE COMMONS", "CC0") },
        "EUPL-1.2" to { it.contains("EUROPEAN UNION PUBLIC LICENCE") },
    )
