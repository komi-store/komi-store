package zed.rainxch.core.data.local.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seen_repos")
data class SeenRepoEntity(
    @PrimaryKey
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val seenAt: Long,
    @ColumnInfo(defaultValue = "NULL")
    val stargazersCount: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val platforms: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val latestVersion: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val sourceHost: String? = null,
)
