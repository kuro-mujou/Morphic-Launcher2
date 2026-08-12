package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The studio's sections — one per entry on the bottom bar, and the whole of what that bar is.
 *
 * **The dividing line is "opens a panel" against "acts immediately", not "is a feature".** Undo, redo, save, reset and
 * the background cycle all take effect the moment they are pressed, so they stay in the corners where they cannot move
 * under the finger when a panel changes; everything that needs a surface to work on is here. That is also why there is
 * no `UNDO` or `SAVE` value: a bar of mixed kinds would make its entries mean two different things, and the user would
 * have to know which before pressing one.
 *
 * **The order is the order the work happens in.** Pick the layer, decide what it is made of, place it, mask it, color
 * it — then the library and the leftovers. A tool rail read left to right is a reasonable first guess at a workflow, so
 * it may as well be the right one.
 *
 * **Every value except [MORE] and [PRESETS] acts on the *selected layer*, which is why [LAYERS] comes first**: the
 * stack is the subject the rest of the bar has an object of. That is the same shape L1's studio never settled — it
 * mixed per-layer tools and whole-icon tools in one flat row and left the split as an open question in its own plan.
 * Here there is nothing whole-icon left to mix in: the tile shape became a per-layer shape, the background became the
 * background layer's source, theming became a foreground source, and sizing lives in `data:settings` and another
 * screen entirely.
 */
enum class StudioTool(val label: String, val icon: ImageVector) {

    /** The stack: which layer is being edited, whether it is showing, its order, and adding or removing one. */
    LAYERS("Layers", Icons.Default.Layers),

    /**
     * Where the selected layer's pixels come from — app default, the app's monochrome artwork, a solid fill, a custom
     * image, or an icon pack (and, for a single app, a *named* drawable from one).
     *
     * One section rather than several because it is one question with mutually exclusive answers, and the model
     * already decides which of them are legal for a given `LayerRole`.
     */
    SOURCE("Source", Icons.Default.Image),

    /** Position, zoom and rotation. */
    TRANSFORM("Transform", Icons.Default.OpenWith),

    /** The layer's silhouette: unshaped, or one of the vector-backed `IconShapes`. */
    SHAPE("Shape", Icons.Default.Hexagon),

    /**
     * How the layer reads: opacity, blend mode, recoloring (hue / saturation / brightness / tint) and the gradient
     * overlay.
     *
     * **This is the one deliberate merge, and the model is what justifies it.** `LayerEffect.Color` and
     * `LayerEffect.Gradient` are two variants of one sealed list — and the deferred shadow will be a third — so an
     * entry per variant would mean the bar grew every time that list did. Opacity and blend join them despite being
     * spec *fields* rather than effects, for the reason the controls' own KDoc gives: that distinction is about what
     * can vary independently in storage, and someone adjusting how a layer reads color-wise does not care which side
     * of it a control sits on.
     */
    EFFECTS("Effects", Icons.Default.Palette),

    /**
     * The preset library: name the current recipe, load one, delete one.
     *
     * A section rather than a corner action even though saving one is immediate, because the library is a *list* and
     * has to be shown to be used. It is also the one section that does not act on the selected layer — a preset is a
     * whole recipe.
     */
    PRESETS("Presets", Icons.Default.Style),

    /**
     * Reset, and the declared home for the session-level extras that are coming (shuffling which app the global studio
     * previews on is the first — see `IconStudioViewModel.openGlobal`).
     *
     * **It exists to hold Reset one tap deep.** Reset is the only destructive verb in the studio, so it must not sit in
     * the corners beside Save — "commit this" and "throw my customisation away" a finger-width apart is a trap — and
     * it does not deserve a bar entry of its own, where it could be pressed on the way to a tool.
     */
    MORE("More", Icons.Default.MoreHoriz),
}
