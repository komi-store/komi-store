package zed.rainxch.core.domain.model.installation

sealed interface BindingStatus {
    data object Intact : BindingStatus

    data class Broken(
        val reason: BreakReason,
    ) : BindingStatus

    enum class BreakReason {
        PACKAGE_NAME,
        VERSION_CODE,
        VERSION_NAME,
        SIGNING_FINGERPRINT,
    }
}
