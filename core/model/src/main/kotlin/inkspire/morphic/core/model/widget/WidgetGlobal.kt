package inkspire.morphic.core.model.widget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A setting a design exposes for someone else to change — "Text color", "Show date", "Font" — and the whole of what the
 * Style tab shows. A design is restyled through its globals and nothing else, so its author decides what can change
 * and the person placing it never meets a layer.
 *
 * A global is **read** by the properties bound to it (a layer's `colorGlobal`, `visibleGlobal`, …) and by formulas
 * through `gv(name)`; it holds its own current value, so each placed widget keeps its own.
 *
 * @property name what bindings and formulas refer to it by, unique within a recipe. Never shown.
 * @property label what the Style tab calls it.
 */
@Serializable
sealed interface WidgetGlobal {
    val name: String
    val label: String

    @Serializable
    @SerialName("color")
    data class Color(override val name: String, override val label: String, val value: Int) : WidgetGlobal

    /** @property min the Style tab's slider's range, which a bound value is not clamped to elsewhere. */
    @Serializable
    @SerialName("number")
    data class Number(
        override val name: String,
        override val label: String,
        val value: Float,
        val min: Float,
        val max: Float,
    ) : WidgetGlobal

    @Serializable
    @SerialName("switch")
    data class Switch(override val name: String, override val label: String, val value: Boolean) : WidgetGlobal

    /** One of a fixed set of [options]; `gv` reads the chosen option's text. */
    @Serializable
    @SerialName("choice")
    data class Choice(
        override val name: String,
        override val label: String,
        val options: List<String>,
        val selected: Int = 0,
    ) : WidgetGlobal

    @Serializable
    @SerialName("font")
    data class Font(override val name: String, override val label: String, val value: WidgetSource.Text.Font) : WidgetGlobal

    @Serializable
    @SerialName("text")
    data class Text(override val name: String, override val label: String, val value: String) : WidgetGlobal
}
