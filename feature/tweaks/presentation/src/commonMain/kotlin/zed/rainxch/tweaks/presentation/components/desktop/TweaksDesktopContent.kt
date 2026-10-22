package zed.rainxch.tweaks.presentation.components.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.presentation.personality.manga.decoration.gridPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.section_app_block
import zed.rainxch.githubstore.core.presentation.res.section_connectivity
import zed.rainxch.githubstore.core.presentation.res.section_look_and_feel
import zed.rainxch.githubstore.core.presentation.res.section_privacy_and_data
import zed.rainxch.githubstore.core.presentation.res.tweaks_title
import zed.rainxch.githubstore.core.presentation.res.navigate_back
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.tweaks.presentation.components.sections.appSection
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker
import zed.rainxch.tweaks.presentation.components.sections.connectivitySection
import zed.rainxch.tweaks.presentation.components.sections.lookAndFeelSection
import zed.rainxch.tweaks.presentation.components.sections.privacySection

enum class DesktopSection(val slot: TweaksDecorSlot) {
    LOOK(TweaksDecorSlot.LookAndFeel),
    CONNECTIVITY(TweaksDecorSlot.Connectivity),
    PRIVACY(TweaksDecorSlot.PrivacyData),
    APP(TweaksDecorSlot.App),
}

@Composable
private fun DesktopSection.label(): String =
    when (this) {
        DesktopSection.LOOK -> stringResource(Res.string.section_look_and_feel)
        DesktopSection.CONNECTIVITY -> stringResource(Res.string.section_connectivity)
        DesktopSection.PRIVACY -> stringResource(Res.string.section_privacy_and_data)
        DesktopSection.APP -> stringResource(Res.string.section_app_block)
    }

@Composable
fun TweaksDesktopContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    personality: AppPersonality,
    accent: AccentId,
    onPersonalitySelected: (AppPersonality) -> Unit,
    onAccentSelected: (AccentId) -> Unit,
    currentLanguageLabel: String,
    onOpenLanguage: () -> Unit,
    onOpenFeedback: () -> Unit,
    onNavigateToMirrorPicker: () -> Unit,
    onNavigateToHiddenRepositories: () -> Unit,
    onNavigateToHostTokens: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .background(colors.surface)
                    .drawBehind {
                        val stroke = (if (isManga) 3.dp else 1.dp).toPx()
                        drawLine(
                            color = if (isManga) colors.outline else colors.outlineVariant,
                            start = Offset(size.width - stroke / 2f, 0f),
                            end = Offset(size.width - stroke / 2f, size.height),
                            strokeWidth = stroke,
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KomiIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.navigate_back),
                    onClick = onNavigateBack,
                )
                KomiText(
                    text = stringResource(Res.string.tweaks_title),
                    role = KomiTextRole.ScreenTitle,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            tweaksKicker(TweaksDecorSlot.SettingsNavTitle)?.let { navKicker ->
                KomiText(
                    text = navKicker,
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 14.dp),
                )
            }
            DesktopSection.entries.forEach { entry ->
                NavRow(
                    label = entry.label(),
                    kicker = tweaksKicker(entry.slot),
                    active = state.desktopSection == entry,
                    onClick = { onAction(TweaksAction.OnDesktopSectionSelected(entry)) },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .gridPaper(colors.onSurface, colors.gridOpacity)
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.desktopSection) {
                    DesktopSection.LOOK ->
                        lookAndFeelSection(
                            state = state,
                            onAction = onAction,
                            personality = personality,
                            accent = accent,
                            onPersonalitySelected = onPersonalitySelected,
                            onAccentSelected = onAccentSelected,
                            currentLanguageLabel = currentLanguageLabel,
                            onOpenLanguage = onOpenLanguage,
                        )

                    DesktopSection.CONNECTIVITY ->
                        connectivitySection(
                            state = state,
                            onAction = onAction,
                            onNavigateToMirrorPicker = onNavigateToMirrorPicker,
                        )

                    DesktopSection.PRIVACY ->
                        privacySection(
                            state = state,
                            onAction = onAction,
                            onNavigateToHiddenRepositories = onNavigateToHiddenRepositories,
                            onNavigateToHostTokens = onNavigateToHostTokens,
                        )

                    DesktopSection.APP -> appSection(onOpenFeedback = onOpenFeedback)
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    label: String,
    kicker: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clip(shape)
                .then(
                    if (active) {
                        if (isManga) {
                            Modifier
                                .hardShadow(DpOffset(3.dp, 3.dp), colors.shadow, shape)
                                .background(colors.primary, shape)
                                .border(2.5.dp, colors.outline, shape)
                        } else {
                            Modifier.background(colors.primary, shape)
                        }
                    } else {
                        Modifier
                    },
                )
                .height(40.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.Label,
            color = if (active) colors.onPrimary else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (kicker != null) {
            KomiText(
                text = kicker,
                role = KomiTextRole.Caption,
                color = if (active) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
    }
}
