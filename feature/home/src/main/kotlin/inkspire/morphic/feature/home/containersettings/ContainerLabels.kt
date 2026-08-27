package inkspire.morphic.feature.home.containersettings

import inkspire.morphic.core.model.FanAnchor
import inkspire.morphic.core.model.GridFill
import inkspire.morphic.core.model.HexOrientation
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
 * An arrangement's display name — **the shape and what it is set to**, in one string rather than two.
 *
 * A shape whose parameter is a picture (the fan's corner) and one whose parameter is a number (the grid's pinned
 * axis) still share a caption, so the caption says the whole thing: the row carrying it names *this* container's
 * arrangement, and a bare "Grid" over a control set to three columns would be the one part of the screen not
 * saying so. The control below it is where either is changed.
 *
 * The fan is named by its corner rather than by a direction because the corner is what a user sees: the arcs sweep
 * out of it and the icons open away from it.
 */
internal val IconArrangement.label: String
    get() = when (this) {
        is IconArrangement.Grid -> when (val fill = fill) {
            GridFill.Auto -> "Grid"
            is GridFill.Columns -> "Grid, " + plural(fill.count, "column")
            is GridFill.Rows -> "Grid, " + plural(fill.count, "row")
        }

        IconArrangement.Circle -> "Circle"
        is IconArrangement.Beehive -> "Beehive, " + orientation.label
        is IconArrangement.Fan -> "Fan from ${anchor.label}"
    }

/**
 * A hex orientation's display name.
 *
 * Named for the cell rather than for the turn, because "flat top" is a thing to look for in the picture and "30
 * degrees" is a thing to work out from it.
 */
internal val HexOrientation.label: String
    get() = when (this) {
        HexOrientation.FLAT_TOP -> "flat top"
        HexOrientation.POINTY_TOP -> "pointy top"
    }

/** [count] of [word], pluralized — the only plural this vocabulary has, so it is a line rather than a facility. */
private fun plural(count: Int, word: String): String = if (count == 1) "1 " + word else "$count ${word}s"

/**
 * A fan anchor's display name, read as the tail of an arrangement's — "Fan from *top left*".
 *
 * An edge is named by the edge alone ("Fan from top"), which is also how it differs from the corners beside it: the
 * name says where it pivots, and the sweep follows from that rather than needing saying.
 */
internal val FanAnchor.label: String
    get() = when (this) {
        FanAnchor.TOP_LEFT -> "top left"
        FanAnchor.TOP -> "top"
        FanAnchor.TOP_RIGHT -> "top right"
        FanAnchor.LEFT -> "left"
        FanAnchor.RIGHT -> "right"
        FanAnchor.BOTTOM_LEFT -> "bottom left"
        FanAnchor.BOTTOM -> "bottom"
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
