package zed.rainxch.tweaks.presentation.hosttokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.account.ForgeKind
import zed.rainxch.core.domain.model.account.HostToken
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.settingsRowDivider
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_title
import zed.rainxch.githubstore.core.presentation.res.host_tokens_action_add
import zed.rainxch.githubstore.core.presentation.res.host_tokens_action_back
import zed.rainxch.githubstore.core.presentation.res.host_tokens_action_cancel
import zed.rainxch.githubstore.core.presentation.res.host_tokens_action_delete
import zed.rainxch.githubstore.core.presentation.res.host_tokens_action_save
import zed.rainxch.githubstore.core.presentation.res.host_tokens_compose_add_title
import zed.rainxch.githubstore.core.presentation.res.host_tokens_compose_detected
import zed.rainxch.githubstore.core.presentation.res.host_tokens_compose_field_forge_address
import zed.rainxch.githubstore.core.presentation.res.host_tokens_compose_replace_hint
import zed.rainxch.githubstore.core.presentation.res.host_tokens_compose_replace_title
import zed.rainxch.githubstore.core.presentation.res.host_tokens_compose_will_connect
import zed.rainxch.githubstore.core.presentation.res.host_tokens_empty_subtitle
import zed.rainxch.githubstore.core.presentation.res.host_tokens_empty_title
import zed.rainxch.githubstore.core.presentation.res.host_tokens_field_display_name
import zed.rainxch.githubstore.core.presentation.res.host_tokens_field_token
import zed.rainxch.githubstore.core.presentation.res.host_tokens_oauth_coexistence
import zed.rainxch.githubstore.core.presentation.res.host_tokens_picker_open_page
import zed.rainxch.githubstore.core.presentation.res.host_tokens_picker_other
import zed.rainxch.githubstore.core.presentation.res.host_tokens_picker_other_subtitle
import zed.rainxch.githubstore.core.presentation.res.host_tokens_picker_paste
import zed.rainxch.githubstore.core.presentation.res.host_tokens_picker_title
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_edit_label
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_invalid_short
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_menu
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_open_token_page
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_replace_token
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_valid_rate
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_valid_short
import zed.rainxch.githubstore.core.presentation.res.host_tokens_row_validate
import zed.rainxch.githubstore.core.presentation.res.host_tokens_title
import zed.rainxch.githubstore.core.presentation.res.host_tokens_undo_action
import zed.rainxch.githubstore.core.presentation.res.host_tokens_undo_snackbar
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker

@Composable
fun HostTokensRoot(
    onNavigateBack: () -> Unit,
    viewModel: HostTokensViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HostTokensEvent.Message ->
                coroutineScope.launch { toastState.show(event.text) }
            is HostTokensEvent.OpenUrl ->
                runCatching { uriHandler.openUri(event.url) }
            is HostTokensEvent.TokenDeletedWithUndo ->
                coroutineScope.launch {
                    toastState.warning(
                        message = getString(Res.string.host_tokens_undo_snackbar, event.deleted.host),
                        actionLabel = getString(Res.string.host_tokens_undo_action),
                        onAction = {
                            viewModel.onAction(HostTokensAction.OnUndoDelete)
                        }
                    )
                }
        }
    }

    KomiScaffold(
        grid = true,
        screentone = true,
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.host_tokens_title),
                titleAccent = tweaksKicker(TweaksDecorSlot.Tokens),
                onBack = onNavigateBack,
            )
        },
        toastState = toastState,
        floatingActionButton = {
            if (state.tokens.isNotEmpty()) {
                KomiFab(
                    onClick = { viewModel.onAction(HostTokensAction.OnAddClicked) },
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.host_tokens_action_add),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.isOAuthSignedInToGithub) {
                OAuthCoexistenceNote()
                Spacer(Modifier.height(12.dp))
            }
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { KomiCircularProgress() }
                }
                state.tokens.isEmpty() -> {
                    EmptyStatePicker(
                        presetForges = visiblePresetForges(state),
                        onPickPreset = { kind -> viewModel.onAction(HostTokensAction.OnPickPresetForge(kind)) },
                        onPickOther = { viewModel.onAction(HostTokensAction.OnPickOtherForge) },
                        onOpenTokenCreationPage = { kind ->
                            viewModel.onAction(HostTokensAction.OnOpenTokenCreationPage(kind))
                        },
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState) {
                        item {
                            SettingsGroup {
                                state.tokens.forEachIndexed { index, token ->
                                    TokenRow(
                                        token = token,
                                        isValidating = token.host in state.validatingHosts,
                                        validation = state.validationByHost[token.host],
                                        last = index == state.tokens.lastIndex,
                                        onValidate = { viewModel.onAction(HostTokensAction.OnValidate(token.host)) },
                                        onDelete = { viewModel.onAction(HostTokensAction.OnDelete(token.host)) },
                                        onReplace = { viewModel.onAction(HostTokensAction.OnReplaceToken(token)) },
                                        onEditLabel = { viewModel.onAction(HostTokensAction.OnEditLabel(token)) },
                                        onOpenManagePage = { kind ->
                                            viewModel.onAction(HostTokensAction.OnOpenTokenCreationPage(kind))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val mode = state.draftMode) {
        DraftMode.Closed -> Unit
        DraftMode.Picker -> {
            PickerDialog(
                presetForges = visiblePresetForges(state),
                onPickPreset = { kind -> viewModel.onAction(HostTokensAction.OnPickPresetForge(kind)) },
                onPickOther = { viewModel.onAction(HostTokensAction.OnPickOtherForge) },
                onOpenTokenCreationPage = { kind ->
                    viewModel.onAction(HostTokensAction.OnOpenTokenCreationPage(kind))
                },
                onDismiss = { viewModel.onAction(HostTokensAction.OnAddDismiss) },
            )
        }
        is DraftMode.Compose -> {
            AddTokenDialog(
                state = state,
                replacingExisting = mode.replacingExisting,
                onAction = viewModel::onAction,
            )
        }
    }
}

private fun visiblePresetForges(state: HostTokensState): List<ForgeKind> {

    val storedHosts = state.tokens.map { it.host }.toSet()
    return ForgeKind.entries.filter { kind ->
        when {
            kind.tokenHost in storedHosts -> true
            kind == ForgeKind.GITHUB && state.isOAuthSignedInToGithub -> false
            else -> true
        }
    }
}

@Composable
private fun OAuthCoexistenceNote() {
    val colors = LocalPersonality.current.colors
    KomiSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiIcon(
                Icons.Default.Info,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            KomiText(
                text = stringResource(Res.string.host_tokens_oauth_coexistence),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyStatePicker(
    presetForges: List<ForgeKind>,
    onPickPreset: (ForgeKind) -> Unit,
    onPickOther: () -> Unit,
    onOpenTokenCreationPage: (ForgeKind) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val colors = LocalPersonality.current.colors
            Spacer(Modifier.height(8.dp))
            KomiText(
                text = stringResource(Res.string.host_tokens_empty_title),
                role = KomiTextRole.SectionTitle,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            KomiText(
                text = stringResource(Res.string.host_tokens_empty_subtitle),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            KomiText(
                text = stringResource(Res.string.host_tokens_picker_title),
                role = KomiTextRole.Overline,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        item {
            SettingsGroup {
                presetForges.forEach { kind ->
                    PresetForgeCard(
                        kind = kind,
                        onPick = { onPickPreset(kind) },
                        onOpenTokenCreationPage = { onOpenTokenCreationPage(kind) },
                        last = false,
                    )
                }
                OtherForgeCard(onPick = onPickOther)
            }
        }
    }
}

@Composable
private fun PresetForgeCard(
    kind: ForgeKind,
    onPick: () -> Unit,
    onOpenTokenCreationPage: () -> Unit,
    last: Boolean,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowDivider(colors.divider, show = !last)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiIcon(
                Icons.Default.VpnKey,
                contentDescription = null,
                tint = colors.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = kind.displayName,
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                )
                KomiText(
                    text = kind.tokenHost,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            KomiButton(
                onClick = onOpenTokenCreationPage,
                label = stringResource(Res.string.host_tokens_picker_open_page),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
                leadingIcon = Icons.Default.OpenInBrowser,
            )
            KomiButton(
                onClick = onPick,
                label = stringResource(Res.string.host_tokens_picker_paste),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        }
    }
}

@Composable
private fun OtherForgeCard(onPick: () -> Unit) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiIcon(
            Icons.Default.VpnKey,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = stringResource(Res.string.host_tokens_picker_other),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
            KomiText(
                text = stringResource(Res.string.host_tokens_picker_other_subtitle),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickerDialog(
    presetForges: List<ForgeKind>,
    onPickPreset: (ForgeKind) -> Unit,
    onPickOther: () -> Unit,
    onOpenTokenCreationPage: (ForgeKind) -> Unit,
    onDismiss: () -> Unit,
) {
    KomiDialog(
        onDismissRequest = onDismiss,
        title = { KomiText(stringResource(Res.string.host_tokens_picker_title), role = KomiTextRole.CardTitle) },
        text = {
            SettingsGroup {
                presetForges.forEach { kind ->
                    PresetForgeCard(
                        kind = kind,
                        onPick = { onPickPreset(kind) },
                        onOpenTokenCreationPage = { onOpenTokenCreationPage(kind) },
                        last = false,
                    )
                }
                OtherForgeCard(onPick = onPickOther)
            }
        },
        confirmButton = {
            KomiButton(
                onClick = onDismiss,
                label = stringResource(Res.string.host_tokens_action_cancel),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
    )
}

@Composable
private fun TokenRow(
    token: HostToken,
    isValidating: Boolean,
    validation: ValidationLine?,
    onValidate: () -> Unit,
    onDelete: () -> Unit,
    onReplace: () -> Unit,
    onEditLabel: () -> Unit,
    onOpenManagePage: (ForgeKind) -> Unit,
    last: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val forge = ForgeKind.fromHost(token.host)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowDivider(colors.divider, show = !last)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            KomiIcon(
                Icons.Default.VpnKey,
                contentDescription = null,
                tint = colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = forge?.displayName ?: token.host,
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                )
                val secondary = when {
                    validation?.isSuccess == true -> {
                        val login = validation.login ?: token.displayName ?: token.host
                        if (validation.rateLimitRemaining != null) {
                            stringResource(Res.string.host_tokens_row_valid_short, login) +
                                " · " + stringResource(
                                    Res.string.host_tokens_row_valid_rate,
                                    validation.rateLimitRemaining,
                                )
                        } else {
                            stringResource(Res.string.host_tokens_row_valid_short, login)
                        }
                    }
                    validation != null -> stringResource(
                        Res.string.host_tokens_row_invalid_short,
                        validation.errorMessage.orEmpty(),
                    )
                    else -> token.displayName ?: token.host
                }
                KomiText(
                    text = secondary,
                    role = KomiTextRole.Body,
                    color = if (validation != null && !validation.isSuccess) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
                )
            }
            if (isValidating) {
                KomiCircularProgress(modifier = Modifier.size(20.dp))
            } else {
                KomiIconButton(
                    icon = Icons.Default.Check,
                    contentDescription = stringResource(Res.string.host_tokens_row_validate),
                    onClick = onValidate,
                    variant = KomiButtonVariant.Text,
                )
            }
            val menuEntries = buildList {
                add(KomiMenuItem(id = "edit", label = stringResource(Res.string.host_tokens_row_edit_label)))
                add(KomiMenuItem(id = "replace", label = stringResource(Res.string.host_tokens_row_replace_token)))
                if (forge != null) {
                    add(
                        KomiMenuItem(
                            id = "open",
                            label = stringResource(Res.string.host_tokens_row_open_token_page, forge.displayName),
                        ),
                    )
                }
                add(KomiMenuItem(id = "delete", label = stringResource(Res.string.host_tokens_action_delete)))
            }.toImmutableList()
            KomiDropdown(
                entries = menuEntries,
                onSelect = { item ->
                    when (item.id) {
                        "edit" -> onEditLabel()
                        "replace" -> onReplace()
                        "open" -> forge?.let { onOpenManagePage(it) }
                        "delete" -> onDelete()
                    }
                },
                trigger = { onClick ->
                    KomiIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.host_tokens_row_menu),
                        onClick = onClick,
                        variant = KomiButtonVariant.Text,
                    )
                },
            )
        }
}

@Composable
private fun AddTokenDialog(
    state: HostTokensState,
    replacingExisting: HostToken?,
    onAction: (HostTokensAction) -> Unit,
) {
    val title = if (replacingExisting != null) {
        stringResource(Res.string.host_tokens_compose_replace_title, replacingExisting.host)
    } else {
        stringResource(Res.string.host_tokens_compose_add_title)
    }
    KomiDialog(
        onDismissRequest = { onAction(HostTokensAction.OnAddDismiss) },
        title = { KomiText(title, role = KomiTextRole.CardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.draftForge == null) {
                    val hostSupporting = state.draftHostError?.let { stringResource(it) }
                        ?: state.draftHostNormalized
                            .takeIf { it.isNotBlank() && it != state.draftHost.trim() }
                            ?.let { normalized ->
                                stringResource(
                                    Res.string.host_tokens_compose_will_connect,
                                    normalized,
                                )
                            }
                    KomiTextField(
                        value = state.draftHost,
                        onValueChange = { onAction(HostTokensAction.OnDraftHostChanged(it)) },
                        label = stringResource(Res.string.host_tokens_compose_field_forge_address),
                        error = if (state.draftHostError != null) hostSupporting else null,
                        helper = if (state.draftHostError == null) hostSupporting else null,
                    )
                }
                val tokenSupporting = state.draftTokenError?.let { stringResource(it) }
                    ?: state.draftDetectedTokenKind?.let { kind ->
                        stringResource(Res.string.host_tokens_compose_detected, kind)
                    }
                    ?: replacingExisting?.let {
                        stringResource(Res.string.host_tokens_compose_replace_hint)
                    }
                KomiTextField(
                    value = state.draftToken,
                    onValueChange = { onAction(HostTokensAction.OnDraftTokenChanged(it)) },
                    label = stringResource(Res.string.host_tokens_field_token),
                    error = if (state.draftTokenError != null) tokenSupporting else null,
                    helper = if (state.draftTokenError == null) tokenSupporting else null,
                    password = true,
                )
                KomiTextField(
                    value = state.draftDisplayName,
                    onValueChange = { onAction(HostTokensAction.OnDraftDisplayNameChanged(it)) },
                    label = stringResource(Res.string.host_tokens_field_display_name),
                )
            }
        },
        confirmButton = {
            KomiButton(
                onClick = { onAction(HostTokensAction.OnAddConfirm) },
                label = stringResource(Res.string.host_tokens_action_save),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
        dismissButton = {
            KomiButton(
                onClick = { onAction(HostTokensAction.OnAddDismiss) },
                label = stringResource(Res.string.host_tokens_action_cancel),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
    )
}
