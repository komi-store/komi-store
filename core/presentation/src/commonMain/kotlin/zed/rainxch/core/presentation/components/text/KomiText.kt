package zed.rainxch.core.presentation.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.appLocale
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun KomiText(
    text: String,
    modifier: Modifier = Modifier,
    role: KomiTextRole = KomiTextRole.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    autoSize: TextAutoSize? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    Text(
        text = if (role.case.isUpper(personality.type)) text.toUpperCase(appLocale()) else text,
        modifier = modifier,
        color = if (color != Color.Unspecified) color else personality.colors.onSurface,
        autoSize = autoSize,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = onTextLayout ?: {},
        style = role.style(personality.type),
    )
}

@Composable
fun KomiText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    role: KomiTextRole = KomiTextRole.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    Text(
        text = text,
        modifier = modifier,
        color = if (color != Color.Unspecified) color else personality.colors.onSurface,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = onTextLayout ?: {},
        style = role.style(personality.type),
    )
}

@Composable
private fun KomiTextScale() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KomiText("Godot", role = KomiTextRole.Display)
        KomiText("Library", role = KomiTextRole.ScreenTitle)
        KomiText("What's new", role = KomiTextRole.SectionTitle)
        KomiText("RustDesk", role = KomiTextRole.CardTitle)
        KomiText("True black (AMOLED)", role = KomiTextRole.ItemTitle)
        KomiText(
            "An open-source remote desktop application designed for self-hosting.",
            role = KomiTextRole.Body,
        )
        KomiText(
            "Godot 3.6.3 is a maintenance release addressing stability and usability issues.",
            role = KomiTextRole.Reading,
        )
        KomiText("119k · 20 MB · 2 mo ago", role = KomiTextRole.Caption)
        KomiText("v3.6.3-stable · arm64-v8a", role = KomiTextRole.Mono)
        KomiText("Install latest", role = KomiTextRole.LabelLarge)
        KomiText("Security", role = KomiTextRole.Label)
        KomiText("Top charts", role = KomiTextRole.LabelSmall)
        KomiText("Assets", role = KomiTextRole.Overline)
        KomiText("Pre-release", role = KomiTextRole.Stamp)
        KomiText("118k", role = KomiTextRole.Numeral)
    }
}

@Preview
@Composable
private fun KomiTextRolesPreview() {
    PersonalityPreview {
        KomiTextScale()
    }
}

@Preview
@Composable
private fun KomiTextNightPreview() {
    PersonalityPreview(paper = MangaPaper.NIGHT, accent = MangaAccent.SUN) {
        KomiTextScale()
    }
}

@Preview
@Composable
private fun KomiTextNordPreview() {
    PersonalityPreview(paper = MangaPaper.NORD, accent = MangaAccent.FROST) {
        KomiTextScale()
    }
}

@Preview
@Composable
private fun KomiTextEllipsisPreview() {
    PersonalityPreview {
        KomiText(
            "OllamaSuperLongRepositoryNameThatOverflowsTheLine",
            role = KomiTextRole.SectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun KomiTextClassicPreview() {
    PersonalityPreview(classicPersonality()) {
        KomiTextScale()
    }
}

@Preview
@Composable
private fun KomiTextClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) {
        KomiTextScale()
    }
}
