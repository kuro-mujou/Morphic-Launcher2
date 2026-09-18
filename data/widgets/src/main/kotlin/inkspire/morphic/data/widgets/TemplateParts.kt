package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetSource

/**
 * The pieces [BuiltInWidgetTemplates] are assembled from — the panel, the label-figure-bar of a progress design, the
 * globals most designs declare — so a family of designs shares one look rather than twelve near-copies of it.
 *
 * Global names are shared too, and that is what makes them load-bearing: a part binding `"accent"` draws in whatever
 * the design's own `accent` global holds, and in its authored color if the design declares none.
 */
internal object TemplateParts {

    /** A battery ring with its percentage inside, as a group so the two stay together wherever it is placed. */
    fun batteryDial(ring: Float, textSize: Float) = WidgetSource.Overlap(
        listOf(
            WidgetLayerSpec(
                WidgetSource.Progress(
                    "\$bi(level)\$",
                    WidgetSource.Progress.Kind.ARC,
                    thickness = ring,
                    trackColor = Track,
                    colorGlobal = "accent",
                ),
                width = WidgetExtent.Fill,
                height = WidgetExtent.Fill,
            ),
            WidgetLayerSpec(WidgetSource.Text("\$bi(level)\$%", size = textSize, weight = 600, colorGlobal = "text")),
        ),
    )

    /** The small upper-case line over a progress design's figure. */
    fun barLabel(text: String, visibleGlobal: String? = null) = WidgetLayerSpec(
        WidgetSource.Text(text, size = 12f, weight = 700, color = Muted),
        anchor = WidgetAnchor.TOP_LEFT,
        offsetX = 20f,
        offsetY = 22f,
        visibleGlobal = visibleGlobal,
    )

    /** A progress design's figure, left-aligned under its label. */
    fun bigFigure(text: String) = WidgetLayerSpec(
        WidgetSource.Text(text, size = 38f, weight = 300, colorGlobal = "text"),
        anchor = WidgetAnchor.LEFT,
        offsetX = 19f,
        offsetY = 2f,
    )

    /** The bar along a progress design's foot, spanning its width with a margin that grows with it. */
    fun bar(progress: WidgetSource.Progress) = WidgetLayerSpec(
        progress,
        anchor = WidgetAnchor.BOTTOM,
        offsetY = -22f,
        width = WidgetExtent.Fraction(value = 0.8f),
        height = WidgetExtent.Dp(value = 8f),
    )

    fun text() = WidgetGlobal.Color("text", "Text color", White)

    fun accent(color: Int) = WidgetGlobal.Color("accent", "Accent", color)

    fun font(font: WidgetSource.Text.Font = WidgetSource.Text.Font.SANS) = WidgetGlobal.Font("font", "Font", font)

    fun h24() = WidgetGlobal.Switch("h24", "24-hour", true)

    /** The card every design sits on, and the two settings each offers for it. */
    fun panelGlobals() = listOf(
        WidgetGlobal.Color("panel", "Background", Panel),
        WidgetGlobal.Number("round", "Corner roundness", value = 28f, min = 0f, max = 48f),
    )

    /** A dark translucent card under the text, so white reads over any wallpaper. */
    fun panel() = WidgetLayerSpec(
        WidgetSource.Shape(color = Panel, cornerRadius = 28f, colorGlobal = "panel", cornerRadiusGlobal = "round"),
        width = WidgetExtent.Fill,
        height = WidgetExtent.Fill,
    )

    /** "Charging" while it is, [otherwise] when it is not. */
    fun chargingOr(otherwise: String) = "\$tc(up, if(bi(charging), \"Charging\", \"$otherwise\"))\$"

    const val Time = "\$if(gv(h24), df(HH:mm), df(h:mm))\$"

    const val White = 0xFFFFFFFF.toInt()
    const val Muted = 0xB3FFFFFF.toInt()
    const val Track = 0x2EFFFFFF
    const val Panel = 0x8C000000.toInt()
    const val Coral = 0xFFFF8A65.toInt()
    const val Mint = 0xFF7FD4B6.toInt()
    const val Sky = 0xFF8AB4F8.toInt()
    const val Gold = 0xFFF2C46D.toInt()
}
