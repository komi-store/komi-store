package zed.rainxch.apps.presentation.components

import androidx.compose.ui.graphics.Color

// A warning colour for a download that moves an app backwards, on the in-progress card and on a
// pending-install row alike. Deliberately not the personality's error colour: the theme and
// personality swap the palette, and this signal has to read the same in all of them.
internal val DowngradeWarningColor = Color(0xFFD32F2F)
