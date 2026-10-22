package zed.rainxch.tweaks.presentation.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_cta
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_error
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_placeholder
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_title
import zed.rainxch.tweaks.presentation.model.ProxyType

data class PastedProxy(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
)

fun parseProxyUrl(raw: String): PastedProxy? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val schemeIdx = trimmed.indexOf("://")
    if (schemeIdx <= 0) return null
    val scheme = trimmed.substring(0, schemeIdx).lowercase()
    val type = when (scheme) {
        "http", "https" -> ProxyType.HTTP
        "socks", "socks4", "socks5", "socks5h" -> ProxyType.SOCKS
        else -> return null
    }
    val afterScheme = trimmed.substring(schemeIdx + 3)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    if (afterScheme.isEmpty()) return null

    val atIdx = afterScheme.lastIndexOf('@')
    val credPart = if (atIdx >= 0) afterScheme.substring(0, atIdx) else null
    val hostPart = if (atIdx >= 0) afterScheme.substring(atIdx + 1) else afterScheme

    val (username, password) = if (credPart != null) {
        val colonIdx = credPart.indexOf(':')
        if (colonIdx >= 0) {
            credPart.substring(0, colonIdx) to credPart.substring(colonIdx + 1)
        } else {
            credPart to ""
        }
    } else {
        null to null
    }

    val colonIdx = hostPart.lastIndexOf(':')
    if (colonIdx <= 0) return null
    val host = hostPart.substring(0, colonIdx)
    val port = hostPart.substring(colonIdx + 1).toIntOrNull() ?: return null
    if (host.isBlank() || port !in 1..65535) return null

    return PastedProxy(
        type = type,
        host = host,
        port = port,
        username = username?.takeIf { it.isNotBlank() },
        password = password?.takeIf { it.isNotBlank() },
    )
}

@Composable
fun PasteProxyUrlSheet(
    input: String,
    isError: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPersonality.current.colors

    KomiSheet(
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.tweaks_connection_paste_url_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            KomiText(
                text = stringResource(Res.string.tweaks_connection_paste_url_body),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            KomiTextField(
                value = input,
                onValueChange = onInputChange,
                label = stringResource(Res.string.tweaks_connection_paste_url_placeholder),
                error = if (isError) stringResource(Res.string.tweaks_connection_paste_url_error) else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            KomiButton(
                onClick = onSubmit,
                label = stringResource(Res.string.tweaks_connection_paste_url_cta),
                variant = KomiButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                enabled = input.isNotBlank(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
