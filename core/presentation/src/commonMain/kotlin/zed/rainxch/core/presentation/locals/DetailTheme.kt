package zed.rainxch.core.presentation.locals

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

fun resolveDetailDarkTheme(
    followAppTheme: Boolean,
    appDarkTheme: Boolean,
    systemDarkTheme: Boolean,
): Boolean = if (followAppTheme) appDarkTheme else systemDarkTheme

@Composable
fun detailPageDarkTheme(): Boolean =
    resolveDetailDarkTheme(
        followAppTheme = LocalDetailFollowAppTheme.current,
        appDarkTheme = LocalAppDarkTheme.current,
        systemDarkTheme = isSystemInDarkTheme(),
    )
