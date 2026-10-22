package zed.rainxch.core.presentation.components.inputs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.clear
import zed.rainxch.githubstore.core.presentation.res.hide_password
import zed.rainxch.githubstore.core.presentation.res.show_password

// Material's filled field made organic, after Chocola's search bar: a pill on a tonal container
// with no underline (a card corner when it holds several lines). Focus springs in a ring, in the
// error color when there is an error; clear and show-password are Material icon buttons that pop
// in and swap with a bouncy scale.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClassicTextField(
    personality: ClassicPersonality,
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    helper: String?,
    error: String?,
    placeholder: String?,
    leadingIcon: ImageVector?,
    trailing: @Composable (() -> Unit)?,
    onClear: (() -> Unit)?,
    multiline: Boolean,
    rows: Int,
    required: Boolean,
    enabled: Boolean,
    password: Boolean,
    onCommit: (() -> Unit)?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val colors = personality.colors
    val hasError = error != null
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val passwordVisible = remember { mutableStateOf(false) }
    val visualTransformation =
        if (password && !passwordVisible.value) PasswordVisualTransformation() else VisualTransformation.None
    val shape = if (multiline) RoundedCornerShape(personality.shape.corner) else CircleShape
    val trailingIcon =
        trailingSlot(
            trailing = trailing,
            passwordVisible = passwordVisible.takeIf { password },
            onClear = onClear,
            canClear = enabled && value.isNotEmpty(),
        )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = TextFieldDefaults.MinHeight),
        enabled = enabled,
        textStyle = personality.type.reading.copy(color = if (enabled) colors.onSurface else colors.disabledContent),
        cursorBrush = SolidColor(if (hasError) colors.error else colors.primary),
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
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = !multiline,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
                isError = hasError,
                label = labelSlot(label, required),
                placeholder = placeholderSlot(placeholder, multiline, colors.onSurfaceVariant),
                leadingIcon = leadingIcon?.let { { Icon(imageVector = it, contentDescription = null) } },
                trailingIcon = trailingIcon,
                supportingText = (error ?: helper)?.let { { Text(text = it) } },
                shape = shape,
                colors = fieldColors(personality),
                container = {
                    FieldContainer(
                        personality = personality,
                        shape = shape,
                        focused = focused,
                        hasError = hasError,
                        enabled = enabled,
                    )
                },
            )
        },
    )
}

private fun labelSlot(
    label: String?,
    required: Boolean,
): (@Composable () -> Unit)? = label?.let { { Text(text = if (required) "$it *" else it) } }

private fun placeholderSlot(
    placeholder: String?,
    multiline: Boolean,
    color: Color,
): (@Composable () -> Unit)? =
    placeholder?.let {
        {
            KomiText(
                text = it,
                role = KomiTextRole.Reading,
                color = color,
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

// A screen's own trailing content wins, then show-password (a password field passes its
// visibility), then clear.
private fun trailingSlot(
    trailing: (@Composable () -> Unit)?,
    passwordVisible: MutableState<Boolean>?,
    onClear: (() -> Unit)?,
    canClear: Boolean,
): (@Composable () -> Unit)? =
    when {
        trailing != null -> {
            trailing
        }

        passwordVisible != null -> {
            { PasswordButton(visible = passwordVisible.value, onToggle = { passwordVisible.value = !passwordVisible.value }) }
        }

        onClear != null -> {
            { ClearButton(visible = canClear, onClick = onClear) }
        }

        else -> {
            null
        }
    }

@Composable
private fun FieldContainer(
    personality: ClassicPersonality,
    shape: Shape,
    focused: Boolean,
    hasError: Boolean,
    enabled: Boolean,
) {
    val colors = personality.colors
    val motion = personality.motion
    // The highest tonal step, so the field still reads on a dialog's raised surface; the ring and
    // the primary label mark focus.
    val container by animateColorAsState(
        targetValue = if (enabled) personality.colorScheme.surfaceContainerHighest else colors.disabledContainer,
        animationSpec = motion.fastEffects(),
        label = "fieldContainer",
    )
    val ring by animateDpAsState(
        targetValue = if (enabled && (focused || hasError)) FocusRing else 0.dp,
        animationSpec = motion.bouncySpatial(),
        label = "fieldRing",
    )
    Box(
        modifier =
            Modifier
                .background(color = container, shape = shape)
                // A zero-width border still draws a hairline, so the ring is only there while it shows.
                .then(
                    if (ring > 0.dp) {
                        Modifier.border(width = ring, color = if (hasError) colors.error else colors.primary, shape = shape)
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
private fun fieldColors(personality: ClassicPersonality): TextFieldColors {
    val colors = personality.colors
    return TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedLeadingIconColor = colors.primary,
        unfocusedLeadingIconColor = colors.onSurfaceVariant,
        disabledLeadingIconColor = colors.disabledContent,
        errorLeadingIconColor = colors.error,
        focusedTrailingIconColor = colors.onSurfaceVariant,
        unfocusedTrailingIconColor = colors.onSurfaceVariant,
        disabledTrailingIconColor = colors.disabledContent,
        errorTrailingIconColor = colors.error,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        disabledLabelColor = colors.disabledContent,
        errorLabelColor = colors.error,
        focusedSupportingTextColor = colors.onSurfaceVariant,
        unfocusedSupportingTextColor = colors.onSurfaceVariant,
        disabledSupportingTextColor = colors.disabledContent,
        errorSupportingTextColor = colors.error,
    )
}

// Appears with a bouncy pop once there is text to clear, and leaves without overshoot.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClearButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    val motion = LocalPersonality.current.motion
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.bouncySpatial()) + fadeIn(motion.fastEffects()),
        exit = scaleOut(motion.fastEffects()) + fadeOut(motion.fastEffects()),
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(imageVector = Icons.Rounded.Cancel, contentDescription = stringResource(Res.string.clear))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordButton(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    val motion = LocalPersonality.current.motion
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(if (visible) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onToggle()
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        AnimatedContent(
            targetState = visible,
            transitionSpec = {
                (scaleIn(motion.bouncySpatial()) + fadeIn(motion.fastEffects())) togetherWith
                    (scaleOut(motion.fastEffects()) + fadeOut(motion.fastEffects()))
            },
            label = "passwordToggle",
        ) { shown ->
            Icon(
                imageVector = if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = stringResource(if (shown) Res.string.hide_password else Res.string.show_password),
            )
        }
    }
}

private val FocusRing = 2.dp
