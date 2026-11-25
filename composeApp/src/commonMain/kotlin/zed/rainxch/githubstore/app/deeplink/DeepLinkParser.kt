package zed.rainxch.githubstore.app.deeplink

object DeepLinkParser {
    private val INVALID_CHARS = setOf('/', '\\', '?', '#', '@', ':', '*', '"', '<', '>', '|', '%', '&', '=')

    private val FORBIDDEN_PATTERNS = listOf("..", "~", "\u0000")

    private val EXCLUDED_PATHS =
        setOf(
            "about",
            "account",
            "admin",
            "api",
            "apps",
            "articles",
            "blog",
            "business",
            "collections",
            "contact",
            "dashboard",
            "enterprises",
            "events",
            "explore",
            "features",
            "home",
            "issues",
            "marketplace",
            "new",
            "notifications",
            "orgs",
            "pricing",
            "pulls",
            "search",
            "security",
            "settings",
            "showcases",
            "site",
            "sponsors",
            "topics",
            "trending",
            "team",
        )

    private val BASE64URL_RE = Regex("^[A-Za-z0-9_-]+$")

    private const val MAX_OWNER_LENGTH = 39
    private const val MAX_REPO_LENGTH = 100
    private const val MAX_AUTH_TOKEN_LENGTH = 256
    private const val MAX_AUTH_ERROR_LENGTH = 64

    // "%" and two hex digits.
    private const val PERCENT_ESCAPE_LENGTH = 3
    private const val HEX_RADIX = 16

    private const val REPO_LINK_PREFIX = "githubstore://repo/"
    private const val GITHUB_PREFIX = "https://github.com/"
    private const val STORE_APP_PREFIX = "https://github-store.org/app/"

    private val EXACT_LINKS =
        mapOf(
            "githubstore://home" to DeepLinkDestination.Home,
            "githubstore://home/" to DeepLinkDestination.Home,
            "githubstore://apps" to DeepLinkDestination.Apps,
            "githubstore://apps/" to DeepLinkDestination.Apps,
            "githubstore://tweaks/feedback" to DeepLinkDestination.Feedback,
            "githubstore://feedback" to DeepLinkDestination.Feedback,
            "githubstore://tweaks" to DeepLinkDestination.Tweaks,
            "githubstore://tweaks/" to DeepLinkDestination.Tweaks,
            "githubstore://about" to DeepLinkDestination.About,
            "githubstore://tweaks/app-info" to DeepLinkDestination.About,
            "githubstore://tweaks/licenses" to DeepLinkDestination.TweaksLicenses,
            "githubstore://search" to DeepLinkDestination.Search,
            "githubstore://search/" to DeepLinkDestination.Search,
            "githubstore://favourites" to DeepLinkDestination.Favourites,
            "githubstore://favourites/" to DeepLinkDestination.Favourites,
            "githubstore://recent" to DeepLinkDestination.RecentlyViewed,
            "githubstore://recent/" to DeepLinkDestination.RecentlyViewed,
        )

    fun parse(uri: String): DeepLinkDestination = EXACT_LINKS[uri] ?: parsePrefixed(uri)

    private fun parsePrefixed(uri: String): DeepLinkDestination =
        when {
            uri.startsWith("githubstore://apps?") -> {
                DeepLinkDestination.Apps
            }

            uri.startsWith("githubstore://auth?") || uri.startsWith("githubstore://auth/?") -> {
                parseAuthCallback(uri)
            }

            uri.startsWith(REPO_LINK_PREFIX) -> {
                parseOwnerRepo(urlDecode(uri.removePrefix(REPO_LINK_PREFIX)))
            }

            uri.startsWith(GITHUB_PREFIX) -> {
                val path =
                    uri
                        .removePrefix(GITHUB_PREFIX)
                        .substringBefore('?')
                        .substringBefore('#')
                parseOwnerRepo(urlDecode(path))
            }

            uri.startsWith(STORE_APP_PREFIX) -> {
                queryValue(uri, "repo")?.let(::parseOwnerRepo) ?: DeepLinkDestination.None
            }

            else -> {
                DeepLinkDestination.None
            }
        }

    private fun urlDecode(value: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < value.length) {
            val escaped = percentEscapeAt(value, i)
            if (escaped != null) {
                result.append(escaped)
                i += PERCENT_ESCAPE_LENGTH
            } else {
                result.append(if (value[i] == '+') ' ' else value[i])
                i++
            }
        }
        return result.toString()
    }

    private fun percentEscapeAt(
        value: String,
        index: Int,
    ): Char? {
        if (value[index] != '%' || index + PERCENT_ESCAPE_LENGTH > value.length) return null
        return value.substring(index + 1, index + PERCENT_ESCAPE_LENGTH).toIntOrNull(HEX_RADIX)?.toChar()
    }

    private fun parseOwnerRepo(path: String): DeepLinkDestination {
        val parts = path.split("/").filter { it.isNotEmpty() }
        return if (parts.size >= 2 && isStrictlyValidOwnerRepo(parts[0], parts[1])) {
            DeepLinkDestination.Repository(parts[0], parts[1])
        } else {
            DeepLinkDestination.None
        }
    }

    private fun isStrictlyValidOwnerRepo(
        owner: String,
        repo: String,
    ): Boolean =
        isValidPathSegment(owner, MAX_OWNER_LENGTH) &&
            isValidPathSegment(repo, MAX_REPO_LENGTH) &&
            owner.lowercase() !in EXCLUDED_PATHS

    private fun isValidPathSegment(
        segment: String,
        maxLength: Int,
    ): Boolean =
        segment.isNotEmpty() &&
            segment.length <= maxLength &&
            segment.first().isLetterOrDigit() &&
            segment.none { it in INVALID_CHARS || it.isISOControl() || it == ' ' } &&
            FORBIDDEN_PATTERNS.none { segment.contains(it, ignoreCase = true) }

    private fun parseAuthCallback(uri: String): DeepLinkDestination {
        val state = queryValue(uri, "state")?.takeIf(::isValidAuthToken) ?: return DeepLinkDestination.None
        val handoff = queryValue(uri, "handoff")
        val error = queryValue(uri, "error")
        return when {
            handoff != null -> {
                if (isValidAuthToken(handoff)) {
                    DeepLinkDestination.AuthHandoff(handoffId = handoff, state = state)
                } else {
                    DeepLinkDestination.None
                }
            }

            error != null -> {
                parseAuthError(error, state)
            }

            else -> {
                DeepLinkDestination.None
            }
        }
    }

    private fun parseAuthError(
        error: String,
        state: String,
    ): DeepLinkDestination {
        val sanitized = error.take(MAX_AUTH_ERROR_LENGTH).filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return if (sanitized.isEmpty()) DeepLinkDestination.None else DeepLinkDestination.AuthError(reason = sanitized, state = state)
    }

    private fun isValidAuthToken(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_AUTH_TOKEN_LENGTH && BASE64URL_RE.matches(value)

    private fun queryValue(
        uri: String,
        key: String,
    ): String? = extractQueryParam(uri, key)?.let(::urlDecode)

    private fun extractQueryParam(
        uri: String,
        key: String,
    ): String? {
        val queryStart = uri.indexOf('?')
        if (queryStart == -1) return null
        return uri
            .substring(queryStart + 1)
            .split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == key }
            ?.get(1)
    }

    fun extractSupportedUrl(text: String): String? {
        val regex =
            """https?://(?:www\.)?(?:github\.com|github-store\.org)(?=[/\s?#]|$)[^\s<>"')\],;.!]*""".toRegex(
                RegexOption.IGNORE_CASE,
            )
        return regex.find(text)?.value
    }
}
