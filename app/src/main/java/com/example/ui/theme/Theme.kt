package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.models.LayoutDensity
import com.example.domain.models.ThemeMode

private val KlipzDarkColorScheme = darkColorScheme(
    primary = CrispWhite,
    onPrimary = Obsidian950,
    primaryContainer = Slate750,
    onPrimaryContainer = CrispWhite,
    secondary = Silver300,
    onSecondary = Obsidian950,
    secondaryContainer = Graphite800,
    onSecondaryContainer = CrispWhite,
    tertiary = Zinc400,
    onTertiary = Obsidian950,
    tertiaryContainer = Charcoal850,
    onTertiaryContainer = Silver300,
    background = Obsidian950,
    onBackground = CrispWhite,
    surface = Charcoal850,
    onSurface = CrispWhite,
    surfaceVariant = Graphite800,
    onSurfaceVariant = Zinc400,
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Charcoal850,
    surfaceContainerHigh = Graphite800,
    surfaceContainerHighest = Slate750,
    outline = HairlineDark,
    outlineVariant = HairlineSubtleDark,
    error = StatusErrorDark,
    onError = Obsidian950,
    errorContainer = Color(0xFF33181A),
    onErrorContainer = StatusErrorDark,
    inverseSurface = CrispWhite,
    inverseOnSurface = Obsidian950,
    inversePrimary = Obsidian950
)

private val KlipzLightColorScheme = lightColorScheme(
    primary = Carbon900,
    onPrimary = PureWhite,
    primaryContainer = Mist200,
    onPrimaryContainer = Carbon900,
    secondary = Carbon700,
    onSecondary = PureWhite,
    secondaryContainer = Mist100,
    onSecondaryContainer = Carbon900,
    tertiary = Zinc600,
    onTertiary = PureWhite,
    tertiaryContainer = Mist100,
    onTertiaryContainer = Carbon700,
    background = Alabaster50,
    onBackground = Carbon900,
    surface = PureWhite,
    onSurface = Carbon900,
    surfaceVariant = Mist100,
    onSurfaceVariant = Zinc600,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = Alabaster50,
    surfaceContainer = PureWhite,
    surfaceContainerHigh = Mist100,
    surfaceContainerHighest = Mist200,
    outline = HairlineLight,
    outlineVariant = Mist200,
    error = StatusErrorLight,
    onError = PureWhite,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = StatusErrorLight,
    inverseSurface = Carbon900,
    inverseOnSurface = PureWhite,
    inversePrimary = PureWhite
)

val KlipzShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Immutable
data class KlipzExtendedColors(
    val success: Color,
    val warning: Color,
    val subtleText: Color
)

@Immutable
data class KlipzSpacing(
    val screenPadding: Dp,
    val cardPadding: Dp,
    val sectionGap: Dp,
    val itemGap: Dp
)

val LocalKlipzColors = staticCompositionLocalOf {
    KlipzExtendedColors(
        success = StatusSuccessDark,
        warning = StatusWarningDark,
        subtleText = Zinc500
    )
}

val LocalKlipzSpacing = staticCompositionLocalOf {
    KlipzSpacing(
        screenPadding = 16.dp,
        cardPadding = 16.dp,
        sectionGap = 16.dp,
        itemGap = 10.dp
    )
}

@Composable
fun KlipzTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    layoutDensity: LayoutDensity = LayoutDensity.COMFORTABLE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) KlipzDarkColorScheme else KlipzLightColorScheme
    val extendedColors = if (isDark) {
        KlipzExtendedColors(
            success = StatusSuccessDark,
            warning = StatusWarningDark,
            subtleText = Zinc500
        )
    } else {
        KlipzExtendedColors(
            success = StatusSuccessLight,
            warning = StatusWarningLight,
            subtleText = Zinc500
        )
    }
    val spacing = when (layoutDensity) {
        LayoutDensity.COMFORTABLE -> KlipzSpacing(
            screenPadding = 16.dp,
            cardPadding = 16.dp,
            sectionGap = 16.dp,
            itemGap = 10.dp
        )
        LayoutDensity.COMPACT -> KlipzSpacing(
            screenPadding = 12.dp,
            cardPadding = 12.dp,
            sectionGap = 10.dp,
            itemGap = 6.dp
        )
    }

    CompositionLocalProvider(
        LocalKlipzColors provides extendedColors,
        LocalKlipzSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = KlipzShapes,
            content = content
        )
    }
}
