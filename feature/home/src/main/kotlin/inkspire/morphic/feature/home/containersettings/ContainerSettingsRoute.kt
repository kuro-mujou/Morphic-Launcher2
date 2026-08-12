package inkspire.morphic.feature.home.containersettings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A container's settings screen — **which container**, which is the only thing the screen cannot work out itself.
 *
 * **A destination rather than a settings section**, and this is the case `docs/CONTAINERS_PLAN.md` weighed and left
 * open. `feature:settings` is one destination whose sections are *panes*, and a pane configures a **surface**: home,
 * the dock, APPS. A container is an *instance* — there may be four of them on one home screen — and the settings
 * taxonomy has no vocabulary for "this one". Backing out of it means "stop configuring this container", which is a
 * back-stack entry's job.
 *
 * **Owned by `feature:home` rather than `feature:settings`**, because everything it reads is already wired here: the
 * container definitions come from `LayoutRepository`, its contents resolve through `AppRepository`, and adding a
 * widget runs the add flow this module owns. A screen in the settings module would have needed all of that
 * re-plumbed to configure an object it cannot otherwise see. Declared here and mapped in `app` for the reason
 * `IconStudioRoute` and `WallpaperCropRoute` are: `entryProvider` is a mapping, not a registry.
 *
 * **A sealed pair rather than an id beside a kind flag**, which is `IconStudioRoute`'s correction: the two
 * containers share no settings at all — one has an arrangement, the other an axis and two behaviors — so a single
 * key carrying both would be a state where half the fields are always meaningless.
 */
@Serializable
sealed interface ContainerSettingsRoute : NavKey {

    /** Which container. Shared because the screen's plumbing (load, back, delete) never needs to know which kind. */
    val containerId: Long

    /** An icon container: what it holds, and how those icons are arranged. */
    @Serializable
    @SerialName("container_settings_icon")
    data class Icon(override val containerId: Long) : ContainerSettingsRoute

    /** A widget container: what it holds, which way it pages, and the two behaviors that page it unasked. */
    @Serializable
    @SerialName("container_settings_widget")
    data class Widget(override val containerId: Long) : ContainerSettingsRoute
}
