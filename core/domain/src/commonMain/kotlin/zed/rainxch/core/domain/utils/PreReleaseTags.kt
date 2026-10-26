package zed.rainxch.core.domain.utils

object PreReleaseTags {
    fun isPreReleaseTag(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return false

        val separated = VersionMath.insertHyphenBeforeKnownMarker(tag)
        return PRE_RELEASE_MARKER_PATTERN.containsMatchIn(separated)
    }

    fun preReleaseMarkerLabel(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val separated = VersionMath.insertHyphenBeforeKnownMarker(tag)
        val match = PRE_RELEASE_MARKER_PATTERN.find(separated) ?: return null
        val raw =
            match.groupValues
                .getOrNull(1)
                ?.lowercase()
                .orEmpty()
        return MARKER_LABELS[raw] ?: MILESTONE_LABEL.takeIf { raw.startsWith("m") }
    }

    private const val MILESTONE_LABEL = "Milestone"

    private val MARKER_LABELS =
        mapOf(
            "alpha" to "Alpha",
            "beta" to "Beta",
            "rc" to "RC",
            "preview" to "Preview",
            "prerelease" to "Pre-release",
            "snapshot" to "Snapshot",
            "canary" to "Canary",
            "nightly" to "Nightly",
            "rolling" to "Rolling",
            "milestone" to MILESTONE_LABEL,
            "ea" to "Early Access",
            "dev" to "Dev",
            "pre" to "Pre",
        )

    private val PRE_RELEASE_MARKER_PATTERN =
        Regex(
            "\\b(alpha|beta|rc|preview|prerelease|snapshot|canary|nightly|rolling|milestone|ea|dev|pre|m\\d+)\\d*\\b",
            RegexOption.IGNORE_CASE,
        )
}
