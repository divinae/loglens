package com.dv.loglens.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.intui.standalone.styling.dark as iconDark
import org.jetbrains.jewel.intui.standalone.styling.light as iconLight
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle

/** Jewel theme definition using the islands color palette. */
fun islandsThemeDefinition(isDark: Boolean): ThemeDefinition =
    if (isDark) {
        JewelTheme.darkThemeDefinition(
            colors = GlobalColors.dark(
                panelBackground = IslandsDark.panel,
                borders = BorderColors.dark(
                    normal = IslandsDark.borderNormal,
                    focused = IslandsDark.borderFocused,
                    disabled = IslandsDark.borderNormal,
                ),
                text = TextColors.dark(
                    normal = IslandsDark.textNormal,
                    selected = IslandsDark.textNormal,
                    disabled = IslandsDark.textDisabled,
                    info = IslandsDark.textInfo,
                    error = IslandsDark.textError,
                ),
            ),
            contentColor = IslandsDark.textNormal,
        )
    } else {
        JewelTheme.lightThemeDefinition(
            colors = GlobalColors.light(
                panelBackground = IslandsLight.panel,
                borders = BorderColors.light(
                    normal = IslandsLight.borderNormal,
                    focused = IslandsLight.borderFocused,
                    disabled = IslandsLight.borderNormal,
                ),
                text = TextColors.light(
                    normal = IslandsLight.textNormal,
                    selected = IslandsLight.textNormal,
                    disabled = IslandsLight.textDisabled,
                    info = IslandsLight.textInfo,
                    error = IslandsLight.textError,
                ),
            ),
            contentColor = IslandsLight.textNormal,
        )
    }

/**
 * Component styling for the islands theme. Provides explicit, clearly visible hover/press overlays —
 * the IntUi default grays barely contrast against the islands panel background.
 */
@Composable
fun islandsComponentStyling(isDark: Boolean): ComponentStyling {
    val iconButtonStyle = if (isDark) {
        IconButtonStyle.iconDark(
            colors = IconButtonColors.iconDark(
                backgroundHovered = Color.White.copy(alpha = 0.10f),
                backgroundFocused = Color.White.copy(alpha = 0.10f),
                backgroundPressed = Color.White.copy(alpha = 0.16f),
            ),
        )
    } else {
        IconButtonStyle.iconLight(
            colors = IconButtonColors.iconLight(
                backgroundHovered = Color.Black.copy(alpha = 0.08f),
                backgroundFocused = Color.Black.copy(alpha = 0.08f),
                backgroundPressed = Color.Black.copy(alpha = 0.14f),
            ),
        )
    }
    return ComponentStyling.default().provide(LocalIconButtonStyle provides iconButtonStyle)
}
