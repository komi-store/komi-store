package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiSegmented
import zed.rainxch.core.presentation.components.buttons.KomiSegmentedItem
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.proxy_host
import zed.rainxch.githubstore.core.presentation.res.proxy_password
import zed.rainxch.githubstore.core.presentation.res.proxy_port
import zed.rainxch.githubstore.core.presentation.res.proxy_save
import zed.rainxch.githubstore.core.presentation.res.proxy_username
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_custom
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_main_section
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_mode_http
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_mode_no_proxy
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_mode_socks
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_mode_system
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_overrides_section
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_scope_discovery_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_scope_download_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_scope_translation_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_test
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_use_main
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.shell.SettingsExpandableRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.connection.PasteProxyUrlSheet
import zed.rainxch.tweaks.presentation.model.ProxyScopeFormState
import zed.rainxch.tweaks.presentation.model.ProxyType

@Composable
fun ConnectionSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val master = state.masterProxyForm

    SettingsGroup(modifier = modifier) {
        SettingsExpandableRow(
            title = stringResource(Res.string.tweaks_connection_main_section),
            subtitle = proxySummary(master),
            expanded = state.connectionMasterExpanded,
            onToggle = { onAction(TweaksAction.OnConnectionMasterExpandToggle) },
        ) {
            ProxyTypeSegmented(
                selected = master.type,
                onSelect = { onAction(TweaksAction.OnMasterProxyTypeSelected(it)) },
            )
            AnimatedVisibility(visible = master.type == ProxyType.HTTP || master.type == ProxyType.SOCKS) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    ProxyFields(
                        form = master,
                        onHost = { onAction(TweaksAction.OnMasterProxyHostChanged(it)) },
                        onPort = { onAction(TweaksAction.OnMasterProxyPortChanged(it)) },
                        onUser = { onAction(TweaksAction.OnMasterProxyUsernameChanged(it)) },
                        onPass = { onAction(TweaksAction.OnMasterProxyPasswordChanged(it)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KomiButton(
                            onClick = { onAction(TweaksAction.OnConnectionPasteSheetOpen) },
                            label = stringResource(Res.string.tweaks_connection_paste_url),
                            variant = KomiButtonVariant.Outline,
                            size = KomiButtonSize.Sm,
                        )
                        KomiButton(
                            onClick = { onAction(TweaksAction.OnMasterProxyTest) },
                            label = stringResource(Res.string.tweaks_connection_test),
                            variant = KomiButtonVariant.Outline,
                            size = KomiButtonSize.Sm,
                            enabled = !master.isTestInProgress,
                            loading = master.isTestInProgress,
                        )
                        KomiButton(
                            onClick = { onAction(TweaksAction.OnMasterProxySave) },
                            label = stringResource(Res.string.proxy_save),
                            variant = KomiButtonVariant.Primary,
                            size = KomiButtonSize.Sm,
                            enabled = !master.isTestInProgress,
                        )
                    }
                }
            }
        }

        ProxyScope.entries.forEachIndexed { index, scope ->
            val useMain = state.useMain(scope)
            val form = state.formFor(scope)
            SettingsExpandableRow(
                title = scopeTitle(scope),
                subtitle = stringResource(Res.string.tweaks_connection_overrides_section),
                expanded = state.expandedProxyScope == scope,
                onToggle = { onAction(TweaksAction.OnProxyScopeExpandToggle(scope)) },
                last = index == ProxyScope.entries.lastIndex,
            ) {
                KomiSegmented(
                    selected = useMain,
                    onSelect = { onAction(TweaksAction.OnScopeUseMainToggled(scope, it)) },
                    size = KomiIconButtonSize.Sm,
                    items =
                        persistentListOf(
                            KomiSegmentedItem(value = true, title = stringResource(Res.string.tweaks_connection_use_main)),
                            KomiSegmentedItem(value = false, title = stringResource(Res.string.tweaks_connection_custom)),
                        ),
                )
                AnimatedVisibility(visible = !useMain) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        ProxyTypeSegmented(
                            selected = form.type,
                            onSelect = { onAction(TweaksAction.OnProxyTypeSelected(scope, it)) },
                        )
                        AnimatedVisibility(visible = form.type == ProxyType.HTTP || form.type == ProxyType.SOCKS) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                ProxyFields(
                                    form = form,
                                    onHost = { onAction(TweaksAction.OnProxyHostChanged(scope, it)) },
                                    onPort = { onAction(TweaksAction.OnProxyPortChanged(scope, it)) },
                                    onUser = { onAction(TweaksAction.OnProxyUsernameChanged(scope, it)) },
                                    onPass = { onAction(TweaksAction.OnProxyPasswordChanged(scope, it)) },
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    KomiButton(
                                        onClick = { onAction(TweaksAction.OnProxyTest(scope)) },
                                        label = stringResource(Res.string.tweaks_connection_test),
                                        variant = KomiButtonVariant.Outline,
                                        size = KomiButtonSize.Sm,
                                        enabled = !form.isTestInProgress,
                                        loading = form.isTestInProgress,
                                    )
                                    KomiButton(
                                        onClick = { onAction(TweaksAction.OnProxySave(scope)) },
                                        label = stringResource(Res.string.proxy_save),
                                        variant = KomiButtonVariant.Tonal,
                                        size = KomiButtonSize.Sm,
                                        enabled = !form.isTestInProgress,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.connectionPasteSheetOpen) {
        PasteProxyUrlSheet(
            input = state.proxyPasteUrlInput,
            isError = state.proxyPasteUrlError,
            onInputChange = { onAction(TweaksAction.OnProxyPasteUrlChanged(it)) },
            onSubmit = { onAction(TweaksAction.OnProxyPasteUrlSubmit) },
            onDismiss = { onAction(TweaksAction.OnConnectionPasteSheetDismiss) },
        )
    }
}

@Composable
private fun proxySummary(form: ProxyScopeFormState): String =
    when (form.type) {
        ProxyType.NONE -> {
            stringResource(Res.string.tweaks_connection_mode_no_proxy)
        }

        ProxyType.SYSTEM -> {
            stringResource(Res.string.tweaks_connection_mode_system)
        }

        ProxyType.HTTP, ProxyType.SOCKS -> {
            (
                if (form.type == ProxyType.HTTP) {
                    stringResource(Res.string.tweaks_connection_mode_http)
                } else {
                    stringResource(Res.string.tweaks_connection_mode_socks)
                }
            ) +
                form.host
                    .takeIf { it.isNotBlank() }
                    ?.let { " · $it" }
                    .orEmpty()
        }
    }

@Composable
private fun scopeTitle(scope: ProxyScope): String =
    when (scope) {
        ProxyScope.DISCOVERY -> stringResource(Res.string.tweaks_connection_scope_discovery_title)
        ProxyScope.DOWNLOAD -> stringResource(Res.string.tweaks_connection_scope_download_title)
        ProxyScope.TRANSLATION -> stringResource(Res.string.tweaks_connection_scope_translation_title)
    }

@Composable
private fun ProxyTypeSegmented(
    selected: ProxyType,
    onSelect: (ProxyType) -> Unit,
) {
    KomiSegmented(
        selected = selected,
        onSelect = onSelect,
        size = KomiIconButtonSize.Sm,
        items =
            persistentListOf(
                KomiSegmentedItem(value = ProxyType.NONE, title = stringResource(Res.string.tweaks_connection_mode_no_proxy)),
                KomiSegmentedItem(value = ProxyType.SYSTEM, title = stringResource(Res.string.tweaks_connection_mode_system)),
                KomiSegmentedItem(value = ProxyType.HTTP, title = stringResource(Res.string.tweaks_connection_mode_http)),
                KomiSegmentedItem(value = ProxyType.SOCKS, title = stringResource(Res.string.tweaks_connection_mode_socks)),
            ),
    )
}

@Composable
private fun ProxyFields(
    form: ProxyScopeFormState,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onUser: (String) -> Unit,
    onPass: (String) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KomiTextField(
                value = form.host,
                onValueChange = onHost,
                modifier = Modifier.weight(2f),
                label = stringResource(Res.string.proxy_host),
            )
            KomiTextField(
                value = form.port,
                onValueChange = onPort,
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.proxy_port),
                keyboardType = KeyboardType.Number,
            )
        }
        Spacer(Modifier.height(8.dp))
        KomiTextField(
            value = form.username,
            onValueChange = onUser,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.proxy_username),
        )
        Spacer(Modifier.height(8.dp))
        KomiTextField(
            value = form.password,
            onValueChange = onPass,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.proxy_password),
            password = true,
            keyboardType = KeyboardType.Password,
        )
    }
}
