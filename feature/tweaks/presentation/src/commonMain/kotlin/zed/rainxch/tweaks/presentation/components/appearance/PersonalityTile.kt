package zed.rainxch.tweaks.presentation.components.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.gridPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow

private val ClassicBg = Color(0xFFEEF1F6)
private val ClassicCard = Color(0xFFFFFFFF)
private val ClassicBorder = Color(0xFFD4DAE6)
private val ClassicAccent = Color(0xFF5B76F7)
private val ClassicBar = Color(0xFF33415C)
private val ClassicBarFaint = Color(0xFFAAB4C8)

@Composable
fun PersonalityTile(
    kind: AppPersonality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val isManga = kind == AppPersonality.MANGA
    val isActiveManga = LocalPersonality.current is MangaPersonality
    val tileShape = if (isActiveManga) RectangleShape else RoundedCornerShape(LocalPersonality.current.shape.corner)
    val borderColor = if (selected) colors.primary else colors.divider

    Column(
        modifier =
            modifier
                .then(
                    if (selected) {
                        if (isActiveManga) {
                            Modifier.hardShadow(DpOffset(5.dp, 5.dp), colors.shadow, tileShape)
                        } else {
                            Modifier.shadow(6.dp, tileShape)
                        }
                    } else {
                        Modifier
                    },
                ).clip(tileShape)
                .background(colors.surface)
                .border(if (isActiveManga) 3.dp else 1.5.dp, borderColor, tileShape)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(if (isManga) colors.background else ClassicBg)
                    .then(
                        if (isManga) {
                            Modifier.gridPaper(colors.onSurface, colors.gridOpacity + 0.02f, cell = 14.dp)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            MiniCard(isManga = isManga, accent = colors.primary, onSurface = colors.onSurface, surface = colors.surface)

            if (isManga) {
                KomiText(
                    text = "ドン",
                    role = KomiTextRole.Label,
                    color = colors.primary,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 8.dp)
                            .rotate(-6f),
                )
            }
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 6.dp, start = 8.dp)
                            .size(22.dp)
                            .then(if (isActiveManga) Modifier else Modifier.clip(CircleShape))
                            .background(colors.primary)
                            .then(if (isActiveManga) Modifier.border(2.dp, colors.outline) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            KomiText(
                text = if (isManga) "Manga" else "Classic",
                role = KomiTextRole.Stamp,
                color = colors.onSurface,
            )
            KomiText(
                text = if (isManga) "Inked comic panels" else "Clean Material 3",
                role = KomiTextRole.LabelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.MiniCard(
    isManga: Boolean,
    accent: Color,
    onSurface: Color,
    surface: Color,
) {
    Row(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 16.dp)
                .then(
                    if (isManga) {
                        Modifier
                            .hardShadow(DpOffset(3.dp, 3.dp), onSurface)
                            .background(surface)
                            .border(2.5.dp, onSurface)
                    } else {
                        Modifier
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .background(ClassicCard, RoundedCornerShape(14.dp))
                            .border(1.dp, ClassicBorder, RoundedCornerShape(14.dp))
                    },
                ).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .then(
                        if (isManga) {
                            Modifier.rotate(-3f).background(accent).border(2.dp, onSurface)
                        } else {
                            Modifier.background(ClassicAccent, RoundedCornerShape(8.dp))
                        },
                    ),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(7.dp)
                        .then(
                            if (isManga) {
                                Modifier.background(onSurface)
                            } else {
                                Modifier.background(ClassicBar, RoundedCornerShape(4.dp))
                            },
                        ),
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(5.dp)
                        .then(
                            if (isManga) {
                                Modifier.background(onSurface.copy(alpha = 0.45f))
                            } else {
                                Modifier.background(ClassicBarFaint, RoundedCornerShape(3.dp))
                            },
                        ),
            )
        }
    }
}
