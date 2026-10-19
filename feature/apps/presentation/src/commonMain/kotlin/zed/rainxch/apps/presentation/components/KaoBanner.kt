package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.kao_banner_body
import zed.rainxch.githubstore.core.presentation.res.kao_banner_cta
import zed.rainxch.githubstore.core.presentation.res.kao_banner_dismiss_cd
import zed.rainxch.githubstore.core.presentation.res.kao_banner_title

@Composable
fun KaoBanner(
    onLearnMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(shape.corner))
                .background(colors.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                KomiIcon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(24.dp).padding(top = 10.dp),
                )

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KomiText(
                        text = stringResource(Res.string.kao_banner_title),
                        role = KomiTextRole.Stamp,
                        fontWeight = FontWeight.SemiBold,
                        uppercase = false,
                        color = colors.onPrimaryContainer,
                    )

                    KomiText(
                        text = stringResource(Res.string.kao_banner_body),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onPrimaryContainer,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.kao_banner_dismiss_cd),
                        tint = colors.onPrimaryContainer,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                KomiButton(
                    onClick = onLearnMore,
                    label = stringResource(Res.string.kao_banner_cta),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            }
        }
    }
}
