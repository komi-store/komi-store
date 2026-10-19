package zed.rainxch.core.domain.model.appearance

enum class AppTheme {
    DYNAMIC,
    NORD,
    CREAM,
    FOREST,
    PLUM,
    ;

    companion object {
        private val LEGACY_MIGRATION =
            mapOf(
                "OCEAN" to NORD,
                "SLATE" to NORD,
                "PURPLE" to PLUM,
                "AMBER" to CREAM,
            )

        fun fromName(name: String?): AppTheme {
            if (name.isNullOrEmpty()) return NORD
            entries.firstOrNull { it.name == name }?.let { return it }
            LEGACY_MIGRATION[name]?.let { return it }
            return NORD
        }
    }
}
