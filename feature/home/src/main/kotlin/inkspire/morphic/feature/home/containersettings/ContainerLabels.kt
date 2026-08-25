package inkspire.morphic.feature.home.containersettings

import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.WidgetContainerAxis

/*
 * The container vocabulary, in one place — the screen's headings and the two enums' display names.
 *
 * One file for the same reason `feature:settings` keeps `LayoutLabels.kt`: `AppsLayout.label` had existed twice with
 * *different* strings, each promising in KDoc to move when a second screen needed it. These are read by the screen's
 * chrome, its chooser rows and its chooser dialogs, so they are already past that point.
 */

/** The screen's heading — what the user opened. */
internal fun ContainerSettingsRoute.title(): String = when (this) {
    is ContainerSettingsRoute.Icon -> "Icon container"
    is ContainerSettingsRoute.Widget -> "Widget stack"
}

/** The add affordance's label. */
internal fun ContainerSettingsRoute.addLabel(): String = when (this) {
    is ContainerSettingsRoute.Icon -> "Add apps"
    is ContainerSettingsRoute.Widget -> "Add widget"
}

/**
 * An arrangement's display name.
 *
 * The four fans are named by the corner they pivot on rather than by a direction, because that corner is what a
 * user sees: one icon sits in it and the rest cascade inward.
 */
internal val IconArrangement.label: String
    get() = when (this) {
        IconArrangement.GRID -> "Grid"
        IconArrangement.CIRCLE -> "Circle"
        IconArrangement.FAN_TOP_LEFT -> "Fan from top left"
        IconArrangement.FAN_TOP_RIGHT -> "Fan from top right"
        IconArrangement.FAN_BOTTOM_LEFT -> "Fan from bottom left"
        IconArrangement.FAN_BOTTOM_RIGHT -> "Fan from bottom right"
        IconArrangement.BEEHIVE -> "Beehive"
    }

/**
 * An axis's display name — phrased as the **swipe**, not as a stacking direction.
 *
 * That is the model's own correction carried into the words: `WidgetContainerAxis` says which way the finger goes,
 * and a label reading "vertical stack" would put back exactly the misreading the KDoc exists to prevent.
 */
internal val WidgetContainerAxis.label: String
    get() = when (this) {
        WidgetContainerAxis.HORIZONTAL -> "Horizontal"
        WidgetContainerAxis.VERTICAL -> "Vertical"
    }
