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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.appLocale
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
    multiline: Boolean = false,
    rows: Int = 4,
    required: Boolean = false,
    size: KomiTextFieldSize = KomiTextFieldSize.Md,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onCommit: (() -> Unit)? = null,
) {
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
                trailing = trailing,
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
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                label = label,
                helper = helper,
                error = error,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailing = trailing,
                clearable = clearable,
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
    modifier: Modifier,
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
) {
    val colors = personality.colors
    val metrics = fieldMetrics(size)
    val type = personality.type
    val hasError = error != null
    val hasAccent = colors.primary != colors.onSurface

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val shellInteraction = remember { MutableInteractionSource() }
    var passwordVisible by remember { mutableStateOf(false) }
    val visualTransformation =
        if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None

    val borderColor = if (hasError) colors.error else colors.outline
    val borderWidth = metrics.border + if (focused && !hasAccent && !hasError) 0.5.dp else 0.dp
    val shadowColor =
        when {
            hasError -> colors.error
            focused && hasAccent -> colors.primary
            else -> colors.shadow
        }
    val iconColor =
        when {
            hasError -> colors.error
            focused && hasAccent -> colors.primary
            else -> colors.onSurface
        }
    val fill = if (enabled) colors.surface else colors.surfaceVariant
    val showClear = clearable && enabled && value.isNotEmpty() && trailing == null

    Column(modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.5f))) {
        if (label != null) {
            val labelStyle = TextStyle(
                fontFamily = type.display.fontFamily,
                fontSize = (metrics.font - 2f).sp,
                letterSpacing = 0.05.em,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.toUpperCase(appLocale()),
                    style = labelStyle,
                    color = colors.onSurface
                )
                if (required) {
                    Text(
                        text = "*",
                        style = labelStyle,
                        color =
                            if (hasError) {
                                colors.error
                            } else if (hasAccent) {
                                colors.primary
                            } else {
                                colors.onSurface
                            },
                    )
                }
            }
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
                                color = shadowColor,
                                shape = RectangleShape,
                            )
                        } else {
                            Modifier
                        },
                    ).background(color = fill, shape = RectangleShape)
                    .border(width = borderWidth, color = borderColor, shape = RectangleShape)
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
                            Modifier.heightIn(min = metrics.height)
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
                    tint = iconColor
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                enabled = enabled,
                textStyle =
                    TextStyle(
                        fontFamily = type.body.fontFamily,
                        fontWeight = FontWeight.W700,
                        fontSize = metrics.font.sp,
                        color = colors.onSurface,
                    ),
                cursorBrush = SolidColor(if (hasAccent) colors.primary else colors.onSurface),
                visualTransformation = visualTransformation,
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
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontFamily = type.body.fontFamily,
                                fontWeight = FontWeight.W700,
                                fontSize = metrics.font.sp
                            ),
                            color = colors.onSurfaceVariant,
                            maxLines = if (multiline) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                },
            )
            if (trailing != null) {
                trailing()
            } else if (password) {
                PasswordToggle(
                    visible = passwordVisible,
                    onToggle = { passwordVisible = !passwordVisible },
                    iconSize = metrics.icon,
                    tint = iconColor,
                )
            } else if (showClear) {
                ClearButton(
                    colors = colors,
                    iconSize = metrics.icon,
                    onClear = { onValueChange("") })
            }
        }

        val supporting = error ?: helper
        if (supporting != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = supporting,
                style = TextStyle(
                    fontFamily = type.body.fontFamily,
                    fontWeight = FontWeight.W700,
                    fontSize = (metrics.font - 3.5f).sp
                ),
                color = if (hasError) colors.error else colors.onSurfaceVariant,
            )
        }
    }
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
            contentDescription = "Clear",
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
        contentDescription = null,
        tint = tint,
        modifier =
            Modifier
                .size(iconSize)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
    )
}

@Composable
private fun ClassicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
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
    enabled: Boolean,
    password: Boolean,
    keyboardType: KeyboardType,
    onCommit: (() -> Unit)?,
) {
    val hasError = error != null
    val supporting = error ?: helper
    val showClear = clearable && enabled && value.isNotEmpty() && trailing == null
    var passwordVisible by remember { mutableStateOf(false) }
    val visualTransformation = if (password && !passwordVisible) {
        PasswordVisualTransformation()
    } else VisualTransformation.None

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = hasError,
        visualTransformation = visualTransformation,
        label = label?.let {
            {
                Text(text = if (required) "$it *" else it)
            }
        },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    color = LocalPersonality.current.colors.onSurfaceVariant,
                    maxLines = if (multiline) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null
                )
            }
        },
        trailingIcon =
            when {
                trailing != null -> {
                    trailing
                }

                password -> {
                    {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.clickable(onClick = {
                                passwordVisible = !passwordVisible
                            }),
                        )
                    }
                }

                showClear -> {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.clickable(onClick = { onValueChange("") }),
                        )
                    }
                }

                else -> {
                    null
                }
            },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = !multiline,
        minLines = if (multiline) rows else 1,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (!multiline && onCommit != null) ImeAction.Done else ImeAction.Default,
            ),
        maxLines = if (multiline) Int.MAX_VALUE else 1,
        keyboardActions = KeyboardActions(onDone = { onCommit?.invoke() }),
    )
}

private data class FieldMetrics(
    val height: Dp,
    val paddingX: Dp,
    val font: Float,
    val icon: Dp,
    val border: Dp,
    val shadow: Dp,
)

private fun fieldMetrics(size: KomiTextFieldSize): FieldMetrics =
    when (size) {
        KomiTextFieldSize.Sm -> FieldMetrics(38.dp, 11.dp, 13f, 16.dp, 2.5.dp, 3.dp)
        KomiTextFieldSize.Md -> FieldMetrics(46.dp, 12.dp, 15f, 19.dp, 3.dp, 4.dp)
        KomiTextFieldSize.Lg -> FieldMetrics(54.dp, 14.dp, 17f, 21.dp, 3.dp, 5.dp)
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
            helper = "GitHub org or user."
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
            helper = "Read-only."
        )
    }
}

@Preview
@Composable
private fun KomiTextFieldMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewFields() }
}

@Preview
@Composable
private fun KomiTextFieldMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.FROST
        )
    ) { PreviewFields() }
}

@Preview
@Composable
private fun KomiTextFieldClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewFields() }
}

@Preview
@Composable
private fun KomiTextFieldClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewFields() }
}
