package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.WidgetSpan

/**
 * The designs the picker offers. A starter set — one per provider that exists — standing in for the template library,
 * which is a design job of its own and replaces these rather than growing them.
 */
object BuiltInWidgetTemplates {

    val all: List<WidgetTemplate> = listOf(
        WidgetTemplate(
            id = "clock",
            name = "Clock",
            recipe = WidgetRecipe(
                span = WidgetSpan(cols = 4, rows = 2),
                layers = listOf(
                    panel(),
                    WidgetLayerSpec(WidgetSource.Text("\$df(HH:mm)\$", size = 60f, weight = 300), offsetY = -10f),
                    WidgetLayerSpec(
                        WidgetSource.Text("\$df(\"EEEE, d MMMM\")\$", size = 14f, weight = 500, color = Muted),
                        anchor = WidgetAnchor.BOTTOM,
                        offsetY = -20f,
                    ),
                ),
            ),
        ),
        WidgetTemplate(
            id = "date",
            name = "Date",
            recipe = WidgetRecipe(
                span = WidgetSpan(cols = 2, rows = 2),
                layers = listOf(
                    panel(),
                    WidgetLayerSpec(
                        WidgetSource.Text("\$tc(up, df(EEE))\$", size = 13f, weight = 700, color = Accent),
                        anchor = WidgetAnchor.TOP,
                        offsetY = 20f,
                    ),
                    WidgetLayerSpec(WidgetSource.Text("\$df(d)\$", size = 56f, weight = 300), offsetY = 10f),
                ),
            ),
        ),
        WidgetTemplate(
            id = "battery",
            name = "Battery",
            recipe = WidgetRecipe(
                span = WidgetSpan(cols = 2, rows = 1),
                layers = listOf(
                    panel(),
                    WidgetLayerSpec(
                        WidgetSource.Text("\$bi(level)\$%", size = 26f, weight = 400),
                        anchor = WidgetAnchor.LEFT,
                        offsetX = 18f,
                    ),
                    WidgetLayerSpec(
                        WidgetSource.Text("\$if(bi(charging), CHARGING, BATTERY)\$", size = 10f, weight = 700, color = Muted),
                        anchor = WidgetAnchor.RIGHT,
                        offsetX = -18f,
                    ),
                ),
            ),
        ),
    )

    /** A dark translucent card under the text, so white reads over any wallpaper. */
    private fun panel() = WidgetLayerSpec(
        WidgetSource.Shape(color = 0x73000000, cornerRadius = 24f),
        width = WidgetExtent.Fill,
        height = WidgetExtent.Fill,
    )

    private const val Muted = 0xB3FFFFFF.toInt()
    private const val Accent = 0xFFFF8A65.toInt()
}
