package zed.rainxch.core.presentation.personality.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// The legacy* styles keep today's look for call sites that still pass size, weight or case
// overrides; each one moves to a job-named role and loses its overrides as it migrates.
@Immutable
data class PersonalityType(
    val display: TextStyle,
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
    val legacyDisplay: TextStyle,
    val legacyTitle: TextStyle,
    val legacyStamp: TextStyle,
    val legacyBody: TextStyle,
    val legacyLabel: TextStyle,
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
            screenTitle = screenTitle.inDisplay(),
            sectionTitle = sectionTitle.inDisplay(),
            cardTitle = cardTitle.inBody(),
            itemTitle = itemTitle.inBody(),
            body = this.body.inBody(),
            reading = reading.inBody(),
            caption = caption.inBody(),
            mono = this.mono.copy(fontFamily = mono),
            labelLarge = labelLarge.inDisplay(),
            label = label.inBody(),
            labelSmall = labelSmall.inBody(),
            overline = overline.inBody(),
            stamp = stamp.inDisplay(),
            numeral = numeral.inDisplay(),
            legacyDisplay = legacyDisplay.inDisplay(),
            legacyTitle = legacyTitle.inDisplay(),
            legacyStamp = legacyStamp.inDisplay(),
            legacyBody = legacyBody.inBody(),
            legacyLabel = legacyLabel.inBody(),
        )
    }
}
