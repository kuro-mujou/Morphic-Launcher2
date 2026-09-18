package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.data.widgets.TemplateParts.Coral
import inkspire.morphic.data.widgets.TemplateParts.Gold
import inkspire.morphic.data.widgets.TemplateParts.Mint
import inkspire.morphic.data.widgets.TemplateParts.Muted
import inkspire.morphic.data.widgets.TemplateParts.Sky
import inkspire.morphic.data.widgets.TemplateParts.Time
import inkspire.morphic.data.widgets.TemplateParts.Track
import inkspire.morphic.data.widgets.TemplateParts.accent
import inkspire.morphic.data.widgets.TemplateParts.batteryDial
import inkspire.morphic.data.widgets.TemplateParts.block
import inkspire.morphic.data.widgets.TemplateParts.chargingOr
import inkspire.morphic.data.widgets.TemplateParts.font
import inkspire.morphic.data.widgets.TemplateParts.h24
import inkspire.morphic.data.widgets.TemplateParts.line
import inkspire.morphic.data.widgets.TemplateParts.text

/**
 * A block the library offers to add to a placed widget — the tier-2 unit, a finished piece of a design with the
 * settings it owns.
 *
 * @property layer the block itself, a named group, anchored at the center of whatever it is added to. Adding copies it,
 *   so later changes to the library never reach a widget it was added to.
 */
data class WidgetBlock(val id: String, val name: String, val layer: WidgetLayerSpec)

/**
 * The blocks anyone can add to a widget. **Each is sized by its content**, not by the widget it lands in, since it has
 * no widget to be sized by until it lands — so where a template pins a battery card's parts to the widget's edges, the
 * library's battery bar lays its parts out against its own top-left corner at fixed widths.
 *
 * The templates build their time the same way ([time]), so a Clock placed from a template and a Time block added to it
 * later are the same thing.
 */
object BuiltInBlocks {

    val all: List<WidgetBlock> = listOf(
        entry("time", time(size = 56f, weight = 300)),
        entry("stacked_time", stackedTime()),
        entry("date", date()),
        entry("weekday", weekday()),
        entry("day_number", dayNumber()),
        entry("greeting", greeting()),
        entry("label", label()),
        entry("battery_ring", batteryRing()),
        entry("battery_bar", batteryBar()),
        entry("day_bar", dayBar()),
        entry("year_bar", yearBar()),
        entry("hour_ring", hourRing()),
    )

    /** A clock's time: its color, font and 24-hour switch. */
    fun time(size: Float, weight: Int) = block(
        name = "Time",
        globals = listOf(text(), font(), h24()),
        layers = listOf(line(Time, size = size, weight = weight, color = "text")),
    )

    private fun entry(id: String, layer: WidgetLayerSpec) = WidgetBlock(id, layer.name.orEmpty(), layer)

    private fun stackedTime() = block(
        name = "Stacked time",
        globals = listOf(text(), accent(Coral), font(), h24()),
        layers = listOf(
            line("\$if(gv(h24), df(HH), df(hh))\$", size = 64f, weight = 700, color = "text", y = -34f),
            line("\$df(mm)\$", size = 64f, weight = 700, color = "accent", y = 34f),
        ),
    )

    private fun date() = block(
        name = "Date",
        globals = listOf(accent(Sky), WidgetGlobal.Color("text", "Text color", Muted), font()),
        layers = listOf(
            line("\$df(EEEE)\$", size = 15f, weight = 600, color = "accent", y = -11f),
            line("\$df(\"d MMMM\")\$", size = 15f, color = "text", y = 11f),
        ),
    )

    private fun weekday() = block(
        name = "Weekday",
        globals = listOf(text(), font(WidgetSource.Text.Font.SERIF)),
        layers = listOf(
            line("\$df(EEEE)\$", size = 40f, color = "text", anchor = WidgetAnchor.LEFT, y = -11f),
            line("\$df(\"d MMMM yyyy\")\$", size = 14f, anchor = WidgetAnchor.LEFT, x = 2f, y = 24f),
        ),
    )

    private fun dayNumber() = block(
        name = "Day number",
        globals = listOf(accent(Coral), text(), font(WidgetSource.Text.Font.SERIF)),
        layers = listOf(
            line("\$tc(up, df(MMMM))\$", size = 12f, weight = 700, color = "accent", y = -50f),
            line("\$df(d)\$", size = 72f, weight = 300, color = "text"),
            line("\$df(EEEE)\$", size = 14f, y = 50f),
        ),
    )

    private fun greeting() = block(
        name = "Greeting",
        globals = listOf(text(), font()),
        layers = listOf(
            line(
                "\$if(df(H) < 5, \"Good night\", df(H) < 12, \"Good morning\", df(H) < 18, \"Good afternoon\", " +
                    "\"Good evening\")\$",
                size = 28f,
                weight = 300,
                color = "text",
            ),
        ),
    )

    /** Words the person types themselves — the one block whose content is theirs rather than read from the device. */
    private fun label() = block(
        name = "Label",
        globals = listOf(
            WidgetGlobal.Text("label", "Text", "Your text"),
            text(),
            font(),
            WidgetGlobal.Number("size", "Size", value = 24f, min = 12f, max = 72f),
        ),
        layers = listOf(
            WidgetLayerSpec(
                WidgetSource.Text(
                    "\$gv(label)\$",
                    size = 24f,
                    colorGlobal = "text",
                    sizeGlobal = "size",
                    fontGlobal = "font",
                ),
            ),
        ),
    )

    private fun batteryRing() = block(
        name = "Battery ring",
        globals = listOf(accent(Mint), text()),
        layers = listOf(
            WidgetLayerSpec(
                batteryDial(ring = 7f, textSize = 16f),
                width = WidgetExtent.Dp(value = 76f),
                height = WidgetExtent.Dp(value = 76f),
            ),
        ),
    )

    /**
     * A small label, a figure under it and a bar under that, laid out against the block's own top-left corner — the
     * shape of the progress templates, at a fixed width since a block has no widget width to share.
     */
    @Suppress("LongParameterList") // What the bar measures, and how it is labelled and colored.
    private fun bar(name: String, label: String, figure: String, value: String, color: Int, max: Float = 100f) = block(
        name = name,
        globals = listOf(text(), accent(color)),
        layers = listOf(
            WidgetLayerSpec(
                WidgetSource.Text(label, size = 12f, weight = 700, color = Muted),
                anchor = WidgetAnchor.TOP_LEFT,
            ),
            WidgetLayerSpec(
                WidgetSource.Text(figure, size = 34f, weight = 300, colorGlobal = "text"),
                anchor = WidgetAnchor.TOP_LEFT,
                offsetX = -1f,
                offsetY = 16f,
            ),
            WidgetLayerSpec(
                WidgetSource.Progress(value, max = max, trackColor = Track, colorGlobal = "accent"),
                anchor = WidgetAnchor.TOP_LEFT,
                offsetY = 64f,
                width = WidgetExtent.Dp(value = 150f),
                height = WidgetExtent.Dp(value = 8f),
            ),
        ),
    )

    private fun batteryBar() = bar(
        name = "Battery bar",
        label = chargingOr("Battery"),
        figure = "\$bi(level)\$%",
        value = "\$bi(level)\$",
        color = Mint,
    )

    private fun dayBar() = bar(
        name = "Day bar",
        label = "TODAY",
        figure = "\$mu(floor, (df(H) * 60 + df(m)) / 14.4)\$%",
        value = "\$df(H) * 60 + df(m)\$",
        color = Gold,
        max = 1440f,
    )

    private fun yearBar() = bar(
        name = "Year bar",
        label = "\$df(yyyy)\$",
        figure = "\$mu(floor, df(D) / 3.65)\$%",
        value = "\$df(D)\$",
        color = Coral,
        max = 365f,
    )

    private fun hourRing() = block(
        name = "Hour ring",
        globals = listOf(accent(Mint), text(), font(), h24()),
        layers = listOf(
            WidgetLayerSpec(
                WidgetSource.Progress(
                    "\$df(m)\$",
                    WidgetSource.Progress.Kind.ARC,
                    max = 60f,
                    thickness = 8f,
                    trackColor = Track,
                    colorGlobal = "accent",
                ),
                width = WidgetExtent.Dp(value = 120f),
                height = WidgetExtent.Dp(value = 120f),
            ),
            line(Time, size = 26f, weight = 300, color = "text", y = -6f),
            line("\$tc(up, df(\"EEE d\"))\$", size = 11f, weight = 600, y = 18f),
        ),
    )
}
