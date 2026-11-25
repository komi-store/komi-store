package zed.rainxch.apps.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.model.LinkStep
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement

@Composable
fun LinkAppBottomSheet(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiSheet(
        onDismiss = { onAction(AppsAction.OnDismissLinkSheet) },
        modifier = modifier,
        placement = KomiSheetPlacement.Bottom,
    ) {
        AnimatedContent(
            targetState = state.linkStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "link_step",
        ) { step ->
            LinkStepContent(step = step, state = state, onAction = onAction)
        }
    }
}

@Composable
private fun LinkStepContent(
    step: LinkStep,
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    when (step) {
        LinkStep.PickApp -> {
            PickAppStep(
                deviceApps = state.filteredDeviceApps,
                searchQuery = state.deviceAppSearchQuery,
                onSearchChange = { onAction(AppsAction.OnDeviceAppSearchChange(it)) },
                onAppSelect = { onAction(AppsAction.OnDeviceAppSelected(it)) },
            )
        }

        LinkStep.SmartMatch -> {
            SmartMatchStep(
                selectedApp = state.selectedDeviceApp,
                loading = state.linkSearchLoading,
                suggestions = state.linkSuggestions,
                error = state.linkSearchError,
                isValidating = state.isValidatingRepo,
                validationStatus = state.linkValidationStatus,
                onSuggestionSelect = { suggestion ->
                    onAction(AppsAction.OnLinkSuggestionSelected(suggestion.owner, suggestion.repo, suggestion.sourceHost))
                },
                onEnterUrlManually = { onAction(AppsAction.OnLinkEnterUrlManually) },
                onRetry = { onAction(AppsAction.OnRetryLinkSearch) },
                onBack = { onAction(AppsAction.OnBackToAppPicker) },
            )
        }

        LinkStep.EnterUrl -> {
            EnterUrlStep(
                selectedApp = state.selectedDeviceApp,
                repoUrl = state.repoUrl,
                isValidating = state.isValidatingRepo,
                validationError = state.repoValidationError,
                validationStatus = state.linkValidationStatus,
                onUrlChange = { onAction(AppsAction.OnRepoUrlChanged(it)) },
                onConfirm = { onAction(AppsAction.OnValidateAndLinkRepo) },
                onBack = { onAction(AppsAction.OnBackToSmartMatch) },
            )
        }

        LinkStep.PickAsset -> {
            PickAssetStep(
                allAssets = state.linkInstallableAssets,
                visibleAssets = state.filteredLinkAssets,
                selectedAsset = state.linkSelectedAsset,
                downloadProgress = state.linkDownloadProgress,
                validationStatus = state.linkValidationStatus,
                validationError = state.repoValidationError,
                filterValue = state.linkAssetFilter,
                filterError = state.linkAssetFilterError,
                fallbackEnabled = state.linkFallbackToOlder,
                onFilterChange = { onAction(AppsAction.OnLinkAssetFilterChanged(it)) },
                onFallbackToggle = { onAction(AppsAction.OnLinkFallbackToggled(it)) },
                onAssetSelect = { onAction(AppsAction.OnLinkAssetSelected(it)) },
                onBack = { onAction(AppsAction.OnBackToEnterUrl) },
            )
        }
    }
}
