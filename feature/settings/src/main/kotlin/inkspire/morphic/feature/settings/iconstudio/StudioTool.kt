package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Image
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
 * **Every value except [MORE] and [PRESETS] acts on whatever the rail has selected, and the stack itself is no longer
 * one of them.** It was `LAYERS`, first on the bar; it is now the always-visible rail down the canvas edge
 * (`StudioLayerRail`), which is where "which layer am I editing?" is answered by looking rather than by opening a
 * panel. Once the rail also reordered, hid and deleted, the entry's only remaining job was *add* — and an entry that
 * is one button is not an entry, it is a button, and it belongs where the layers are.
 *
 * L1's studio mixed per-layer tools and whole-icon tools in one flat row and left the split as an open question in
 * its own plan. **The answer here is that the scope is a selection rather than a mode**: the rail says what is being
 * edited, this bar shows what that thing can be asked (see [appliesTo]), and a tool that means something for both
 * simply means something for both. So Shape and Effects serve a layer or the whole icon with no second vocabulary,
 * while the rest of L1's whole-icon row went elsewhere entirely — the background is the background layer's source,
 * theming is a foreground source, and sizing lives in `data:settings` and another screen.
 */
enum class StudioTool(val label: String, val icon: ImageVector) {

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

    /**
     * A silhouette: unshaped, or one of the vector-backed `IconShapes` — the selected layer's, or the whole icon's.
     *
     * The two are not the same picture, which is why the composite has one of its own rather than the section being
     * a shortcut for setting the layers': a per-layer mask trims each layer *before* it joins the stack, so a bloom
     * or a blend that reaches past that layer's own silhouette escapes it. Cut against the composite, nothing does.
     */
    SHAPE("Shape", Icons.Default.Hexagon),

    /**
     * How the layer reads: opacity, blend mode, recoloring (hue / saturation / brightness / tint), the bloom overlay
     * and the built-in looks.
     *
     * **This is the one deliberate merge, and the model is what justifies it.** `LayerEffect.Color` and
     * `LayerEffect.Bloom` are two variants of one sealed list — and the deferred shadow will be another — so an
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
     * the corners beside Save — "commit this" and "throw my customization away" a finger-width apart is a trap — and
     * it does not deserve a bar entry of its own, where it could be pressed on the way to a tool.
     */
    MORE("More", Icons.Default.MoreHoriz),
    ;

    /**
     * Whether this tool means anything for [target] — which is what decides the bar's contents.
     *
     * **The bar is derived from the selection, and always was in spirit**: every entry here acts on the selected
     * thing, which is why the Layers entry was deleted when the rail took that job. Making the whole icon selectable
     * simply makes some of them inapplicable, and a bar that changes with the selection is the honest report of that
     * — a shorter bar for a target with fewer questions.
     *
     * It is also what an **open panel** is held to: a section that stops applying is closed by the screen rather than
     * left showing a header with no controls under it. One question, asked in both places, so the bar and the panel
     * cannot disagree about what the selection offers.
     *
     * [SOURCE] and [TRANSFORM] are a *layer's*: the composite has no source (it is what the layers make) and no
     * transform of its own. [EFFECTS] applies to both, which is the whole point of the composite existing, and
     * **[SHAPE] now does too** — `IconLayerSet.shape` is a real stack-level mask, and it is what makes "put every
     * icon in a squircle" one control instead of the same shape set on each layer in turn. [PRESETS] and [MORE] were
     * never per-layer — they act on the whole recipe — so the composite keeps four entries.
     */
    fun appliesTo(target: StudioTarget): Boolean = when (this) {
        SOURCE, TRANSFORM -> target is StudioTarget.Layer
        SHAPE, EFFECTS, PRESETS, MORE -> true
    }
}
