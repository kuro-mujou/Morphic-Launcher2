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
import inkspire.morphic.data.widgets.TemplateParts.block
import inkspire.morphic.data.widgets.TemplateParts.chargingOr
import inkspire.morphic.data.widgets.TemplateParts.font
import inkspire.morphic.data.widgets.TemplateParts.h24
import inkspire.morphic.data.widgets.TemplateParts.panel
import inkspire.morphic.data.widgets.TemplateParts.panelGlobals
import inkspire.morphic.data.widgets.TemplateParts.text
import inkspire.morphic.data.widgets.TemplateParts.wholeBlock

/**
 * The template library: the finished designs the picker offers, each placed as a copy the user then restyles.
 *
 * **Every design is a background plus blocks.** The background panel and its two settings belong to the widget; each
 * block — a time, a date, a battery dial — is a named group carrying its own settings, which is what the studio lets
 * someone select and restyle on its own. A block is a rigid unit placed by its own anchor, so the lines inside one sit
 * around its center, while a design that is one thing (a progress card) is a single block the size of the widget
 * whose parts are pinned to its edges.
 *
 * **Laid out for the cell it lands in on a phone** — a visual cell is about 103 × 154 dp at the default 4 × 5 grid,
 * less HOME's inset — and re-laid rather than scaled when resized. Judged by eye through `TemplateGalleryHarness` in
 * `feature:home`, not by any test here; `WidgetCadenceTest` guards only that none of them wakes every second.
 *
 * A clock's pattern is a constant in each branch rather than one built from a global: a pattern chosen at run time
 * cannot be read for its tick, and would wake the widget every second instead of every minute.
 */
object BuiltInWidgetTemplates {

    /**
     * In the order the picker shows them, which packs its shelves (eight visual cells wide on a phone) without gaps:
     * the 4 × 1s in pairs, then the 2 × 2s together.
     */
    val all: List<WidgetTemplate> = listOf(
        clock(), clockAndBattery(),
        weekday(), greeting(),
        dayProgress(), battery(), date(),
        stackedClock(), hourRing(), calendarPage(), batteryRing(),
        yearProgress(),
    )

    private fun clock() = WidgetTemplate(
        id = "clock",
        name = "Clock",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                timeBlock(size = 64f, weight = 200, anchor = WidgetAnchor.LEFT, offsetX = 24f),
                block(
                    name = "Date",
                    globals = listOf(accent(Sky), WidgetGlobal.Color("text", "Text color", Muted), font()),
                    layers = listOf(
                        line(
                            "\$df(EEEE)\$",
                            size = 15f,
                            weight = 600,
                            color = "accent",
                            anchor = WidgetAnchor.RIGHT,
                            y = -11f,
                        ),
                        line("\$df(\"d MMMM\")\$", size = 15f, color = "text", anchor = WidgetAnchor.RIGHT, y = 11f),
                    ),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -24f,
                ),
            ),
        ),
    )

    private fun clockAndBattery() = WidgetTemplate(
        id = "clock_battery",
        name = "Clock & battery",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                timeBlock(size = 60f, weight = 300, anchor = WidgetAnchor.LEFT, offsetX = 24f),
                block(
                    name = "Battery",
                    globals = listOf(accent(Mint), text()),
                    layers = listOf(
                        WidgetLayerSpec(
                            batteryDial(ring = 8f, textSize = 17f),
                            width = WidgetExtent.Dp(value = 84f),
                            height = WidgetExtent.Dp(value = 84f),
                        ),
                    ),
                    anchor = WidgetAnchor.RIGHT,
                    offsetX = -24f,
                ),
            ),
        ),
    )

    private fun weekday() = WidgetTemplate(
        id = "weekday",
        name = "Weekday",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                block(
                    name = "Date",
                    globals = listOf(text(), font(WidgetSource.Text.Font.SERIF)),
                    layers = listOf(
                        line("\$df(EEEE)\$", size = 46f, color = "text", anchor = WidgetAnchor.LEFT, y = -12f),
                        line("\$df(\"d MMMM yyyy\")\$", size = 15f, anchor = WidgetAnchor.LEFT, x = 2f, y = 28f),
                    ),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 24f,
                ),
            ),
        ),
    )

    private fun greeting() = WidgetTemplate(
        id = "greeting",
        name = "Greeting",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                block(
                    name = "Greeting",
                    globals = listOf(text(), font()),
                    layers = listOf(
                        line(
                            "\$if(df(H) < 5, \"Good night\", df(H) < 12, \"Good morning\", df(H) < 18, " +
                                "\"Good afternoon\", \"Good evening\")\$",
                            size = 32f,
                            weight = 300,
                            color = "text",
                            anchor = WidgetAnchor.LEFT,
                            y = -13f,
                        ),
                        line("It's \$df(\"EEEE, d MMMM\")\$", size = 15f, anchor = WidgetAnchor.LEFT, x = 1f, y = 22f),
                    ),
                    anchor = WidgetAnchor.LEFT,
                    offsetX = 24f,
                ),
            ),
        ),
    )

    private fun dayProgress() = WidgetTemplate(
        id = "day_progress",
        name = "Day",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 4, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                wholeBlock(
                    name = "Day",
                    globals = listOf(text(), accent(Gold)),
                    layers = listOf(
                        barLabel("TODAY"),
                        WidgetLayerSpec(
                            WidgetSource.Text(
                                "\$mu(floor, (1440 - df(H) * 60 - df(m)) / 60)\$h " +
                                    "\$(1440 - df(H) * 60 - df(m)) % 60\$m left",
                                size = 12f,
                                weight = 600,
                                color = Muted,
                            ),
                            anchor = WidgetAnchor.TOP_RIGHT,
                            offsetX = -24f,
                            offsetY = 22f,
                        ),
                        bigFigure("\$mu(floor, (df(H) * 60 + df(m)) / 14.4)\$%"),
                        bar(
                            WidgetSource.Progress(
                                "\$df(H) * 60 + df(m)\$",
                                max = 1440f,
                                trackColor = Track,
                                colorGlobal = "accent",
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun battery() = WidgetTemplate(
        id = "battery",
        name = "Battery",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                wholeBlock(
                    name = "Battery",
                    globals = listOf(text(), accent(Mint), WidgetGlobal.Switch("status", "Show status", true)),
                    layers = listOf(
                        barLabel(chargingOr("Battery"), visibleGlobal = "status"),
                        bigFigure("\$bi(level)\$%"),
                        bar(WidgetSource.Progress("\$bi(level)\$", trackColor = Track, colorGlobal = "accent")),
                    ),
                ),
            ),
        ),
    )

    private fun date() = WidgetTemplate(
        id = "date",
        name = "Date",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                block(
                    name = "Date",
                    globals = listOf(accent(Coral), text(), font()),
                    layers = listOf(
                        line("\$tc(up, df(EEEE))\$", size = 13f, weight = 700, color = "accent", y = -36f),
                        line("\$df(d)\$", size = 48f, weight = 300, color = "text", y = 2f),
                        line("\$df(MMMM)\$", size = 13f, y = 38f),
                    ),
                ),
            ),
        ),
    )

    private fun stackedClock() = WidgetTemplate(
        id = "stacked_clock",
        name = "Stacked clock",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                block(
                    name = "Time",
                    globals = listOf(text(), accent(Coral), font(), h24()),
                    layers = listOf(
                        line("\$if(gv(h24), df(HH), df(hh))\$", size = 100f, weight = 700, color = "text", y = -54f),
                        line("\$df(mm)\$", size = 100f, weight = 700, color = "accent", y = 54f),
                    ),
                ),
            ),
        ),
    )

    private fun hourRing() = WidgetTemplate(
        id = "hour_ring",
        name = "Hour ring",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                block(
                    name = "Ring",
                    globals = listOf(accent(Mint)),
                    layers = listOf(
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
                            width = WidgetExtent.Fill,
                            height = WidgetExtent.Fill,
                        ),
                    ),
                    // Shares of the widget, so a narrower cell shrinks the ring rather than clipping it. An arc draws the
                    // largest circle its box holds, and on a 2 × 2 cell these two shares make that box about square.
                    width = WidgetExtent.Fraction(value = 0.8f),
                    height = WidgetExtent.Fraction(value = 0.55f),
                ),
                block(
                    name = "Time",
                    globals = listOf(text(), font(), h24()),
                    layers = listOf(
                        line(Time, size = 30f, weight = 300, color = "text", y = -6f),
                        line("\$tc(up, df(\"EEE d\"))\$", size = 12f, weight = 600, y = 22f),
                    ),
                ),
            ),
        ),
    )

    private fun calendarPage() = WidgetTemplate(
        id = "calendar_page",
        name = "Calendar page",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 2),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                block(
                    name = "Date",
                    globals = listOf(accent(Coral), text(), font(WidgetSource.Text.Font.SERIF)),
                    layers = listOf(
                        line("\$tc(up, df(MMMM))\$", size = 15f, weight = 700, color = "accent", y = -92f),
                        line("\$df(d)\$", size = 120f, weight = 300, color = "text"),
                        line("\$df(EEEE)\$", size = 17f, y = 92f),
                    ),
                ),
            ),
        ),
    )

    private fun batteryRing() = WidgetTemplate(
        id = "battery_ring",
        name = "Battery ring",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 1, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                wholeBlock(
                    name = "Battery",
                    globals = listOf(accent(Mint), text(), WidgetGlobal.Switch("status", "Show status", true)),
                    layers = listOf(
                        WidgetLayerSpec(
                            batteryDial(ring = 6f, textSize = 15f),
                            offsetY = -10f,
                            width = WidgetExtent.Fraction(value = 0.8f),
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
            ),
        ),
    )

    private fun yearProgress() = WidgetTemplate(
        id = "year_progress",
        name = "Year",
        recipe = WidgetRecipe(
            span = WidgetSpan(cols = 2, rows = 1),
            globals = panelGlobals(),
            layers = listOf(
                panel(),
                wholeBlock(
                    name = "Year",
                    globals = listOf(text(), accent(Coral)),
                    layers = listOf(
                        // A leap year's last day reads 100% a day early, which a whole-percent display cannot show anyway.
                        barLabel("\$df(yyyy)\$"),
                        bigFigure("\$mu(floor, df(D) / 3.65)\$%"),
                        bar(WidgetSource.Progress("\$df(D)\$", max = 365f, trackColor = Track, colorGlobal = "accent")),
                    ),
                ),
            ),
        ),
    )

    /** A clock's time as a block of its own: its color, font and 24-hour switch. */
    private fun timeBlock(size: Float, weight: Int, anchor: WidgetAnchor, offsetX: Float) = block(
        name = "Time",
        globals = listOf(text(), font(), h24()),
        layers = listOf(line(Time, size = size, weight = weight, color = "text")),
        anchor = anchor,
        offsetX = offsetX,
    )

    /**
     * One line of text inside a block, in the block's font, placed around the block's center — or against the side
     * [anchor] names, which is how a block's lines share a left or right edge.
     *
     * @param color the global it takes its color from, or null for the muted second color every design uses.
     */
    @Suppress("LongParameterList") // A text's look and its place, which every line states.
    private fun line(
        text: String,
        size: Float,
        weight: Int = 400,
        color: String? = null,
        anchor: WidgetAnchor = WidgetAnchor.CENTER,
        x: Float = 0f,
        y: Float = 0f,
    ) = WidgetLayerSpec(
        WidgetSource.Text(text, size = size, weight = weight, color = Muted, colorGlobal = color, fontGlobal = "font"),
        anchor = anchor,
        offsetX = x,
        offsetY = y,
    )
}
