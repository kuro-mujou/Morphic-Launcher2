package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.WidgetSpan

/**
 * The designs the picker offers. A starter set — one per provider that exists — standing in for the template library,
 * which is a design job of its own and replaces these rather than growing them.
 *
 * Each declares the globals its Style tab shows; a design with none is placed as authored and offers no Style row.
 */
object BuiltInWidgetTemplates {

    val all: List<WidgetTemplate> = listOf(clock(), date(), battery())

    private fun clock() = WidgetTemplate(
        id = "clock",
        name = "Clock",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 2),
            globals = listOf(
                WidgetGlobal.Color("text", "Text color", White),
                WidgetGlobal.Font("font", "Font", WidgetSource.Text.Font.SANS),
                WidgetGlobal.Switch("h24", "24-hour", true),
                WidgetGlobal.Switch("showDate", "Show date", true),
            ) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    // Two constant patterns rather than one built by `gv`: a pattern chosen at run time cannot be
                    // read for its tick, and would wake the widget every second instead of every minute.
                    WidgetSource.Text(
                        text = "\$if(gv(h24), df(HH:mm), df(h:mm))\$",
                        size = 60f,
                        weight = 300,
                        colorGlobal = "text",
                        fontGlobal = "font",
                    ),
                    offsetY = -10f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        text = "\$df(\"EEEE, d MMMM\")\$",
                        size = 14f,
                        weight = 500,
                        color = Muted,
                        fontGlobal = "font",
                    ),
                    anchor = WidgetAnchor.BOTTOM,
                    offsetY = -20f,
                    visibleGlobal = "showDate",
                ),
            ),
        ),
    )

    private fun date() = WidgetTemplate(
        id = "date",
        name = "Date",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = listOf(
                WidgetGlobal.Color("accent", "Accent", Accent),
                WidgetGlobal.Color("text", "Text color", White),
                WidgetGlobal.Font("font", "Font", WidgetSource.Text.Font.SANS),
            ) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        text = "\$tc(up, df(EEE))\$",
                        size = 13f,
                        weight = 700,
                        colorGlobal = "accent",
                        fontGlobal = "font",
                    ),
                    anchor = WidgetAnchor.TOP,
                    offsetY = 20f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(d)\$", size = 56f, weight = 300, colorGlobal = "text", fontGlobal = "font"),
                    offsetY = 10f,
                ),
            ),
        ),
    )

    private fun battery() = WidgetTemplate(
        id = "battery",
        name = "Battery",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = listOf(
                WidgetGlobal.Color("text", "Text color", White),
                WidgetGlobal.Switch("status", "Show status", true),
            ) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text("\$bi(level)\$%", size = 26f, weight = 400, colorGlobal = "text"),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 18f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$if(bi(charging), CHARGING, BATTERY)\$", size = 10f, weight = 700, color = Muted),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -18f,
                    visibleGlobal = "status",
                ),
            ),
        ),
    )

    /** The card every starter design sits on, and the two settings each offers for it. */
    private fun panelGlobals() = listOf(
        WidgetGlobal.Color("panel", "Background", Panel),
        WidgetGlobal.Number("round", "Corner roundness", value = 24f, min = 0f, max = 48f),
    )

    /** A dark translucent card under the text, so white reads over any wallpaper. */
    private fun panel() = WidgetLayerSpec(
        WidgetSource.Shape(color = Panel, cornerRadius = 24f, colorGlobal = "panel", cornerRadiusGlobal = "round"),
        width = WidgetExtent.Fill,
        height = WidgetExtent.Fill,
    )

    private const val White = 0xFFFFFFFF.toInt()
    private const val Muted = 0xB3FFFFFF.toInt()
    private const val Accent = 0xFFFF8A65.toInt()
    private const val Panel = 0x73000000
}
