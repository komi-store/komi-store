package zed.rainxch.core.presentation.components.text

import androidx.compose.ui.text.TextStyle
import zed.rainxch.core.presentation.personality.model.PersonalityType

enum class KomiTextRole(
    internal val style: (PersonalityType) -> TextStyle,
    internal val case: KomiTextCase = KomiTextCase.AsWritten,
) {
    Display(PersonalityType::display, KomiTextCase.Heading),
    ScreenTitle(PersonalityType::screenTitle, KomiTextCase.Heading),
    SectionTitle(PersonalityType::sectionTitle, KomiTextCase.Heading),
    CardTitle(PersonalityType::cardTitle),
    ItemTitle(PersonalityType::itemTitle),
    Body(PersonalityType::body),
    Reading(PersonalityType::reading),
    Caption(PersonalityType::caption),
    Mono(PersonalityType::mono),
    LabelLarge(PersonalityType::labelLarge),
    Label(PersonalityType::label),
    LabelSmall(PersonalityType::labelSmall),
    Overline(PersonalityType::overline, KomiTextCase.Upper),
    Stamp(PersonalityType::stamp, KomiTextCase.Heading),
    Numeral(PersonalityType::numeral),
}

internal enum class KomiTextCase {
    AsWritten,

    // In capitals only where the personality sets its headings in capitals (Manga).
    Heading,
    Upper,
    ;

    fun isUpper(type: PersonalityType): Boolean =
        when (this) {
            AsWritten -> false
            Heading -> type.uppercaseHeadings
            Upper -> true
        }
}
