package zed.rainxch.core.domain.model.account
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: Int,
    val imageUrl: String,
    val name: String,
    val username: String,
    val bio: String?,
    val repositoryCount: Int,
    val followers: Int,
    val following: Int,
)
