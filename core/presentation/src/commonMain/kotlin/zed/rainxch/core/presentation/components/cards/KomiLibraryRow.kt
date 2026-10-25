package zed.rainxch.core.presentation.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.reviews.CompactRating
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.utils.formatCompactCount
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.installed
import zed.rainxch.githubstore.core.presentation.res.self_owned_badge
import zed.rainxch.githubstore.core.presentation.res.update_available

enum class KomiLibraryStatus {
    Installed,
    Update,
}

// One saved repo in a library list (Recently viewed, Favourites, Stars). The app's icon (the
// installed one when the app is on the device) sits on a cookie plate (an ink frame in Manga) beside
// its name, owner and two lines of description; below them is what people weigh before opening it:
// install status, rating, stars, platforms and the newest version. [context] says when the list got
// the repo, and [action] sits at the top end.
@Composable
fun KomiLibraryRow(
    name: String,
    owner: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    avatarUrl: String? = null,
    installedPackageName: String? = null,
    platforms: ImmutableList<DiscoveryPlatform> = persistentListOf(),
    version: String? = null,
    stars: Int? = null,
    ratingAverage: Double? = null,
    ratingCount: Int = 0,
    status: KomiLibraryStatus? = null,
    context: String? = null,
    contextIcon: ImageVector? = null,
    ownedByViewer: Boolean = false,
    onOwnerClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LibraryPicture(name = name, avatarUrl = avatarUrl, installedPackageName = installedPackageName)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        KomiText(
                            text = name,
                            role = KomiTextRole.CardTitle,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OwnerLine(owner = owner, ownedByViewer = ownedByViewer, onClick = onOwnerClick)
                    }
                    action?.invoke()
                }
                description?.takeIf { it.isNotBlank() }?.let {
                    KomiText(
                        text = it,
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LibraryFacts(
                    status = status,
                    ratingAverage = ratingAverage,
                    ratingCount = ratingCount,
                    stars = stars,
                    platforms = platforms,
                    version = version,
                )
                if (context != null) LibraryContext(text = context, icon = contextIcon)
            }
        }
    }
}

// An installed app's icon has its own shape, so it sits inset on the plate; an avatar is cut to it.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryPicture(
    name: String,
    avatarUrl: String?,
    installedPackageName: String?,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val shape = if (manga) RectangleShape else MaterialShapes.Cookie4Sided.toShape()
    val frame =
        if (manga) {
            Modifier
                .hardShadow(offset = DpOffset(2.dp, 2.dp), color = colors.shadow)
                .border(2.dp, colors.outline)
        } else {
            Modifier
        }
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .then(frame)
                .clip(shape)
                .background(if (installedPackageName != null && !manga) colors.primaryContainer else colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (installedPackageName != null) {
            InstalledAppIcon(
                packageName = installedPackageName,
                appName = name,
                modifier = Modifier.fillMaxSize(if (manga) 1f else PLATED_ICON_FRACTION),
                avatarFallbackUrl = avatarUrl,
            )
        } else {
            GitHubStoreImage(imageModel = { avatarUrl }, modifier = Modifier.fillMaxSize(), showLoadingIndicator = false)
        }
    }
}

@Composable
private fun OwnerLine(
    owner: String,
    ownedByViewer: Boolean,
    onClick: (() -> Unit)?,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(personality.shape.cornerSmall))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KomiText(
            text = owner,
            role = KomiTextRole.LabelSmall,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (ownedByViewer) {
            KomiIcon(
                imageVector = Icons.Filled.Verified,
                contentDescription = stringResource(Res.string.self_owned_badge),
                tint = colors.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// Each fact only when it's known: rows saved before a repo's details were kept show fewer.
@Composable
private fun LibraryFacts(
    status: KomiLibraryStatus?,
    ratingAverage: Double?,
    ratingCount: Int,
    stars: Int?,
    platforms: ImmutableList<DiscoveryPlatform>,
    version: String?,
) {
    val colors = LocalPersonality.current.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        status?.let { StatusBadge(it) }
        ratingAverage?.let { CompactRating(average = it, count = ratingCount) }
        stars?.let { count ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                KomiIcon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                KomiText(text = formatCompactCount(count), role = KomiTextRole.Caption, color = colors.onSurfaceVariant)
            }
        }
        if (platforms.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                platforms.forEach { platform ->
                    platform.toIcon()?.let { icon ->
                        KomiIcon(
                            imageVector = icon,
                            contentDescription = platform.toLabel(),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        version?.let { VersionTag(it) }
    }
}

@Composable
private fun StatusBadge(status: KomiLibraryStatus) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val update = status == KomiLibraryStatus.Update
    val fill = if (update) colors.primary else colors.primaryContainer
    val ink = if (update) colors.onPrimary else colors.onPrimaryContainer
    Row(
        modifier =
            Modifier
                .background(fill, if (personality is MangaPersonality) RectangleShape else RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KomiIcon(
            imageVector = if (update) Icons.Rounded.Upgrade else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(12.dp),
        )
        KomiText(
            text = stringResource(if (update) Res.string.update_available else Res.string.installed),
            role = KomiTextRole.LabelSmall,
            color = ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun VersionTag(version: String) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    KomiText(
        text = version,
        role = KomiTextRole.LabelSmall,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .border(1.dp, colors.outlineVariant, if (personality is MangaPersonality) RectangleShape else RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun LibraryContext(
    text: String,
    icon: ImageVector?,
) {
    val colors = LocalPersonality.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let {
            KomiIcon(imageVector = it, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
        KomiText(text = text, role = KomiTextRole.Caption, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

private const val PLATED_ICON_FRACTION = 0.76f
