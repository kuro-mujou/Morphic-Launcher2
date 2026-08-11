package inkspire.morphic.feature.settings.iconstudio

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The icon studio's destination — **what is being edited**, which is the only thing the studio cannot work out for
 * itself.
 *
 * **A destination rather than a settings section**, for `WallpaperCropRoute`'s reason: the sections are panes, two of
 * which share the screen on a tablet, so "which section" is `SettingsScreen`'s state. The studio is full-screen, it
 * has its own chrome, and backing out of it means "stop editing this icon" — which is a back-stack entry's job.
 * Declared in this module rather than in `core:navigation` for the same reason that one is: `entryProvider` in `app`
 * is a mapping and not a registry, so a destination that belongs to one feature stays in it. L1 put every route in
 * its navigation module, which is how an eleven-value settings enum reached `feature:home`'s compile classpath.
 *
 * **A sealed pair rather than a mode enum beside a nullable component.** L1's route was `(mode, component?)`, which
 * can express `GLOBAL` carrying a component — a combination with no meaning that every reader has to notice is
 * impossible. Here the global case carries nothing because there is nothing for it to carry.
 */
@Serializable
sealed interface IconStudioRoute : NavKey {

    /**
     * Edit the **global default** recipe — the one every app inherits until it is edited individually.
     *
     * Previewed on a sample app rather than on nothing, since a recipe only means something drawn over real
     * artwork.
     */
    @Serializable
    @SerialName("icon_studio_global")
    data object Global : IconStudioRoute

    /**
     * Edit **one app's** recipe.
     *
     * @property component the app, as a flattened `ComponentKey` string: a `NavKey` must be `@Serializable` and
     *   `ComponentKey` is a model type this module would otherwise be pinning into the back stack's stored form.
     *   Null means *no app chosen yet* — the studio opens on its picker and this becomes the choice. That is a real
     *   state rather than a missing argument: it is how the dashboard's "Edit specific apps" arrives, where the
     *   item menu's "Edit icon" already knows which app it means.
     */
    @Serializable
    @SerialName("icon_studio_app")
    data class App(val component: String? = null) : IconStudioRoute
}
