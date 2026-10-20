package zed.rainxch.tweaks.presentation.hosttokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.host_tokens_entry_subtitle
import zed.rainxch.githubstore.core.presentation.res.host_tokens_title

@Composable
fun HostTokensEntryCard(
    onClick: () -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    KomiSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(personality.shape.cornerSmall))
                    .background(colors.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = stringResource(Res.string.host_tokens_title),
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                )
                KomiText(
                    text = stringResource(Res.string.host_tokens_entry_subtitle),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
            KomiIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
