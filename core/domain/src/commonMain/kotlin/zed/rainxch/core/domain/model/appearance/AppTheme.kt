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
            return entries.firstOrNull { it.name == name } ?: LEGACY_MIGRATION[name] ?: NORD
        }
    }
}
