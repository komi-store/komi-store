package zed.rainxch.core.domain.model.appearance

enum class AccentId {
    MONO,
    CRIMSON,
    COBALT,
    SUN,
    FROST,

    // Classic on Android 12+ only: the system's colors from the wallpaper.
    WALLPAPER,
    ;

    companion object {
        fun fromName(name: String?): AccentId = entries.firstOrNull { it.name == name } ?: CRIMSON
    }
}
