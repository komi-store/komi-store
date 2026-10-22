package zed.rainxch.core.data.dto

import zed.rainxch.core.data.network.gitHubJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubStarredPageParseTest {
    private val json = gitHubJson

    private val repository =
        """
        {
          "id": 42,
          "name": "komi-store",
          "owner": { "login": "komi-store", "avatar_url": "https://example.com/avatar.png" },
          "description": "A multiplatform app store",
          "language": "Kotlin",
          "html_url": "https://github.com/komi-store/komi-store",
          "stargazers_count": 100,
          "forks_count": 20,
          "open_issues_count": 5
        }
        """.trimIndent()

    @Test
    fun `parses the wrapped entry the media type produces`() {
        val payload = """[{ "starred_at": "2026-10-12T03:48:30Z", "repo": $repository }]"""

        val page = parseStarredPage(json, json.parseToJsonElement(payload))

        assertEquals(1, page.size)
        assertEquals("2026-10-12T03:48:30Z", page.single().starredAt)
        assertEquals("komi-store", page.single().repo.name)
    }

    @Test
    fun `parses a bare entry instead of failing the page`() {
        val payload = """[$repository]"""

        val page = parseStarredPage(json, json.parseToJsonElement(payload))

        assertEquals(1, page.size)
        assertNull(page.single().starredAt)
        assertEquals(42L, page.single().repo.id)
    }

    @Test
    fun `an empty page stays empty`() {
        assertEquals(0, parseStarredPage(json, json.parseToJsonElement("[]")).size)
    }
}
