package zed.rainxch.core.domain.model.repository

enum class FeedCategory(
    val topicCodes: Set<String>,
) {
    All(emptySet()),
    Ai(setOf("ai")),
    Privacy(setOf("privacy", "security")),
    Networking(setOf("networking", "browser", "messaging")),
    Media(setOf("audio", "video", "photo")),
    Social(setOf("social")),
    Reading(setOf("reader", "notes")),
    Tools(setOf("launcher", "backup", "self-hosted")),
    ;

    fun matches(codes: List<String>): Boolean {
        if (this == All) return true
        if (codes.isEmpty()) return false
        return codes.any { it.lowercase() in topicCodes }
    }
}
