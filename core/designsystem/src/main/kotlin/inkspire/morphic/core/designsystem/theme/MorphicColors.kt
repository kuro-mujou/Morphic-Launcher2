package inkspire.morphic.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles for the in-house design system. A token names a colour by *purpose*, not value, so a
 * `Morphic*` component reads role names and works unchanged under either scheme.
 *
 * The palette is deliberately **monochrome** — greyscale chrome that stays out of the way so the wallpaper
 * and app icons carry the colour. [accent] is a high-contrast greyscale *emphasis* (not a hue), so selected
 * and active states read by contrast; the only real colour is [error] (a red variant), reserved for
 * destructive / validation cases. State variants (pressed / hover / disabled) are alpha or overlay modifiers
 * on these roles, not extra roles. Both a [Light] and a [Dark] scheme exist — dark mode is an accessibility
 * barrier for some users, so light is a first-class peer, not an afterthought.
 */
@Immutable
data class MorphicColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val scrim: Color,
    val content: Color,
    val contentMuted: Color,
    val contentDisabled: Color,
    val accent: Color,
    val onAccent: Color,
    val accentMuted: Color,
    val outline: Color,
    val divider: Color,
    val trackInactive: Color,
    val trackActive: Color,
    val thumb: Color,
    val focusRing: Color,
    val error: Color,
    val onError: Color,
) {
    companion object {
        /** Monochrome dark scheme — white emphasis on near-black. */
        val Dark = MorphicColors(
            background = Color(0xFF0A0A0A),
            surface = Color(0xFF141414),
            surfaceElevated = Color(0xFF1E1E1E),
            scrim = Color(0x99000000),
            content = Color(0xFFFFFFFF),
            contentMuted = Color(0xFFA0A0A0),
            contentDisabled = Color(0xFF565656),
            accent = Color(0xFFFFFFFF),
            onAccent = Color(0xFF000000),
            accentMuted = Color(0xFF2A2A2A),
            outline = Color(0xFF2E2E2E),
            divider = Color(0xFF242424),
            trackInactive = Color(0xFF2E2E2E),
            trackActive = Color(0xFFFFFFFF),
            thumb = Color(0xFFFFFFFF),
            focusRing = Color(0xFFFFFFFF),
            error = Color(0xFFE5484D),
            onError = Color(0xFFFFFFFF),
        )

        /** Monochrome light scheme — near-black emphasis on white. */
        val Light = MorphicColors(
            background = Color(0xFFF2F2F2),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFFFFFFF),
            scrim = Color(0x66000000),
            content = Color(0xFF0A0A0A),
            contentMuted = Color(0xFF5A5A5A),
            contentDisabled = Color(0xFFB0B0B0),
            accent = Color(0xFF0A0A0A),
            onAccent = Color(0xFFFFFFFF),
            accentMuted = Color(0xFFE6E6E6),
            outline = Color(0xFFDADADA),
            divider = Color(0xFFEAEAEA),
            trackInactive = Color(0xFFDADADA),
            trackActive = Color(0xFF0A0A0A),
            thumb = Color(0xFF0A0A0A),
            focusRing = Color(0xFF0A0A0A),
            error = Color(0xFFD93A40),
            onError = Color(0xFFFFFFFF),
        )
    }
}

/** The current [MorphicColors]; defaults to [MorphicColors.Dark]. Provide the right scheme via [MorphicTheme]. */
val LocalMorphicColors = staticCompositionLocalOf { MorphicColors.Dark }

/**
 * Provides the [MorphicColors] scheme for [darkTheme] (defaults to the system setting). Wrap design-system
 * content so `Morphic*` components resolve their colours from [LocalMorphicColors].
 */
@Composable
fun MorphicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MorphicColors.Dark else MorphicColors.Light
    CompositionLocalProvider(LocalMorphicColors provides colors, content = content)
}

/**
 * Bridges this monochrome palette to an M3 [ColorScheme], so stock Material components (Button, Slider,
 * Switch, dialogs…) render monochrome *and* keep their Expressive motion — instead of showing M3's default
 * purple. [dark] only seeds the handful of roles we don't map. This is what lets us build components *on* M3
 * and merely restyle their colour, rather than rebuilding each from scratch.
 */
fun MorphicColors.toM3ColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentMuted,
        onPrimaryContainer = content,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = content,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = surfaceElevated,
        onTertiaryContainer = content,
        background = background,
        onBackground = content,
        surface = surface,
        onSurface = content,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = contentMuted,
        surfaceTint = accent,
        inverseSurface = content,
        inverseOnSurface = surface,
        surfaceBright = surfaceElevated,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceElevated,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceElevated,
        outline = outline,
        outlineVariant = divider,
        error = error,
        onError = onError,
        errorContainer = error,
        onErrorContainer = onError,
        scrim = scrim,
    )
}
