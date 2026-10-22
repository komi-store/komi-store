package zed.rainxch.core.presentation.components.chips

enum class KomiChipKind {
    Info,
    Filter,
    Input,

    // A filled chip for a tinted card, where a selected filter chip would vanish: a neutral fill (the primary
    // container when selected) in Classic, ink in Manga.
    Tonal,
}
