package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.util.lerp
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.BackdropEffect

/**
 * The relative luminance at which dark text starts beating light text on a background.
 *
 * **Not a taste value** — it is where the two WCAG contrast ratios cross. Contrast against white is
 * `1.05 / (L + 0.05)` and against black is `(L + 0.05) / 0.05`; setting them equal gives `(L + 0.05)² = 0.0525`, so
 * `L ≈ 0.179`. Above it a background wants dark chrome, below it light.
 *
 * `data:wallpaper` holds the same number for one job of its own (reconciling the OS's dark-text hint with the number
 * beside it) and the duplication is deliberate: a `core` module cannot read a `data` one, and a derived constant is
 * the one kind of value that is safe to state twice — there is nothing to prefer, so there is nothing to drift.
 */
const val DarkTextLuminance = 0.179f

/** Whether a background of [luminance] wants light text on it. */
fun isDarkBackground(luminance: Float): Boolean = luminance < DarkTextLuminance

/**
 * The luminance of a **wash over a picture** — what a frosted surface actually shows.
 *
 * The renderer draws the sampled crop and then paints [wash] over it, so what the eye sees is the two composited at
 * the wash's own alpha. That is exactly a `lerp`, and it is the whole reason the wallpaper's brightness had to stop
 * being a light/dark verdict: a verdict cannot be mixed 35% with a color.
 *
 * @param picture the luminance of what is being sampled — the wallpaper's mean.
 * @param wash the color painted over it, **alpha included**. `Color.Transparent` leaves [picture] untouched, which is
 *   what a tint of `BackdropTint.NONE` resolves to.
 */
fun washedLuminance(picture: Float, wash: Color): Float = lerp(picture, wash.luminance(), wash.alpha)

/**
 * The **full-screen film** as the chrome needs to know it: the color its wash is struck from, and whether it is dark
 * enough to want light text on it.
 *
 * **One type because it is one material described twice**, and the two must be measured together: a wash weighed with
 * one tone and painted with another is exactly the silent disagreement this subsystem keeps rediscovering.
 */
class Film(val tone: Color, val isDark: Boolean)

/**
 * The resolved [Film], or null outside the launcher shell.
 *
 * **Resolved once, above every surface, and that placement is the whole point.** Both halves pass through
 * [wallpaperTone], which is 70% `MaterialTheme.colorScheme.surfaceVariant` — so evaluating either inside a subtree
 * that has re-themed itself against the film gives a different answer than the shell got. Two films would then paint
 * in two colors for one effect, and a surface deciding its own darkness from a tone that depends on that decision can
 * settle either way near the crossover. One reading above them all, and neither can happen.
 *
 * Null is the honest value outside the shell — a settings preview draws one panel under one theme and has no second
 * film to agree with — which is what [filmIsDark] and [filmTone] turn into each half's own sensible default.
 */
val LocalFilm = staticCompositionLocalOf<Film?> { null }

/**
 * Whether the film wants light text on it.
 *
 * **True outside the shell**, the same safer miss the wallpaper reading defaults to: light chrome over an unexpectedly
 * bright film is unreadable, dark chrome over a dark one is merely dull.
 */
@Composable
fun filmIsDark(): Boolean = LocalFilm.current?.isDark ?: true

/** The film's wash base, or `Color.Unspecified` outside the shell — which means "work it out from the theme here". */
@Composable
fun filmTone(): Color = LocalFilm.current?.tone ?: Color.Unspecified

/**
 * Measures the [Film] for [effect] — for the shell, which is the only caller that should.
 *
 * **The wash it weighs is [backdropTint]'s**, the same expression the renderer paints, and it is handed the tone this
 * function just struck rather than reading it again. That is the standing rule for anything two paths must agree on:
 * the color deciding the text and the color behind the text are one derivation, not two that look alike.
 *
 * @param wallpaperLuminance the wallpaper's mean, or **null when there is no picture to sample**. A film with nothing
 *   to sample is its own flat scrim, and a scrim is a theme color — so it already contrasts the enclosing theme's
 *   content, and the honest answer is [fallback] rather than a number invented for the occasion.
 * @param fallback what to answer with nothing to measure: the enclosing theme's own darkness.
 * @param accent the wallpaper's representative color. Passed rather than read from `LocalBackdrop`, since the shell
 *   resolves this in the same call that provides it.
 */
@Composable
fun resolveFilm(effect: BackdropEffect, wallpaperLuminance: Float?, fallback: Boolean, accent: Color?): Film {
    val tone = wallpaperTone(accent)
    val wash = backdropTint(effect.fullScreenFilm, tone)
    return Film(
        tone = tone,
        isDark = if (wallpaperLuminance == null) {
            fallback
        } else {
            isDarkBackground(washedLuminance(wallpaperLuminance, wash))
        },
    )
}

/**
 * Declares that [content] is drawn **on the full-screen film**: it must not frost itself again, and it must be themed
 * against the film rather than against the wallpaper.
 *
 * **One call for both, because they are one fact.** A surface that arrives over the film has to say so twice
 * otherwise — once so a menu or a plate inside it renders flat ([LocalOverFrost]), once so its text contrasts what it
 * is actually sitting on — and the second is the half nobody remembers, because forgetting it is invisible until
 * someone picks a wash that crosses the threshold. The set of surfaces needing each is identical, so there is one
 * declaration and no way to get half of it.
 *
 * **Not for the layer that *draws* the film**, which is `SurfaceBackdropLayer` and is neither on one nor themed by
 * one — see its own opt-out.
 */
@Composable
fun OnFilm(content: @Composable () -> Unit) {
    LauncherTheme(darkTheme = filmIsDark()) {
        CompositionLocalProvider(
            value = LocalOverFrost provides true,
            content = content
        )
    }
}

/**
 * Declares that [content] is drawn **on a frosted panel** — a container tile — and themes it against that panel.
 *
 * **A panel is a third background, not the film and not the wallpaper.** It samples at the user's own blur and wears
 * the user's own wash, so a container on a bright wallpaper with a dark wash is a dark tile on a light screen: HOME's
 * theme says dark text and the tile underneath wants light. Unlike the film there is nothing global to resolve —
 * every panel is on HOME, under one theme, so the reading is local and cheap.
 *
 * **It sets [LocalOverFrost] as well**, exactly as [OnFilm] does, because a panel is already-blurred wallpaper for
 * the same reason a film is. An icon plate inside a container was the case that proved it: a silhouette of the
 * wallpaper, sampled a second time, floating on a tile that had already blurred it. This KDoc used to say the
 * opposite — that a panel was too small for the rule to matter — which was wrong, and wrong in the way a stacking
 * blur always is: visible only as a patch that looks slightly *sharper* than what surrounds it.
 *
 * Falls back to the enclosing theme when there is no wallpaper to sample, for [resolveFilm]'s reason: the panel is
 * then its own flat scrim, which is a theme color and contrasts by construction.
 */
@Composable
fun OnPanel(content: @Composable () -> Unit) {
    val backdrop = LocalBackdrop.current
    val enclosing = isDarkBackground(LocalMorphicColors.current.background.luminance())
    val dark = if (backdrop == null) {
        enclosing
    } else {
        isDarkBackground(washedLuminance(backdrop.luminance, backdropTint()))
    }
    LauncherTheme(darkTheme = dark) {
        CompositionLocalProvider(
            value = LocalOverFrost provides true,
            content = content
        )
    }
}
