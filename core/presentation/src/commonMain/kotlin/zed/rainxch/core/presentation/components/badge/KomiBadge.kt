package zed.rainxch.core.presentation.components.badge

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.model.MotionLevel
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun KomiBadge(
    modifier: Modifier = Modifier,
    count: Int? = null,
    dot: Boolean = false,
    tone: KomiBadgeTone = KomiBadgeTone.Alert,
    size: KomiBadgeSize = KomiBadgeSize.Sm,
    max: Int = 99,
    tilt: Boolean = false,
    animate: Boolean = false,
    contentDescription: String? = null,
) {
    if (!dot && (count == null || count <= 0)) return

    val personality = LocalPersonality.current
    val colors = personality.colors
    val fill = if (tone == KomiBadgeTone.Neutral) colors.primary else colors.error
    val fg = if (tone == KomiBadgeTone.Neutral) colors.onPrimary else colors.onError
    val text =
        if (dot) {
            null
        } else if (count!! > max) {
            "$max+"
        } else {
            count.toString()
        }
    val a11y = contentDescription ?: text

    when (personality) {
        is MangaPersonality -> {
        val metrics = badgeMetrics(size)
        val motionOff = personality.motion.level == MotionLevel.OFF
        val popScale = remember { Animatable(if (animate && !motionOff) 0f else 1f) }
        LaunchedEffect(animate) {
            if (animate && !motionOff) {
                popScale.animateTo(1f, personality.motion.fastSpatial())
            }
        }
        val transformed = animate || tilt
        val transformMod =
            if (transformed) {
                Modifier.graphicsLayer {
                    val s = popScale.value
                    scaleX = s
                    scaleY = s
                    rotationZ = if (tilt) -4f else 0f
                }
            } else {
                Modifier
            }
        val sizeMod =
            if (dot) {
                Modifier.size(metrics.dot)
            } else {
                Modifier.height(metrics.height).widthIn(min = metrics.height)
            }

        Box(
            modifier =
                modifier
                    .then(transformMod)
                    .then(sizeMod)
                    .hardShadow(DpOffset(metrics.shadow, metrics.shadow), colors.shadow)
                    .background(fill)
                    .border(metrics.border, colors.outline)
                    .then(if (dot) Modifier else Modifier.padding(horizontal = metrics.padX))
                    .semanticsLabel(a11y),
            contentAlignment = Alignment.Center,
        ) {
            if (text != null) {
                Text(
                    text = text,
                    color = fg,
                    style = personality.type.labelSmall.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        }

        is ClassicPersonality -> {
            Badge(
                modifier = modifier.semanticsLabel(a11y),
                containerColor = fill,
                contentColor = fg,
            ) {
                if (text != null) Text(text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomiBadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    offset: Dp = 5.dp,
    content: @Composable () -> Unit,
) {
    when (LocalPersonality.current) {
        is MangaPersonality -> {
            Box(modifier = modifier) {
                content()
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                translationX = offset.toPx()
                                translationY = -offset.toPx()
                            },
                ) {
                    badge()
                }
            }
        }

        is ClassicPersonality -> {
            BadgedBox(badge = { badge() }, modifier = modifier) { content() }
        }
    }
}

private fun Modifier.semanticsLabel(label: String?): Modifier =
    if (label == null) this else this.semantics { contentDescription = label }

private data class BadgeMetrics(
    val height: Dp,
    val border: Dp,
    val shadow: Dp,
    val padX: Dp,
    val dot: Dp,
)

private fun badgeMetrics(size: KomiBadgeSize): BadgeMetrics =
    when (size) {
        KomiBadgeSize.Sm -> BadgeMetrics(
            height = 16.dp,
            border = 2.dp,
            shadow = 1.5.dp,
            padX = 4.dp,
            dot = 9.dp
        )

        KomiBadgeSize.Md -> BadgeMetrics(
            height = 20.dp,
            border = 2.dp,
            shadow = 2.dp,
            padX = 5.dp,
            dot = 11.dp
        )
    }

@Composable
private fun PreviewBadges() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KomiBadgedBox(badge = {
            KomiBadge(
                count = 3,
                size = KomiBadgeSize.Sm,
                contentDescription = "3 updates available"
            )
        }) {
            Icon(Icons.Default.Apps, contentDescription = "Apps", modifier = Modifier.size(26.dp))
        }
        KomiBadgedBox(badge = { KomiBadge(count = 12, size = KomiBadgeSize.Sm) }) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Alerts",
                modifier = Modifier.size(26.dp)
            )
        }
        KomiBadgedBox(badge = { KomiBadge(count = 128, size = KomiBadgeSize.Sm) }) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Inbox",
                modifier = Modifier.size(26.dp)
            )
        }
        KomiBadgedBox(badge = { KomiBadge(dot = true, size = KomiBadgeSize.Sm) }) {
            Icon(Icons.Default.Person, contentDescription = "You", modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun PreviewRules() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KomiBadge(count = 1, size = KomiBadgeSize.Md)
        KomiBadge(count = 9, size = KomiBadgeSize.Md)
        KomiBadge(count = 42, size = KomiBadgeSize.Md)
        KomiBadge(count = 99, size = KomiBadgeSize.Md)
        KomiBadge(count = 100, size = KomiBadgeSize.Md)
        KomiBadge(count = 5, size = KomiBadgeSize.Md, tone = KomiBadgeTone.Neutral)
        KomiBadge(dot = true, size = KomiBadgeSize.Md)
    }
}

@Composable
private fun PreviewRow() {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiText(
            text = "Announcements",
            modifier = Modifier.weight(1f),
            role = KomiTextRole.Body,
            color = colors.onSurface
        )
        KomiBadge(count = 3, size = KomiBadgeSize.Md)
    }
}

@Composable
private fun PreviewAll() {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PreviewBadges()
        PreviewRules()
        PreviewRow()
    }
}

@Preview
@Composable
private fun KomiBadgeMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewAll() }
}

@Preview
@Composable
private fun KomiBadgeMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN
        )
    ) { PreviewAll() }
}

@Preview
@Composable
private fun KomiBadgeClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewAll() }
}
