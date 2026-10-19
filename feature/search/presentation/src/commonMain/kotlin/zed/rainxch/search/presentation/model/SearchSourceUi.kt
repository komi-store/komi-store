package zed.rainxch.search.presentation.model

sealed interface SearchSourceUi {
    val label: String

    data object GitHub : SearchSourceUi {
        override val label = "GitHub"
    }

    data object Codeberg : SearchSourceUi {
        override val label = "Codeberg"
    }

    data class CustomForge(
        val host: String,
    ) : SearchSourceUi {
        override val label: String = host
    }
}
