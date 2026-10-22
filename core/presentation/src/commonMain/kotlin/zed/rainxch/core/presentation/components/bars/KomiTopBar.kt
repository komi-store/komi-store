package zed.rainxch.core.presentation.components.bars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.appLocale
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.speedLineWash
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.navigate_back
import androidx.compose.material3.Text as MaterialText

@Composable
fun KomiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleAccent: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    size: KomiTopBarSize = KomiTopBarSize.Masthead,
    centerTitle: Boolean = false,
    insets: Boolean = true,
) {
    val personality = LocalPersonality.current
    // A Classic tab screen has no top bar: its search field and actions go to the tab card.
    val tabCard = LocalTabCardSlot.current?.takeIf { personality is ClassicPersonality }
    // Elsewhere Classic hands the back action and the actions to its KomiScaffold, which draws
    // them in a KomiActionBar at the bottom, so the top bar keeps only the title. A search field
    // keeps its actions beside it.
    val scaffoldChrome = LocalScaffoldChrome.current?.takeIf { personality is ClassicPersonality && search == null }
    if (scaffoldChrome != null) HandToScaffold(chrome = scaffoldChrome, onBack = onBack, actions = actions)
    val topBarBack: (@Composable () -> Unit)? =
        onBack?.takeIf { scaffoldChrome == null }?.let { back -> @Composable { TopBarBack(back) } }
    val resolvedLeading = leading ?: topBarBack
    val topBarActions = actions.takeIf { scaffoldChrome == null }
    when {
        tabCard != null -> {
            HandToTabCard(slot = tabCard, search = search, actions = actions)
        }

        search != null -> {
            SearchTopBar(
                search = search,
                leading = resolvedLeading,
                actions = actions,
                insets = insets,
                modifier = modifier,
            )
        }

        personality is MangaPersonality -> {
            MangaTopBar(
                personality = personality,
                title = title,
                titleAccent = titleAccent,
                subtitle = subtitle,
                leading = resolvedLeading,
                actions = actions,
                size = size,
                centerTitle = centerTitle,
                insets = insets,
                modifier = modifier,
            )
        }

        personality is ClassicPersonality -> {
            ClassicTopBar(
                title = title,
                subtitle = subtitle,
                leading = resolvedLeading,
                actions = topBarActions,
                size = size,
                centerTitle = centerTitle,
                insets = insets,
                modifier = modifier,
            )
        }
    }
}

// A search screen's field takes the title's place, with its actions beside it.
@Composable
private fun SearchTopBar(
    search: @Composable () -> Unit,
    leading: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    insets: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (insets) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Box(modifier = Modifier.weight(1f)) { search() }
        actions?.invoke(this)
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
    val locale = appLocale()
    val annotatedTitle =
        remember(title, titleAccent, colors.primary, locale) {
            mangaTitle(title.toUpperCase(locale), titleAccent?.toUpperCase(locale), colors.primary)
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
            modifier = Modifier.weight(1f).testTag(KomiTestTags.TOP_BAR_CONTENT),
            horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            MaterialText(
                text = annotatedTitle,
                style = personality.type.screenTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                KomiText(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    role = KomiTextRole.Overline,
                    color = colors.onSurfaceVariant,
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
    if (accentSub.isNullOrEmpty()) return AnnotatedString(title)
    val idx = title.indexOf(accentSub)
    if (idx < 0) return AnnotatedString(title)
    return buildAnnotatedString {
        append(title.substring(0, idx))
        withStyle(SpanStyle(color = accent)) {
            append(title.substring(idx, idx + accentSub.length))
        }
        append(title.substring(idx + accentSub.length))
    }
}

// One row on the page itself: the title right under the status bar, with no container and no
// tint when content scrolls under it. Inside a KomiScaffold it slides away as the content scrolls
// down and returns on the way up.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicTopBar(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    size: KomiTopBarSize,
    centerTitle: Boolean,
    insets: Boolean,
    modifier: Modifier = Modifier,
) {
    val windowInsets = if (insets) TopAppBarDefaults.windowInsets else WindowInsets(0, 0, 0, 0)
    // With nothing to show (Details hands everything to the action bar), only the status bar inset.
    val hasTitle = title.isNotBlank() || subtitle != null
    if (!hasTitle && leading == null && actions == null) {
        Spacer(modifier = modifier.windowInsetsTopHeight(windowInsets))
        return
    }
    val chrome = LocalScaffoldChrome.current
    if (chrome != null) {
        DisposableEffect(chrome) {
            chrome.barAttached.value = true
            onDispose { chrome.barAttached.value = false }
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
    val titleRole = if (size == KomiTopBarSize.Masthead) KomiTextRole.Display else KomiTextRole.ScreenTitle
    val titleContent: @Composable () -> Unit = {
        Column {
            KomiText(
                text = title,
                role = titleRole,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                KomiText(
                    text = it,
                    role = KomiTextRole.Caption,
                    color = LocalPersonality.current.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val navigationIcon: @Composable () -> Unit = {
        leading?.let { CompositionLocalProvider(LocalInBar provides true, content = it) }
    }
    val actionsContent: @Composable RowScope.() -> Unit = {
        actions?.let {
            CompositionLocalProvider(LocalInBar provides true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = it,
                )
            }
        }
    }

    if (centerTitle) {
        CenterAlignedTopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actionsContent,
            colors = colors,
            windowInsets = windowInsets,
            scrollBehavior = chrome?.behavior,
            modifier = modifier,
        )
    } else {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actionsContent,
            colors = colors,
            windowInsets = windowInsets,
            scrollBehavior = chrome?.behavior,
            modifier = modifier,
        )
    }
}

@Composable
private fun HandToScaffold(
    chrome: ScaffoldChrome,
    onBack: (() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val currentActions by rememberUpdatedState(actions)
    val hasBack = onBack != null
    val hasActions = actions != null
    DisposableEffect(chrome, hasBack, hasActions) {
        chrome.onBack.value = if (hasBack) ({ currentOnBack?.invoke() }) else null
        chrome.actions.value = if (hasActions) ({ currentActions?.invoke(this) }) else null
        onDispose {
            chrome.onBack.value = null
            chrome.actions.value = null
        }
    }
}

@Composable
private fun TopBarBack(onBack: () -> Unit) {
    KomiIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(Res.string.navigate_back),
        onClick = onBack,
    )
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
