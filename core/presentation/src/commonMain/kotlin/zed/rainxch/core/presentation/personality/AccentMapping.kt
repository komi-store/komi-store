package zed.rainxch.core.presentation.personality

import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.presentation.personality.manga.MangaAccent

fun AccentId.toMangaAccent(): MangaAccent =
    when (this) {
        AccentId.MONO -> MangaAccent.MONO
        AccentId.CRIMSON, AccentId.WALLPAPER -> MangaAccent.CRIMSON
        AccentId.COBALT -> MangaAccent.COBALT
        AccentId.SUN -> MangaAccent.SUN
        AccentId.FROST -> MangaAccent.FROST
    }

fun MangaAccent.toAccentId(): AccentId =
    when (this) {
        MangaAccent.MONO -> AccentId.MONO
        MangaAccent.CRIMSON -> AccentId.CRIMSON
        MangaAccent.COBALT -> AccentId.COBALT
        MangaAccent.SUN -> AccentId.SUN
        MangaAccent.FROST -> AccentId.FROST
    }
