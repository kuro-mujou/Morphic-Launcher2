package inkspire.morphic.data.widgets

import inkspire.morphic.core.model.widget.WidgetAnchor
import inkspire.morphic.core.model.widget.WidgetExtent
import inkspire.morphic.core.model.widget.WidgetGlobal
import inkspire.morphic.core.model.widget.WidgetLayerSpec
import inkspire.morphic.core.model.widget.WidgetRecipe
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.model.widget.WidgetSpan
import inkspire.morphic.data.widgets.TemplateParts.Coral
import inkspire.morphic.data.widgets.TemplateParts.Gold
import inkspire.morphic.data.widgets.TemplateParts.Mint
import inkspire.morphic.data.widgets.TemplateParts.Muted
import inkspire.morphic.data.widgets.TemplateParts.Sky
import inkspire.morphic.data.widgets.TemplateParts.Time
import inkspire.morphic.data.widgets.TemplateParts.Track
import inkspire.morphic.data.widgets.TemplateParts.accent
import inkspire.morphic.data.widgets.TemplateParts.bar
import inkspire.morphic.data.widgets.TemplateParts.barLabel
import inkspire.morphic.data.widgets.TemplateParts.batteryDial
import inkspire.morphic.data.widgets.TemplateParts.bigFigure
import inkspire.morphic.data.widgets.TemplateParts.chargingOr
import inkspire.morphic.data.widgets.TemplateParts.font
import inkspire.morphic.data.widgets.TemplateParts.h24
import inkspire.morphic.data.widgets.TemplateParts.panel
import inkspire.morphic.data.widgets.TemplateParts.panelGlobals
import inkspire.morphic.data.widgets.TemplateParts.text

/**
 * The template library: the finished designs the picker offers, each placed as a copy the user then restyles through
 * the globals it declares. This is the tier-1 product — someone who never opens an editor gets their widget from here.
 *
 * **Every design is laid out for the cell it lands in on a phone** — a visual cell is about 103 × 154 dp at the
 * default 4 × 5 grid — and re-lays rather than scales when resized, so each is anchored to the edges its parts belong
 * to. Judged by eye through `TemplateGalleryHarness` in `feature:home`, not by any test here; `WidgetCadenceTest`
 * guards only that none of them wakes every second.
 *
 * A clock's pattern is a constant in each branch rather than one built from a global: a pattern chosen at run time
 * cannot be read for its tick, and would wake the widget every second instead of every minute.
 */
object BuiltInWidgetTemplates {

    val all: List<WidgetTemplate> = listOf(
        clock(), stackedClock(), hourRing(), clockAndBattery(),
        date(), calendarPage(), weekday(), greeting(),
        batteryRing(), battery(), dayProgress(), yearProgress(),
    )

    private fun clock() = WidgetTemplate(
        id = "clock",
        name = "Clock",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = listOf(text(), accent(Sky), font(), h24(), WidgetGlobal.Switch("showDate", "Show date", true)) +
                panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(Time, size = 64f, weight = 200, colorGlobal = "text", fontGlobal = "font"),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 24f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$df(EEEE)\$",
                        size = 15f,
                        weight = 600,
                        colorGlobal = "accent",
                        fontGlobal = "font",
                    ),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -24f,
                    offsetY = -11f,
                    visibleGlobal = "showDate",
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(\"d MMMM\")\$", size = 15f, color = Muted, fontGlobal = "font"),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -24f,
                    offsetY = 11f,
                    visibleGlobal = "showDate",
                ),
            ),
        ),
    )

    private fun stackedClock() = WidgetTemplate(
        id = "stacked_clock",
        name = "Stacked clock",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = listOf(text(), accent(Coral), font()) + h24() + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$if(gv(h24), df(HH), df(hh))\$",
                        size = 100f,
                        weight = 700,
                        colorGlobal = "text",
                        fontGlobal = "font",
                    ),
                    offsetY = -54f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(mm)\$", size = 100f, weight = 700, colorGlobal = "accent", fontGlobal = "font"),
                    offsetY = 54f,
                ),
            ),
        ),
    )

    private fun hourRing() = WidgetTemplate(
        id = "hour_ring",
        name = "Hour ring",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = listOf(text(), accent(Mint), font()) + h24() + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    // The minutes gone in this hour, going round like a watch's minute hand.
                    WidgetSource.Progress(
                        "\$df(m)\$",
                        WidgetSource.Progress.Kind.ARC,
                        max = 60f,
                        thickness = 10f,
                        trackColor = Track,
                        colorGlobal = "accent",
                    ),
                    // A share of the width, so a narrower cell shrinks the ring rather than clipping it; an arc draws
                    // the largest circle its box holds.
                    width = WidgetExtent.Fraction(value = 0.8f),
                    height = WidgetExtent.Fill,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text(Time, size = 40f, weight = 300, colorGlobal = "text", fontGlobal = "font"),
                    offsetY = -6f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$tc(up, df(\"EEE d\"))\$",
                        size = 12f,
                        weight = 600,
                        color = Muted,
                        fontGlobal = "font",
                    ),
                    offsetY = 28f,
                ),
            ),
        ),
    )

    private fun clockAndBattery() = WidgetTemplate(
        id = "clock_battery",
        name = "Clock & battery",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = listOf(text(), accent(Mint), font()) + h24() + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(Time, size = 60f, weight = 300, colorGlobal = "text", fontGlobal = "font"),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 24f,
                ),
                WidgetLayerSpec(
                    batteryDial(ring = 8f, textSize = 17f),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -24f,
                    width = WidgetExtent.Dp(value = 84f),
                    height = WidgetExtent.Dp(value = 84f),
                ),
            ),
        ),
    )

    private fun date() = WidgetTemplate(
        id = "date",
        name = "Date",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = listOf(accent(Coral), text(), font()) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$tc(up, df(EEEE))\$",
                        size = 13f,
                        weight = 700,
                        colorGlobal = "accent",
                        fontGlobal = "font",
                    ),
                    anchor = WidgetAnchor.TOP,
                    offsetY = 16f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(d)\$", size = 54f, weight = 300, colorGlobal = "text", fontGlobal = "font"),
                    offsetY = 2f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(MMMM)\$", size = 13f, color = Muted, fontGlobal = "font"),
                    anchor = WidgetAnchor.BOTTOM,
                    offsetY = -14f,
                ),
            ),
        ),
    )

    private fun calendarPage() = WidgetTemplate(
        id = "calendar_page",
        name = "Calendar page",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = listOf(accent(Coral), text(), font(WidgetSource.Text.Font.SERIF)) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$tc(up, df(MMMM))\$",
                        size = 15f,
                        weight = 700,
                        colorGlobal = "accent",
                        fontGlobal = "font",
                    ),
                    anchor = WidgetAnchor.TOP,
                    offsetY = 32f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(d)\$", size = 120f, weight = 300, colorGlobal = "text", fontGlobal = "font"),
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(EEEE)\$", size = 17f, color = Muted, fontGlobal = "font"),
                    anchor = WidgetAnchor.BOTTOM,
                    offsetY = -32f,
                ),
            ),
        ),
    )

    private fun weekday() = WidgetTemplate(
        id = "weekday",
        name = "Weekday",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = listOf(text(), font(WidgetSource.Text.Font.SERIF)) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(EEEE)\$", size = 46f, colorGlobal = "text", fontGlobal = "font"),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 24f,
                    offsetY = -12f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("\$df(\"d MMMM yyyy\")\$", size = 15f, color = Muted, fontGlobal = "font"),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 26f,
                    offsetY = 28f,
                ),
            ),
        ),
    )

    private fun greeting() = WidgetTemplate(
        id = "greeting",
        name = "Greeting",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = listOf(text(), font()) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$if(df(H) < 5, \"Good night\", df(H) < 12, \"Good morning\", df(H) < 18, \"Good afternoon\", " +
                            "\"Good evening\")\$",
                        size = 32f,
                        weight = 300,
                        colorGlobal = "text",
                        fontGlobal = "font",
                    ),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 24f,
                    offsetY = -13f,
                ),
                WidgetLayerSpec(
                    WidgetSource.Text("It's \$df(\"EEEE, d MMMM\")\$", size = 15f, color = Muted, fontGlobal = "font"),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 25f,
                    offsetY = 22f,
                ),
            ),
        ),
    )

    private fun batteryRing() = WidgetTemplate(
        id = "battery_ring",
        name = "Battery ring",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 1, rows = 1),
            globals = listOf(accent(Mint), WidgetGlobal.Switch("status", "Show status", true)) + panelGlobals(),
            layers = listOf(
                panel(),
                WidgetLayerSpec(
                    batteryDial(ring = 7f, textSize = 18f),
                    offsetY = -10f,
                    width = WidgetExtent.Fraction(value = 0.74f),
                    height = WidgetExtent.Dp(value = 76f),
                ),
                WidgetLayerSpec(
                    WidgetSource.Text(chargingOr("Battery"), size = 11f, weight = 600, color = Muted),
                    anchor = WidgetAnchor.BOTTOM,
                    offsetY = -16f,
                    visibleGlobal = "status",
                ),
            ),
        ),
    )

    private fun battery() = WidgetTemplate(
        id = "battery",
        name = "Battery",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = listOf(text(), accent(Mint), WidgetGlobal.Switch("status", "Show status", true)) + panelGlobals(),
            layers = listOf(
                panel(),
                barLabel(chargingOr("Battery"), visibleGlobal = "status"),
                bigFigure("\$bi(level)\$%"),
                bar(WidgetSource.Progress("\$bi(level)\$", trackColor = Track, colorGlobal = "accent")),
            ),
        ),
    )

    private fun dayProgress() = WidgetTemplate(
        id = "day_progress",
        name = "Day",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = listOf(text(), accent(Gold)) + panelGlobals(),
            layers = listOf(
                panel(),
                barLabel("TODAY"),
                WidgetLayerSpec(
                    WidgetSource.Text(
                        "\$mu(floor, (1440 - df(H) * 60 - df(m)) / 60)\$h \$(1440 - df(H) * 60 - df(m)) % 60\$m left",
                        size = 12f,
                        weight = 600,
                        color = Muted,
                    ),
                    anchor = WidgetAnchor.TOP_RIGHT,
                    offsetX = -24f,
                    offsetY = 22f,
                ),
                bigFigure("\$mu(floor, (df(H) * 60 + df(m)) / 14.4)\$%"),
                bar(WidgetSource.Progress("\$df(H) * 60 + df(m)\$", max = 1440f, trackColor = Track, colorGlobal = "accent")),
            ),
        ),
    )

    private fun yearProgress() = WidgetTemplate(
        id = "year_progress",
        name = "Year",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = listOf(text(), accent(Coral)) + panelGlobals(),
            layers = listOf(
                panel(),
                // A leap year's last day reads 100% a day early, which a whole-percent display cannot show anyway.
                barLabel("\$df(yyyy)\$"),
                bigFigure("\$mu(floor, df(D) / 3.65)\$%"),
                bar(WidgetSource.Progress("\$df(D)\$", max = 365f, trackColor = Track, colorGlobal = "accent")),
            ),
        ),
    )
}
