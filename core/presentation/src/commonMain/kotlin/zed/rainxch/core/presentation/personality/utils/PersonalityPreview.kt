package zed.rainxch.core.presentation.personality.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.Personality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.mangaPersonality

@Composable
fun PersonalityPreview(
    modifier: Modifier = Modifier,
    personality: Personality = mangaPersonality(),
    content: @Composable () -> Unit,
) {
    PersonalityTheme(personality) {
        Box(
            modifier =
                modifier
                    .background(LocalPersonality.current.colors.background)
                    .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun PersonalityPreview(
    paper: MangaPaper,
    accent: MangaAccent,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PersonalityPreview(
        modifier = modifier,
        personality = mangaPersonality(paper = paper, accent = accent),
        content = content,
    )
}
