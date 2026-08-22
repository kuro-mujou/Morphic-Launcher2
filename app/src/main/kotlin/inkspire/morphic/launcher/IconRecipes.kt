package inkspire.morphic.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.icon.compose.LocalIconAppearance
import inkspire.morphic.core.icon.compose.LocalIconOverrides
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.data.icons.IconOverrideRepository
import inkspire.morphic.data.settings.SettingsRepository
import org.koin.compose.koinInject

/**
 * Provides the two things that decide what an app icon looks like: the **global default** recipe (from
 * `data:settings`) and the **per-app overrides** that detach individual apps from it (from `data:icons`).
 *
 * **Wrapped around the whole navigation graph, not around the launcher shell.** An icon is drawn on the launcher
 * surfaces, in the settings previews and — soon — in the studio itself, and all three must agree about what the
 * user's icons look like; scoping this to one zone is how a settings preview ends up showing a different icon from
 * the home screen it is previewing. That is the opposite call from the *theme*, which is deliberately per zone
 * (the launcher follows wallpaper brightness, settings follows the system) because those genuinely differ.
 *
 * **It lives here rather than in `MainActivity` or `LauncherNavHost`,** each for its own reason. The Activity's
 * whole design is that it provides what it *owns* from DI and hosts the graph — collecting repository flows there
 * is the L1 mistake its KDoc names, where 204 lines of `setContent` mixed wallpaper loading and cache invalidation
 * with the nav wiring. And the nav host is navigation and nothing else. So this is a third small thing that `app`
 * assembles, which is `app`'s job.
 *
 * Both are read with [collectAsStateWithLifecycle], so an icon edit lands as soon as it is written and nothing is
 * collected while the launcher is in the background.
 */
@Composable
fun ProvideIconRecipes(content: @Composable () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val overrides: IconOverrideRepository = koinInject()
    val defaultSet by settings.iconAppearance.collectAsStateWithLifecycle(IconAppearance.Base)
    val perApp by overrides.overrides.collectAsStateWithLifecycle(emptyMap())

    CompositionLocalProvider(
        LocalIconAppearance provides defaultSet,
        LocalIconOverrides provides perApp,
        content = content,
    )
}
