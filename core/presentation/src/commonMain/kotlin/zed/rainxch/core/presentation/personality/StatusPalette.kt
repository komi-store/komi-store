package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.status.StatusColors

fun statusColors(dark: Boolean): StatusColors = if (dark) StatusColorsDark else StatusColorsLight

private val StatusColorsLight =
    StatusColors(
        issueOpen = Color(0xFF1A7F37),
        issueClosed = Color(0xFF8250DF),
        pullOpen = Color(0xFF1A7F37),
        pullMerged = Color(0xFF8250DF),
        pullClosed = Color(0xFFCF222E),
        severityCritical = Color(0xFFB3261E),
        severityHigh = Color(0xFFCF222E),
        severityMedium = Color(0xFFBC4C00),
        severityLow = Color(0xFF9A6700),
        severityUnknown = Color(0xFF6E7781),
        statusReady = Color(0xFF1A7F37),
        statusWarning = Color(0xFF9A6700),
        statusError = Color(0xFFCF222E),
        protectionSignature = Color(0xFF0969DA),
        protectionPrivileged = Color(0xFFBC4C00),
    )

private val StatusColorsDark =
    StatusColors(
        issueOpen = Color(0xFF3FB950),
        issueClosed = Color(0xFFA371F7),
        pullOpen = Color(0xFF3FB950),
        pullMerged = Color(0xFFA371F7),
        pullClosed = Color(0xFFF85149),
        severityCritical = Color(0xFFFF6B5E),
        severityHigh = Color(0xFFF85149),
        severityMedium = Color(0xFFDB6D28),
        severityLow = Color(0xFFD29922),
        severityUnknown = Color(0xFF8B949E),
        statusReady = Color(0xFF3FB950),
        statusWarning = Color(0xFFD29922),
        statusError = Color(0xFFF85149),
        protectionSignature = Color(0xFF58A6FF),
        protectionPrivileged = Color(0xFFDB6D28),
    )
