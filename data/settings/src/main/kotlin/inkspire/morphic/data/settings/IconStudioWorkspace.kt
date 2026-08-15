package inkspire.morphic.data.settings

import kotlinx.serialization.Serializable

/**
 * Which way the icon studio's layer rail runs.
 *
 * **An enum rather than an `isRow` boolean**, on `ContentAnchor`'s and `SideZoneEdge`'s grounds: a stack is always laid
 * out *some* way, so both values are real and neither is the absence of the other. The name a reader wants at the call
 * site is the arrangement, not the negation of the default.
 *
 * Stored, so the value names are the on-disk contract — renaming one silently resets the rail to [VERTICAL], which is
 * the same exposure every other plain enum in a slice has and the same recoverable failure.
 */
enum class LayerRailAxis {

    /** A column down the side of the canvas: the resting arrangement, and the one that matches how layers stack. */
    VERTICAL,

    /** A row across it, for a user who would rather the rail took width than height. */
    HORIZONTAL,
    ;

    /** The other one — what the stack menu's toggle switches to. */
    val flipped: LayerRailAxis get() = if (this == VERTICAL) HORIZONTAL else VERTICAL
}

/**
 * **Where the icon studio's user left their workspace** — how the icon preview is panned and zoomed, and where the
 * layer rail was dragged to.
 *
 * The sixth slice, and the second that is about the *studio* rather than about the launcher — `iconStudioBackground`
 * was the first, and this is its exact argument repeated. It shapes no surface and reaches no rendered icon: it is
 * where the paper is lying on the desk, not the drawing. Which is precisely why it is **not** part of `IconLayerSet`
 * — a recipe carrying the pan someone happened to be using while they made it would make an icon's identity depend on
 * the view it was drawn in, and every icon on the device would re-bake because the canvas was nudged.
 *
 * It is a preference by the only test that matters: the user arranged it, and would be annoyed to arrange it again.
 *
 * **Its own key rather than a field beside the backdrop**, for the reason that slice's own KDoc gives about rates of
 * change. A backdrop is cycled a handful of times a session; this is written at the end of every pan and every pinch,
 * so sharing a blob would mean each drag rewrote the backdrop with it. Same call, one slice apart.
 *
 * ### Everything here is a fraction, and that is the load-bearing decision
 *
 * [panX] / [panY] and [railX] / [railY] are fractions of the canvas's **width and height**, never dp and never pixels
 * — the same rule `IconBoundShift` and the studio's other placement constants are stated in, one step further on:
 * these values are *persisted*, so a dp offset saved on a phone would put the rail off the edge of a tablet, and a
 * pixel one would move with the density. A fraction restores to the same place on the screen it was arranged on and
 * to the analogous place on any other.
 *
 * It is also what makes [sanitized] possible without knowing anything about the screen: every bound here is in
 * fraction space, so the guard is pure arithmetic over floats.
 *
 * @property panX how far the icon's bound has been dragged from its resting place, as a fraction of canvas width.
 * @property panY the same, of canvas height.
 * @property zoom what the bound's side is multiplied by. 1 is the resting size; the studio clamps the live gesture to
 *   its own range, and [sanitized] is only the guard against a stored value that is not a number at all.
 * @property railX how far the layer rail has been dragged from its resting place, as a fraction of canvas width.
 * @property railY the same, of canvas height.
 * @property railAxis which way the rail runs. See [LayerRailAxis].
 * @property railCollapsed whether the rail's list of layers is cut down to a single tile's worth of viewport. It is
 *   the *window* that shrinks, not the list: every layer is still there and still reachable by scrolling, which is
 *   what keeps a collapsed rail a working one rather than a state you have to leave before you can do anything.
 *   **A boolean where [railAxis] is an enum**, and the asymmetry is the rule rather than an oversight: expanded is
 *   what a stack *is*, and collapsing is a deliberate reduction of it — an "off" with a real default, like a layer's
 *   `visible`. An axis has no such default; it is one of two arrangements either way.
 */
@Serializable
data class IconStudioWorkspace(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val railX: Float = 0f,
    val railY: Float = 0f,
    val railAxis: LayerRailAxis = LayerRailAxis.VERTICAL,
    val railCollapsed: Boolean = false,
) {

    /**
     * This workspace with any value that is not a finite number replaced by its default.
     *
     * **A storage guard, not a layout rule** — which is the whole of why the *bounds* are not enforced here. Where the
     * icon may be dragged to depends on how big it is drawn and what chrome is in the way, and those are the studio's
     * questions; answering them a second time here would be two clamps that could disagree, which is the drift this
     * codebase keeps un-making. What this catches is the one failure the studio cannot recover from on its own: a
     * `NaN` reaching a `Modifier.offset` does not throw, it silently places the node nowhere, so the icon would simply
     * not be on screen and nothing would say why. A blob is JSON, so a non-finite float is reachable — from a hand
     * edit, from a truncated write, or from a later build that stored something this one does not expect.
     *
     * Zero and one are the resting arrangement, so a spoiled value degrades to "as the studio opens" rather than to a
     * crash — the position `IconOverrideRepository` takes on an unreadable recipe, one slice over.
     *
     * **`copy`, never the constructor**, which is the same trap `IconLayerSet` records about rebuilding a stack
     * positionally: this guards the numeric fields, so naming them all in a constructor call would silently reset
     * every field it did *not* name — and the fields it does not name are exactly the ones added later. It was written
     * that way and [railAxis] and [railCollapsed] were the two that would have vanished on the very next read, with no
     * error and nothing to see but a rail that would not stay where it was put.
     */
    fun sanitized(): IconStudioWorkspace = copy(
        panX = panX.orDefault(0f),
        panY = panY.orDefault(0f),
        zoom = zoom.orDefault(1f),
        railX = railX.orDefault(0f),
        railY = railY.orDefault(0f),
    )

    /**
     * Whether the **preview** is where the studio would have put it — nothing panned, nothing zoomed.
     *
     * What the studio's reset-preview button is disabled by, so the control doubles as the answer to "have I moved
     * this?" — which is `SliderControl`'s own rule for its reset, applied to the one thing on this screen that can be
     * dragged out of place without any slider saying so. A canvas panned by two pixels looks like a canvas that is not
     * panned, and that is exactly when a lit button is worth having.
     *
     * **It ignores the rail**, for the reason [withPreviewReset] gives: the two are arranged separately and undoing
     * one is not undoing the other.
     */
    val previewAtRest: Boolean
        get() = panX == 0f && panY == 0f && zoom == 1f

    /**
     * This workspace with the preview back where it started, **and the rail left exactly where it is**.
     *
     * The split is the honest one rather than the tidy one: a user pans the icon to look at a corner of it and drags
     * the rail because it is covering something, and those are two different annoyances fixed at two different
     * moments. A reset that put the rail back too would undo a piece of arrangement the user never asked about — and
     * the rail needs no reset of its own, since it is clamped to the canvas and so can always be dragged back by hand.
     */
    fun withPreviewReset(): IconStudioWorkspace = copy(panX = 0f, panY = 0f, zoom = 1f)

    companion object {

        /** Nothing arranged: the icon rests where the studio puts it, at its own size, with the rail on its edge. */
        val Default = IconStudioWorkspace()
    }
}

/** [fallback] unless this is a real number — `isFinite` covers `NaN` and both infinities in one test. */
private fun Float.orDefault(fallback: Float): Float = if (isFinite()) this else fallback
