package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.utils.displayName
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiSegmented
import zed.rainxch.core.presentation.components.buttons.KomiSegmentedItem
import zed.rainxch.githubstore.core.presentation.res.repo_layout_grid
import zed.rainxch.githubstore.core.presentation.res.repo_layout_list
import zed.rainxch.githubstore.core.presentation.res.repo_layout_title

private enum class ModeChoice { LIGHT, DARK, SYSTEM }

private fun isDarkToChoice(value: Boolean?): ModeChoice =
    when (value) {
        true -> ModeChoice.DARK
        false -> ModeChoice.LIGHT
        null -> ModeChoice.SYSTEM
    }

private fun choiceToIsDark(choice: ModeChoice): Boolean? =
    when (choice) {
        ModeChoice.DARK -> true
        ModeChoice.LIGHT -> false
        ModeChoice.SYSTEM -> null
    }

private fun showsAmoledToggle(
    personality: AppPersonality,
    isDarkTheme: Boolean?,
): Boolean =
    when (personality) {
        // AMOLED forces near-black surfaces — only meaningful where a dark surface is
        // actually rendered. Classic Light never applies it (hide); Dark + System keep it.
        AppPersonality.CLASSIC -> isDarkTheme != false
        // Manga's Night paper is inherently pure-black, so there is nothing to toggle.
        AppPersonality.MANGA -> false
    }

@Composable
fun appearanceSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val desktop = isDesktop()
    val amoledVisible = showsAmoledToggle(state.selectedPersonality, state.isDarkTheme)
    SettingsGroup(modifier = modifier) {
        when (state.selectedPersonality) {
            AppPersonality.MANGA -> {
                SettingsRow(
                    title = stringResource(Res.string.appearance_section_paper),
                    trailing = {
                        KomiSegmented(
                            selected = state.mangaPaper,
                            onSelect = { onAction(TweaksAction.OnMangaPaperSelected(it)) },
                            size = KomiIconButtonSize.Sm,
                            items =
                                persistentListOf(
                                    KomiSegmentedItem(value = MangaPaperId.DAY, title = stringResource(Res.string.paper_day)),
                                    KomiSegmentedItem(value = MangaPaperId.NIGHT, title = stringResource(Res.string.paper_night)),
                                    KomiSegmentedItem(value = MangaPaperId.NORD, title = stringResource(Res.string.paper_nord)),
                                ),
                        )
                    },
                )
            }

            AppPersonality.CLASSIC -> {
                SettingsRow(
                    title = stringResource(Res.string.appearance_section_mode),
                    trailing = {
                        KomiSegmented(
                            selected = isDarkToChoice(state.isDarkTheme),
                            onSelect = { onAction(TweaksAction.OnDarkThemeChange(choiceToIsDark(it))) },
                            size = KomiIconButtonSize.Sm,
                            items =
                                persistentListOf(
                                    KomiSegmentedItem(value = ModeChoice.LIGHT, title = stringResource(Res.string.theme_light)),
                                    KomiSegmentedItem(value = ModeChoice.DARK, title = stringResource(Res.string.theme_dark)),
                                    KomiSegmentedItem(value = ModeChoice.SYSTEM, title = stringResource(Res.string.theme_system)),
                                ),
                        )
                    },
                )

                if (amoledVisible) {
                    SettingsRow(
                        title = stringResource(Res.string.amoled_black_theme),
                        subtitle = stringResource(Res.string.amoled_black_description),
                        trailing = {
                            KomiSwitch(
                                checked = state.isAmoledThemeEnabled,
                                onCheckedChange = { onAction(TweaksAction.OnAmoledThemeToggled(it)) },
                            )
                        },
                    )
                }
            }
        }

        // How Explore, Top Charts and Search lay out repositories.
        SettingsRow(
            title = stringResource(Res.string.repo_layout_title),
            last = !desktop,
            trailing = {
                KomiSegmented(
                    selected = state.isRepoGridLayout,
                    onSelect = { onAction(TweaksAction.OnRepoLayoutSelected(it)) },
                    size = KomiIconButtonSize.Sm,
                    items =
                        persistentListOf(
                            KomiSegmentedItem(value = false, title = stringResource(Res.string.repo_layout_list)),
                            KomiSegmentedItem(value = true, title = stringResource(Res.string.repo_layout_grid)),
                        ),
                )
            },
        )

        if (desktop) {
            SettingsRow(
                title = stringResource(Res.string.scrollbar_option_title),
                subtitle = stringResource(Res.string.scrollbar_option_description),
                trailing = {
                    KomiSwitch(
                        checked = state.isScrollbarEnabled,
                        onCheckedChange = { onAction(TweaksAction.OnScrollbarToggled(it)) },
                    )
                },
            )

            SettingsRow(
                title = stringResource(Res.string.content_width_title),
                subtitle = stringResource(Res.string.content_width_description),
                last = true,
                trailing = {
                    KomiSegmented(
                        selected = state.contentWidth,
                        onSelect = { onAction(TweaksAction.OnContentWidthSelected(it)) },
                        size = KomiIconButtonSize.Sm,
                        items = ContentWidth.entries.map { KomiSegmentedItem(value = it, title = it.displayName) }.toImmutableList(),
                    )
                },
            )
        }
    }
}
