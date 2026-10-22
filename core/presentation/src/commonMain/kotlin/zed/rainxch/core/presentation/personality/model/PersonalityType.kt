package zed.rainxch.core.presentation.personality.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Immutable
data class PersonalityType(
    val display: TextStyle,
    // The page's own name where it stands out, as M3 Expressive's emphasized display.
    val displayEmphasized: TextStyle,
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val itemTitle: TextStyle,
    val body: TextStyle,
    val reading: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
    val labelLarge: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val overline: TextStyle,
    val stamp: TextStyle,
    val numeral: TextStyle,
    val uppercaseHeadings: Boolean,
) {
    fun withFamilies(
        display: FontFamily,
        body: FontFamily,
        mono: FontFamily,
        displayWeight: FontWeight? = null,
    ): PersonalityType {
        fun TextStyle.inDisplay() = copy(fontFamily = display, fontWeight = displayWeight ?: fontWeight)

        fun TextStyle.inBody() = copy(fontFamily = body)

        return copy(
            display = this.display.inDisplay(),
            displayEmphasized = displayEmphasized.inDisplay(),
            screenTitle = screenTitle.inDisplay(),
            sectionTitle = sectionTitle.inDisplay(),
            cardTitle = cardTitle.inBody(),
            itemTitle = itemTitle.inBody(),
            body = this.body.inBody(),
            reading = reading.inBody(),
            caption = caption.inBody(),
            mono = this.mono.copy(fontFamily = mono),
            labelLarge = labelLarge.inBody(),
            label = label.inBody(),
            labelSmall = labelSmall.inBody(),
            overline = overline.inBody(),
            stamp = stamp.inDisplay(),
            numeral = numeral.inDisplay(),
        )
    }
}
