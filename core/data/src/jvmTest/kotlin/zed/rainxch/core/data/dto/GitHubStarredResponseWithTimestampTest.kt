package zed.rainxch.core.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubStarredResponseWithTimestampTest {
    @Test
    fun decodesRepositoryAndStarredAtTimestamp() {
        val response =
            Json.decodeFromString<GitHubStarredResponseWithTimestamp>(
                """
                {
                  "starred_at": "2026-10-12T03:48:30Z",
                  "repo": {
                    "id": 42,
                    "name": "komi-store",
                    "owner": {
                      "login": "komi-store",
                      "avatar_url": "https://example.com/avatar.png"
                    },
                    "description": "A multiplatform app store",
                    "language": "Kotlin",
                    "html_url": "https://github.com/komi-store/komi-store",
                    "stargazers_count": 100,
                    "forks_count": 20,
                    "open_issues_count": 5
                  }
                }
                """.trimIndent(),
            )

        assertEquals("2026-10-12T03:48:30Z", response.starredAt)
        assertEquals(42L, response.repo.id)
        assertEquals("komi-store", response.repo.name)
        assertEquals("komi-store", response.repo.owner.login)
    }

    @Test
    fun missingStarredAtTimestampDefaultsToNull() {
        val response =
            Json.decodeFromString<GitHubStarredResponseWithTimestamp>(
                """
                {
                  "repo": {
                    "id": 42,
                    "name": "komi-store",
                    "owner": {
                      "login": "komi-store",
                      "avatar_url": "https://example.com/avatar.png"
                    },
                    "description": null,
                    "language": null,
                    "html_url": "https://github.com/komi-store/komi-store",
                    "stargazers_count": 100,
                    "forks_count": 20,
                    "open_issues_count": 5
                  }
                }
                """.trimIndent(),
            )

        assertNull(response.starredAt)
    }
}
