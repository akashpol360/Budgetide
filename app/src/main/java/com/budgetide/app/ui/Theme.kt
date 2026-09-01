package com.budgetide.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ------------------------------------------------------------
// Brand palette - a livelier teal/emerald + warm amber/coral
// scheme instead of a single flat green, with a real dark theme.
// ------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6F2DF),
    onPrimaryContainer = Color(0xFF00201A),

    secondary = Color(0xFFB8860B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE7B0),
    onSecondaryContainer = Color(0xFF2A1C00),

    tertiary = Color(0xFF6750A4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF22005D),

    error = Color(0xFFD64545),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    surface = Color(0xFFF7FBF9),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDCE9E3),
    background = Color(0xFFF7FBF9),
    onBackground = Color(0xFF171D1B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FDBBE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFFB6F2DF),

    secondary = Color(0xFFF3C05C),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE7B0),

    tertiary = Color(0xFFCFBDFE),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378A),
    onTertiaryContainer = Color(0xFFEADDFF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    surface = Color(0xFF101513),
    onSurface = Color(0xFFDDE4E0),
    surfaceVariant = Color(0xFF3F4946),
    background = Color(0xFF101513),
    onBackground = Color(0xFFDDE4E0)
)

@Composable
fun BudgetideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

// ------------------------------------------------------------
// Semantic colors used across the app so money direction and
// status are colour-coded consistently (income vs expense,
// owed-to-you vs you-owe, warnings, etc). Each adapts to the
// current light/dark theme.
// ------------------------------------------------------------

@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) Color(0xFF6FDBBE) else Color(0xFF0E7C66)

@Composable
fun expenseColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFD64545)

@Composable
fun warningColor(): Color = if (isSystemInDarkTheme()) Color(0xFFF3C05C) else Color(0xFFB8860B)

@Composable
fun neutralAccentColor(): Color = if (isSystemInDarkTheme()) Color(0xFFCFBDFE) else Color(0xFF6750A4)

// A varied, theme-aware palette used to give each spending category its
// own distinct colour in charts/bars, picked deterministically by name
// so the same category always gets the same colour.
@Composable
fun categoryColor(name: String): Color {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) {
        listOf(
            Color(0xFF6FDBBE), Color(0xFFF3C05C), Color(0xFFCFBDFE),
            Color(0xFFFFB4AB), Color(0xFF9ED3FF), Color(0xFFFFA8D6),
            Color(0xFFB5E36B), Color(0xFFFFC48C)
        )
    } else {
        listOf(
            Color(0xFF0E7C66), Color(0xFFB8860B), Color(0xFF6750A4),
            Color(0xFFD64545), Color(0xFF2E7DBE), Color(0xFFC2478A),
            Color(0xFF5F8B2E), Color(0xFFC7681E)
        )
    }
    val index = (name.hashCode().let { if (it < 0) -it else it }) % palette.size
    return palette[index]
}

// Blends a semantic colour into the current surface colour to make a soft,
// theme-appropriate pastel container colour for cards - gives each card a
// distinct tint (income/expense/warning/category etc) instead of every
// card being the same flat surface colour, without needing raw hardcoded
// backgrounds that would look wrong in dark mode.
@Composable
fun tintedContainer(tint: Color, amount: Float = 0.24f): Color =
    lerp(MaterialTheme.colorScheme.surface, tint, amount)
