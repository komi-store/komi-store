package zed.rainxch.tweaks.presentation.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_licenses_intro_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_licenses_intro_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_licenses_load_failed
import zed.rainxch.githubstore.core.presentation.res.tweaks_licenses_title

@Composable
fun LicensesRoot(
    onNavigateBack: () -> Unit,
    viewModel: LicensesViewModel = koinViewModel(),
) {
    val toastState = rememberKomiToastState()
    val uriHandler = LocalUriHandler.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.tweaks_licenses_title),
                onBack = onNavigateBack,
            )
        },
        toastState = toastState,
        grid = true,
        screentone = true
    ) { innerPadding ->
        val colors = LocalPersonality.current.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            KomiSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                KomiText(
                    text = stringResource(Res.string.tweaks_licenses_intro_title),
                    role = KomiTextRole.Body,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                KomiText(
                    text = stringResource(Res.string.tweaks_licenses_intro_body),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiCircularProgress()
                }
            }

            state.loadError -> {
                KomiText(
                    text = stringResource(Res.string.tweaks_licenses_load_failed),
                    role = KomiTextRole.Body,
                    color = LocalPersonality.current.colors.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            state.libraries != null -> {
                state.libraries?.let { libraryEntries ->
                    libraryEntries.forEach { library ->
                        LibraryRow(
                            library = library,
                            onClick = {
                                runCatching { uriHandler.openUri(library.url) }
                            }
                        )

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun LibraryRow(library: LibraryEntry, onClick: () -> Unit) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text = library.name,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            KomiText(
                text = library.license,
                role = KomiTextRole.Mono,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
