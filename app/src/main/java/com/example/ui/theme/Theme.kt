package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ExamPilotDarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = MintSecondary,
    onSecondary = MintOnSecondary,
    secondaryContainer = MintContainer,
    onSecondaryContainer = MintOnContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer,
    background = MidnightBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = MidnightBg,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceVariant,
    surfaceContainerHigh = MidnightSurfaceElevated,
    outline = MidnightOutline,
    outlineVariant = MidnightSurfaceElevated,
)

private val ExamPilotLightColorScheme = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoOnContainerLight,
    secondary = MintSecondaryLight,
    onSecondary = MintOnSecondaryLight,
    secondaryContainer = MintContainerLight,
    onSecondaryContainer = MintOnContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    error = CoralErrorLight,
    onError = CoralOnErrorLight,
    errorContainer = CoralContainerLight,
    onErrorContainer = CoralOnContainerLight,
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightBg,
    surfaceContainer = DaylightSurfaceVariant,
    outline = DaylightOutline,
)

val ExamPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ExamPilotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ExamPilotDarkColorScheme else ExamPilotLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExamPilotTypography,
        shapes = ExamPilotShapes,
        content = content,
    )
}
