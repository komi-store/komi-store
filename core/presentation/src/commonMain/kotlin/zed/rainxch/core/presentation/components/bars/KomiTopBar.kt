package zed.rainxch.core.presentation.components.bars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.speedLineWash
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import androidx.compose.material3.Text as MaterialText

@Composable
fun KomiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleAccent: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    size: KomiTopBarSize = KomiTopBarSize.Masthead,
    centerTitle: Boolean = false,
    insets: Boolean = true,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaTopBar(
                personality = personality,
                title = title,
                titleAccent = titleAccent,
                subtitle = subtitle,
                leading = leading,
                actions = actions,
                size = size,
                centerTitle = centerTitle,
                insets = insets,
                modifier = modifier,
            )
        }

        is ClassicPersonality -> {
            ClassicTopBar(
                title = title,
                subtitle = subtitle,
                leading = leading,
                actions = actions,
                centerTitle = centerTitle,
                insets = insets,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MangaTopBar(
    personality: MangaPersonality,
    title: String,
    titleAccent: String?,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    size: KomiTopBarSize,
    centerTitle: Boolean,
    insets: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = personality.colors
    val isMast = size == KomiTopBarSize.Masthead
    val titleSize = if (isMast) 27.sp else 22.sp
    val annotatedTitle =
        remember(title, titleAccent, colors.primary) {
            mangaTitle(title, titleAccent, colors.primary)
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (isMast) colors.background else colors.surface)
                .clipToBounds()
                .speedLineWash(color = colors.onSurface)
                .then(if (insets) Modifier.statusBarsPadding() else Modifier)
                .then(if (isMast) Modifier else Modifier.heightIn(min = 54.dp))
                .padding(
                    start = if (isMast) 16.dp else 8.dp,
                    end = if (isMast) 16.dp else 8.dp,
                    top = if (isMast) 8.dp else 0.dp,
                    bottom = if (isMast) 12.dp else 0.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        leading?.invoke()

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            MaterialText(
                text = annotatedTitle,
                style = personality.type.title.copy(fontSize = titleSize),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                KomiText(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    role = KomiTextRole.Label,
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        actions?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = it,
            )
        }
    }
}

private fun mangaTitle(
    title: String,
    accentSub: String?,
    accent: Color,
): AnnotatedString {
    val upper = title.uppercase()
    if (accentSub.isNullOrEmpty()) return AnnotatedString(upper)
    val accentUpper = accentSub.uppercase()
    val idx = upper.indexOf(accentUpper)
    if (idx < 0) return AnnotatedString(upper)
    return buildAnnotatedString {
        append(upper.substring(0, idx))
        withStyle(SpanStyle(color = accent)) {
            append(upper.substring(idx, idx + accentUpper.length))
        }
        append(upper.substring(idx + accentUpper.length))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicTopBar(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    centerTitle: Boolean,
    insets: Boolean,
    modifier: Modifier = Modifier,
) {
    val windowInsets = if (insets) TopAppBarDefaults.windowInsets else WindowInsets(0, 0, 0, 0)
    val titleContent: @Composable () -> Unit = {
        Column {
            KomiText(
                text = title,
                role = KomiTextRole.Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                KomiText(
                    text = it,
                    role = KomiTextRole.Label,
                    color = LocalPersonality.current.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    uppercase = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val navigationIcon: @Composable () -> Unit = { leading?.invoke() }
    val actionsContent: @Composable RowScope.() -> Unit = {
        actions?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = it,
            )
        }
    }

    if (centerTitle) {
        CenterAlignedTopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actionsContent,
            windowInsets = windowInsets,
            modifier = modifier,
        )
    } else {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actionsContent,
            windowInsets = windowInsets,
            modifier = modifier,
        )
    }
}

@Composable
private fun PreviewMasthead() {
    KomiTopBar(
        title = "Komi Store",
        titleAccent = "Store",
        subtitle = "発見 · DISCOVER APPS",
        leading = {
            KomiIconButton(
                Icons.Default.Search,
                "Search",
                {},
                variant = KomiButtonVariant.Outline,
            )
        },
        actions = {
            KomiIconButton(
                Icons.Default.Search,
                "Search",
                {},
                variant = KomiButtonVariant.Tonal,
            )
        },
    )
}

@Composable
private fun PreviewCompact() {
    KomiTopBar(
        title = "詳細 · DETAILS",
        size = KomiTopBarSize.Compact,
        centerTitle = true,
        leading = {
            KomiIconButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Back",
                {},
                variant = KomiButtonVariant.Outline,
            )
        },
        actions = {
            KomiIconButton(
                Icons.Default.Search,
                "Search",
                {},
                variant = KomiButtonVariant.Outline,
            )
        },
    )
}

@Preview
@Composable
private fun KomiTopBarMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewMasthead() }
}

@Preview
@Composable
private fun KomiTopBarMangaCompactPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewCompact() }
}

@Preview
@Composable
private fun KomiTopBarMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN,
        ),
    ) { PreviewMasthead() }
}

@Preview
@Composable
private fun KomiTopBarClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewMasthead() }
}

@Preview
@Composable
private fun KomiTopBarClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewCompact() }
}
