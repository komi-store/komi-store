package zed.rainxch.tweaks.presentation.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import zed.rainxch.githubstore.core.presentation.res.Res

class LicensesViewModel : ViewModel() {
    private val _state = MutableStateFlow(LicensesState())
    val state = _state.asStateFlow()

    init {
        loadLicenses()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun loadLicenses() {
        viewModelScope.launch {
            runCatching {
                val bytes = Res.readBytes(LICENSES_PATH)
                json
                    .decodeFromString(
                        LicensesPayload.serializer(),
                        bytes.decodeToString(),
                    ).libraries
                    .toImmutableList()
            }.onSuccess { libraries ->
                _state.update {
                    it.copy(libraries = libraries, isLoading = false, loadError = false)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(isLoading = false, loadError = true) }
            }
        }
    }

    @Serializable
    private data class LicensesPayload(
        val libraries: List<LibraryEntry> = emptyList(),
    )

    private companion object {
        private const val LICENSES_PATH = "files/licenses.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
