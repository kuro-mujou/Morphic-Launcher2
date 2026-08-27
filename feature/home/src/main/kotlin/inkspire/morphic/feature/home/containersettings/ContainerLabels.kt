package inkspire.morphic.feature.home.containersettings

import inkspire.morphic.core.model.FanAnchor
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
 * An arrangement's display name — the shape, and for a fan the corner it pivots on.
 *
 * The fan is named by its corner rather than by a direction because the corner is what a user sees: the arcs sweep
 * out of it and the icons open away from it.
 */
internal val IconArrangement.label: String
    get() = when (this) {
        IconArrangement.Grid -> "Grid"
        IconArrangement.Circle -> "Circle"
        IconArrangement.Beehive -> "Beehive"
        is IconArrangement.Fan -> "Fan from ${anchor.label}"
    }

/** A fan anchor's display name, read as the tail of an arrangement's — "Fan from *top left*". */
internal val FanAnchor.label: String
    get() = when (this) {
        FanAnchor.TOP_LEFT -> "top left"
        FanAnchor.TOP_RIGHT -> "top right"
        FanAnchor.BOTTOM_LEFT -> "bottom left"
        FanAnchor.BOTTOM_RIGHT -> "bottom right"
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
