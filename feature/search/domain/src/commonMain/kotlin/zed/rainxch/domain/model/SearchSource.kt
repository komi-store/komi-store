package zed.rainxch.domain.model

sealed interface SearchSource {
    data object GitHub : SearchSource

    data class Forgejo(
        val host: String,
    ) : SearchSource

    companion object {
        val Codeberg: SearchSource = Forgejo("codeberg.org")
    }
}
