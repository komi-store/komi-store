package zed.rainxch.core.domain.model.mirror

sealed interface MirrorPreference {
    data object Direct : MirrorPreference

    data class Selected(
        val id: String,
    ) : MirrorPreference

    data class Custom(
        val template: String,
    ) : MirrorPreference
}
