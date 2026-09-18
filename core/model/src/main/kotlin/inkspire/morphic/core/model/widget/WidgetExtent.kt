package inkspire.morphic.core.model.widget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How big a layer is along one axis. Width and height are chosen independently, so a bar can be half the widget wide
 * and 4dp tall.
 *
 * The three are what "re-lay, don't scale" needs: [Dp] keeps its size when the widget is resized, [Fraction] follows
 * the parent, and [Content] is as big as what it draws. The [SerialName]s are the stored contract.
 */
@Serializable
sealed interface WidgetExtent {

    /** As big as the content — a text's measured line, an image's own aspect. */
    @Serializable
    @SerialName("content")
    data object Content : WidgetExtent

    /** A fixed size in dp, unchanged by the widget's size. */
    @Serializable
    @SerialName("dp")
    data class Dp(val value: Float) : WidgetExtent

    /** A share of the parent along this axis: 1 fills it. */
    @Serializable
    @SerialName("fraction")
    data class Fraction(val value: Float) : WidgetExtent

    companion object {
        val Fill: WidgetExtent = Fraction(1f)
    }
}
