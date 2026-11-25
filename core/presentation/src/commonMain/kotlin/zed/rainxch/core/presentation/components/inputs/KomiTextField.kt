package zed.rainxch.core.presentation.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.model.PersonalityType
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.clear
import zed.rainxch.githubstore.core.presentation.res.hide_password
import zed.rainxch.githubstore.core.presentation.res.show_password

@Composable
fun KomiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
    error: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    clearable: Boolean = false,
    onClear: (() -> Unit)? = null,
    multiline: Boolean = false,
    rows: Int = 4,
    required: Boolean = false,
    size: KomiTextFieldSize = KomiTextFieldSize.Md,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onCommit: (() -> Unit)? = null,
) {
    // onClear replaces the plain clear for a screen whose clear does more than empty the text.
    val clear: (() -> Unit)? = onClear ?: if (clearable) ({ onValueChange("") }) else null
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaTextField(
                personality = personality,
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                label = label,
                helper = helper,
                error = error,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailing =
                    trailing ?: onClear?.let { onClearClick ->
                        {
                            if (enabled && value.isNotEmpty()) {
                                ClearButton(personality.colors, fieldMetrics(size).icon, onClearClick)
                            }
                        }
                    },
                clearable = clearable,
                multiline = multiline,
                rows = rows,
                required = required,
                size = size,
                enabled = enabled,
                password = password,
                keyboardType = keyboardType,
                onCommit = onCommit,
            )
        }

        is ClassicPersonality -> {
            ClassicTextField(
                personality = personality,
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                label = label,
                helper = helper,
                error = error,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailing = trailing,
                onClear = clear,
                multiline = multiline,
                rows = rows,
                required = required,
                enabled = enabled,
                password = password,
                keyboardType = keyboardType,
                onCommit = onCommit,
            )
        }
    }
}

@Composable
private fun MangaTextField(
    personality: MangaPersonality,
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    helper: String?,
    error: String?,
    placeholder: String?,
    leadingIcon: ImageVector?,
    trailing: @Composable (() -> Unit)?,
    clearable: Boolean,
    multiline: Boolean,
    rows: Int,
    required: Boolean,
    size: KomiTextFieldSize,
    enabled: Boolean,
    password: Boolean,
    keyboardType: KeyboardType,
    onCommit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = personality.colors
    val metrics = fieldMetrics(size)
    val hasError = error != null

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val shellInteraction = remember { MutableInteractionSource() }
    var passwordVisible by remember { mutableStateOf(false) }
    val tones = mangaFieldTones(colors, metrics.border, hasError = hasError, focused = focused, enabled = enabled)

    Column(modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.5f))) {
        if (label != null) {
            MangaFieldLabel(label = label, required = required, hasError = hasError, colors = colors)
            Spacer(modifier = Modifier.height(7.dp))
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled) {
                            Modifier.hardShadow(
                                offset = DpOffset(metrics.shadow, metrics.shadow),
                                color = tones.shadow,
                                shape = RectangleShape,
                            )
                        } else {
                            Modifier
                        },
                    ).background(color = tones.fill, shape = RectangleShape)
                    .border(width = tones.borderWidth, color = tones.border, shape = RectangleShape)
                    .then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = shellInteraction,
                                indication = null,
                                onClick = { focusRequester.requestFocus() },
                            )
                        } else {
                            Modifier
                        },
                    ).then(
                        if (multiline) {
                            Modifier.padding(10.dp)
                        } else {
                            Modifier
                                .heightIn(min = metrics.height)
                                .padding(horizontal = metrics.paddingX)
                        },
                    ),
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(metrics.icon),
                    tint = tones.icon,
                )
            }
            MangaFieldInput(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                multiline = multiline,
                rows = rows,
                enabled = enabled,
                masked = password && !passwordVisible,
                keyboardType = keyboardType,
                onCommit = onCommit,
                interaction = interaction,
                colors = colors,
                textStyle = personality.type.reading,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            MangaFieldTrailing(
                trailing = trailing,
                password = password,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible },
                value = value,
                clearable = clearable,
                enabled = enabled,
                onClear = { onValueChange("") },
                iconSize = metrics.icon,
                tint = tones.icon,
                colors = colors,
            )
        }

        val supporting = error ?: helper
        if (supporting != null) {
            Spacer(modifier = Modifier.height(6.dp))
            KomiText(
                text = supporting,
                role = KomiTextRole.Caption,
                color = if (hasError) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MangaFieldLabel(
    label: String,
    required: Boolean,
    hasError: Boolean,
    colors: PersonalityColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KomiText(text = label, role = KomiTextRole.Overline, color = colors.onSurface)
        if (required) {
            KomiText(
                text = "*",
                role = KomiTextRole.Overline,
                color =
                    if (hasError) {
                        colors.error
                    } else if (colors.primary != colors.onSurface) {
                        colors.primary
                    } else {
                        colors.onSurface
                    },
            )
        }
    }
}

@Composable
private fun MangaFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    multiline: Boolean,
    rows: Int,
    enabled: Boolean,
    masked: Boolean,
    keyboardType: KeyboardType,
    onCommit: (() -> Unit)?,
    interaction: MutableInteractionSource,
    colors: PersonalityColors,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val hasAccent = colors.primary != colors.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(if (hasAccent) colors.primary else colors.onSurface),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = !multiline,
        minLines = if (multiline) rows else 1,
        maxLines = if (multiline) Int.MAX_VALUE else 1,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (!multiline && onCommit != null) ImeAction.Done else ImeAction.Default,
            ),
        keyboardActions = KeyboardActions(onDone = { onCommit?.invoke() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            if (value.isEmpty() && placeholder != null) {
                KomiText(
                    text = placeholder,
                    role = KomiTextRole.Reading,
                    color = colors.onSurfaceVariant,
                    maxLines = if (multiline) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            inner()
        },
    )
}

@Composable
private fun MangaFieldTrailing(
    trailing: @Composable (() -> Unit)?,
    password: Boolean,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    value: String,
    clearable: Boolean,
    enabled: Boolean,
    onClear: () -> Unit,
    iconSize: Dp,
    tint: Color,
    colors: PersonalityColors,
) {
    if (trailing != null) {
        trailing()
    } else if (password) {
        PasswordToggle(
            visible = passwordVisible,
            onToggle = onTogglePassword,
            iconSize = iconSize,
            tint = tint,
        )
    } else if (clearable && enabled && value.isNotEmpty()) {
        ClearButton(colors = colors, iconSize = iconSize, onClear = onClear)
    }
}

private val FocusedBorderBoost = 0.5.dp

private data class MangaFieldTones(
    val fill: Color,
    val border: Color,
    val borderWidth: Dp,
    val shadow: Color,
    val icon: Color,
)

private fun mangaFieldTones(
    colors: PersonalityColors,
    baseBorder: Dp,
    hasError: Boolean,
    focused: Boolean,
    enabled: Boolean,
): MangaFieldTones {
    val hasAccent = colors.primary != colors.onSurface
    val accentFocused = focused && hasAccent
    return MangaFieldTones(
        fill = if (enabled) colors.surface else colors.surfaceVariant,
        border = if (hasError) colors.error else colors.outline,
        borderWidth = baseBorder + if (focused && !hasAccent && !hasError) FocusedBorderBoost else 0.dp,
        shadow =
            when {
                hasError -> colors.error
                accentFocused -> colors.primary
                else -> colors.shadow
            },
        icon =
            when {
                hasError -> colors.error
                accentFocused -> colors.primary
                else -> colors.onSurface
            },
    )
}

@Composable
private fun ClearButton(
    colors: PersonalityColors,
    iconSize: Dp,
    onClear: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(iconSize + 7.dp)
                .background(color = colors.background, shape = RectangleShape)
                .border(width = 2.dp, color = colors.outline, shape = RectangleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(Res.string.clear),
            modifier = Modifier.size(iconSize - 5.dp),
            tint = colors.onSurface,
        )
    }
}

@Composable
private fun PasswordToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    iconSize: Dp,
    tint: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    Icon(
        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
        contentDescription = stringResource(if (visible) Res.string.hide_password else Res.string.show_password),
        tint = tint,
        modifier =
            Modifier
                .size(iconSize)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
    )
}

private data class FieldMetrics(
    val height: Dp,
    val paddingX: Dp,
    val icon: Dp,
    val border: Dp,
    val shadow: Dp,
)

private fun fieldMetrics(size: KomiTextFieldSize): FieldMetrics =
    when (size) {
        KomiTextFieldSize.Sm -> FieldMetrics(38.dp, 11.dp, 16.dp, 2.5.dp, 3.dp)
        KomiTextFieldSize.Md -> FieldMetrics(46.dp, 12.dp, 19.dp, 3.dp, 4.dp)
        KomiTextFieldSize.Lg -> FieldMetrics(54.dp, 14.dp, 21.dp, 3.dp, 5.dp)
    }

@Composable
private fun PreviewFields() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        KomiTextField(
            value = "",
            onValueChange = {},
            label = "Search",
            leadingIcon = Icons.Default.Search,
            clearable = true,
            placeholder = "Search apps…",
            helper = "Press Enter to search.",
            onCommit = {},
        )
        KomiTextField(
            value = "immich-app",
            onValueChange = {},
            label = "Owner",
            required = true,
            helper = "GitHub org or user.",
        )
        KomiTextField(
            value = "not-an-email",
            onValueChange = {},
            label = "Email",
            leadingIcon = Icons.Default.MailOutline,
            error = "That doesn't look like an email.",
        )
        KomiTextField(value = "Self-hosted photo backup.", onValueChange = {
        }, label = "Description", multiline = true, rows = 3, helper = "Shown on the app card.")
        KomiTextField(
            value = "locked",
            onValueChange = {},
            label = "Disabled",
            enabled = false,
            helper = "Read-only.",
        )
    }
}

@Preview
@Composable
private fun KomiTextFieldMangaPreview() {
    PersonalityPreview(personality = mangaPersonality()) { PreviewFields() }
}

@Preview
@Composable
private fun KomiTextFieldMangaNightPreview() {
    PersonalityPreview(
        personality =
            mangaPersonality(
                paper = MangaPaper.NIGHT,
                accent = MangaAccent.FROST,
            ),
    ) { PreviewFields() }
}

@Preview
@Composable
private fun KomiTextFieldClassicPreview() {
    PersonalityPreview(personality = classicPersonality()) { PreviewFields() }
}

@Preview
@Composable
private fun KomiTextFieldClassicDarkPreview() {
    PersonalityPreview(personality = classicPersonality(dark = true)) { PreviewFields() }
}
