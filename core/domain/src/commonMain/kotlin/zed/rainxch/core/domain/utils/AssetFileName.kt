package zed.rainxch.core.domain.utils

object AssetFileName {
    private val FORBIDDEN = Regex("""[^a-z0-9.\-]""")

    private val MULTI_UNDERSCORE = Regex("_+")

    private const val MAX_COMPONENT_LEN = 64

    fun scoped(
        owner: String,
        repo: String,
        originalName: String,
    ): String {
        val safeOwner = sanitizeComponent(owner).take(MAX_COMPONENT_LEN)
        val safeRepo = sanitizeComponent(repo).take(MAX_COMPONENT_LEN)

        val originalLower = originalName.lowercase()
        val dotIndex = originalLower.lastIndexOf('.')
        val (body, ext) =
            if (dotIndex > 0 && dotIndex < originalLower.length - 1) {
                originalLower.substring(0, dotIndex) to originalLower.substring(dotIndex)
            } else {
                originalLower to ""
            }
        val safeBody = sanitizeComponent(body).take(MAX_COMPONENT_LEN)
        val safeExt = sanitizeExtension(ext)

        val ownerPart = safeOwner.ifBlank { "unknown" }
        val repoPart = safeRepo.ifBlank { "unknown" }
        val bodyPart = safeBody.ifBlank { "asset" }

        val joined = "${ownerPart}_${repoPart}_$bodyPart$safeExt"
        return MULTI_UNDERSCORE.replace(joined, "_")
    }

    fun isScoped(
        fileName: String,
        owner: String,
        repo: String,
    ): Boolean {
        val ownerPart = sanitizeComponent(owner).take(MAX_COMPONENT_LEN).ifBlank { "unknown" }
        val repoPart = sanitizeComponent(repo).take(MAX_COMPONENT_LEN).ifBlank { "unknown" }
        val expectedPrefix = MULTI_UNDERSCORE.replace("${ownerPart}_${repoPart}_", "_")
        return fileName.lowercase().startsWith(expectedPrefix)
    }

    private fun sanitizeComponent(input: String): String {
        if (input.isBlank()) return ""
        val lowered = input.lowercase()

        val withoutSeparators =
            lowered.replace('/', '_').replace('\\', '_')

        val noDots = withoutSeparators.replace("..", "_")
        return FORBIDDEN.replace(noDots, "_")
    }

    private fun sanitizeExtension(ext: String): String {
        if (ext.isEmpty()) return ""

        val cleaned = FORBIDDEN.replace(ext, "")

        return if (cleaned.startsWith('.')) cleaned else ".$cleaned"
    }
}
