package zed.rainxch.devprofile.data.mappers

import zed.rainxch.core.data.dto.UserProfileNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileNetworkToDomainTest {
    @Test
    fun an_organization_type_marks_the_profile_as_an_organization() {
        val profile = network(type = "Organization").toDeveloperProfile()

        assertEquals("Organization", profile.userType)
        assertTrue(profile.isOrganization)
    }

    @Test
    fun a_user_type_is_not_an_organization() {
        val profile = network(type = "User").toDeveloperProfile()

        assertEquals("User", profile.userType)
        assertFalse(profile.isOrganization)
    }

    @Test
    fun a_missing_type_is_not_an_organization() {
        val profile = network(type = null).toDeveloperProfile()

        assertEquals(null, profile.userType)
        assertFalse(profile.isOrganization)
    }

    private fun network(type: String?) =
        UserProfileNetwork(
            id = 878437,
            login = "JetBrains",
            avatarUrl = "https://avatars.githubusercontent.com/u/878437",
            htmlUrl = "https://github.com/JetBrains",
            followers = 0,
            following = 0,
            publicRepos = 0,
            type = type,
        )
}
