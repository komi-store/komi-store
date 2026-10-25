package zed.rainxch.core.presentation.components.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_to_favourites
import zed.rainxch.githubstore.core.presentation.res.remove_from_favourites

// The heart that keeps a repo in Favourites: filled red when on, an outline when off. It pops when
// it fills, not when a row that's already a favourite appears.
@Composable
fun KomiFavouriteToggle(
    favourited: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val motion = personality.motion
    val manga = personality is MangaPersonality
    val description = stringResource(if (favourited) Res.string.remove_from_favourites else Res.string.add_to_favourites)
    val wasFavourited = remember { mutableStateOf(favourited) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(favourited) {
        if (favourited && !wasFavourited.value) {
            pop.animateTo(HEART_POP_SCALE, motion.fastSpatial())
            pop.animateTo(1f, motion.bouncySpatial())
        }
        wasFavourited.value = favourited
    }
    val shape = if (manga) RectangleShape else CircleShape
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .then(if (manga) Modifier.border(2.dp, colors.outline) else Modifier)
                .toggleable(value = favourited, role = Role.Switch, onValueChange = { onClick() })
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = if (favourited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = if (favourited) colors.error else colors.onSurfaceVariant,
            modifier =
                Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
        )
    }
}

private const val HEART_POP_SCALE = 1.35f
