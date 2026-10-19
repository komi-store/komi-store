package zed.rainxch.core.domain.model.appearance

enum class ContentWidth {
    COMPACT,
    WIDE,
    EXTRA_WIDE,
    ;

    companion object {
        fun fromName(name: String?): ContentWidth = entries.find { it.name == name } ?: COMPACT
    }
}
