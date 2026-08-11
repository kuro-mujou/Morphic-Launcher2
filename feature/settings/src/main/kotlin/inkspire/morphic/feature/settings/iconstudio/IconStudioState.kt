package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole

/**
 * What the canvas is drawn *on*, cycled by a single control.
 *
 * A drawing app's transparency checkerboard, plus flat black and white, plus the two mixes — because the question an
 * icon designer actually has is "does this read on a dark background, on a light one, and where exactly are its
 * transparent parts?", and no single backdrop answers all three.
 *
 * **L1's sixth option, the launcher's own wallpaper, is deliberately absent.** The studio never shows it. That is
 * partly a design call and partly load-bearing: Haze blurs whatever node is really beneath a floating surface, and
 * the wallpaper reaches the settings previews through a `BlendMode.Src` punch to a *transparent* window — which
 * would leave the studio's panels with nothing to sample.
 */
enum class PreviewBackground {
    BLACK,
    WHITE,

    /** The checkerboard everywhere — transparency shown across the whole canvas. */
    CHECKERBOARD,

    /** Black outside the icon's bound, checkerboard within it: the icon's own alpha against a dark surround. */
    BLACK_WITH_CHECKER,

    /** White outside the icon's bound, checkerboard within it. */
    WHITE_WITH_CHECKER,
    ;

    /** The next background in the cycle, wrapping — the whole of the control's behaviour. */
    fun next(): PreviewBackground = entries[(ordinal + 1) % entries.size]

    /** Whether the area *inside* the icon bound shows the transparency checkerboard. */
    val checkersInsideBound: Boolean
        get() = this == CHECKERBOARD || this == BLACK_WITH_CHECKER || this == WHITE_WITH_CHECKER

    /** Whether the area *outside* the icon bound does. */
    val checkersOutsideBound: Boolean get() = this == CHECKERBOARD
}

/**
 * Which recipe the studio is editing, resolved from [IconStudioRoute] once the app (if any) has been parsed.
 *
 * Separate from the route because the route is a *string* — a `NavKey` has to be serializable — while everything
 * downstream wants a [ComponentKey]. Resolving once, in the ViewModel, is what stops every consumer re-parsing it.
 */
sealed interface StudioSubject {

    /** Editing the global default. [sample] is the app the preview borrows artwork from; null before apps load. */
    data class Global(val sample: ComponentKey? = null) : StudioSubject

    /** Editing one app's own recipe. */
    data class App(val component: ComponentKey) : StudioSubject

    /** Individual mode with nothing chosen: the studio shows its picker. */
    data object Unchosen : StudioSubject

    /** The app whose artwork the preview draws, or null when there is nothing to draw yet. */
    val previewComponent: ComponentKey?
        get() = when (this) {
            is Global -> sample
            is App -> component
            Unchosen -> null
        }
}

/**
 * Everything the icon studio shows.
 *
 * **[editing] is the screen's, not the store's**, and that is the central fact about this screen rather than an
 * implementation detail. A live editor's set diverges from what is persisted the instant a slider moves; if this
 * were a projection of the repository flow, every frame of a drag would either have to be written or be discarded by
 * the next emission. So the stored value seeds it once and the screen owns it from then on — which is the same
 * full-snapshot detach the persistence layer uses, showing up one layer higher.
 *
 * @property parsed the subject's parsed layers, for the live render. Null while loading, or when the app has gone.
 * @property label what the chrome calls the subject — an app's name, or nothing for the global default.
 */
data class IconStudioState(
    val subject: StudioSubject = StudioSubject.Unchosen,
    val editing: IconLayerSet = IconLayerSet.Base,
    val parsed: ParsedIcon? = null,
    val label: String? = null,
    val background: PreviewBackground = PreviewBackground.BLACK_WITH_CHECKER,
    val selected: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
    val pickable: List<AppInfo> = emptyList(),
) {

    /** The layer the controls act on, or null before anything has loaded. */
    val selectedLayer: IconLayerSpec? get() = editing.layers.getOrNull(selected)

    /** Whether the selected layer can be deleted — the foreground and background are permanent. */
    val canRemoveSelected: Boolean get() = selectedLayer?.role == LayerRole.CUSTOM

    /**
     * Whether the selected layer can move up / down the stack.
     *
     * Asked of the model rather than re-derived, so the buttons are disabled by exactly the rule the set enforces:
     * `moveUp`/`moveDown` return the set unchanged when a move would put the foreground under its background. That
     * is also why the reorder is buttons rather than a drag — a **disabled button says which move is illegal before
     * it is attempted**, where a refused drag is an interaction that silently does nothing and cannot explain
     * itself. (L1 locked buttons for this reason, then reversed itself to a drag list in a later plan; this takes
     * the first answer.)
     */
    val canMoveUp: Boolean get() = editing.moveUp(selected) !== editing

    /** @see canMoveUp */
    val canMoveDown: Boolean get() = editing.moveDown(selected) !== editing
}
