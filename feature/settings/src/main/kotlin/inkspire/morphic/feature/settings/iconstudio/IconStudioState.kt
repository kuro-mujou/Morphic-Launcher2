package inkspire.morphic.feature.settings.iconstudio

import android.graphics.Bitmap
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconLayerSet
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.PreviewBackground
import inkspire.morphic.data.icons.InstalledIconPack
import inkspire.morphic.data.settings.IconPreset

/**
 * Which recipe the studio is editing, resolved from [IconStudioRoute] once the app (if any) has been parsed.
 *
 * Separate from the route because the route is a *string* — a `NavKey` has to be serializable — while everything
 * downstream wants a [ComponentKey]. Resolving once, in the ViewModel, is what stops every consumer re-parsing it.
 */
sealed interface StudioSubject {

    /**
     * Editing the global default.
     *
     * @property sample the app the preview borrows artwork from; null before apps load. **Whichever app it is changes
     *   what the edit looks like** — a legacy icon with a flat plate and an adaptive one with a transparent foreground
     *   answer the same layer differently — so it is re-rollable rather than fixed at open;
     *   `IconStudioViewModel.shuffleSample` is the whole of that.
     */
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
 * Browsing one pack's drawables to choose a specific icon for this app.
 *
 * **Individual mode only, and that is a property of the model rather than a scoping decision.** A named drawable
 * on the *global* default would be inherited by every app, giving all of them the same picture — so there is
 * nothing sensible for this to mean there, and the studio does not offer it.
 *
 * @property names every drawable the pack maps to some app, which is what the browser lists. Not filtered here;
 *   the search field is the UI's.
 */
data class PackBrowse(
    val packPackage: String,
    val names: List<String> = emptyList(),
)

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
 * @property images every custom-image layer's artwork, by its path — **including images not yet written to disk**.
 *   A freshly picked image is previewed from here before any file exists, which is what lets an abandoned edit
 *   leave nothing behind; see `CustomIconStore`.
 * @property packs the installed icon packs, for the chooser. Empty is the ordinary state on a device with none.
 * @property packImages this app as drawn by each pack the recipe names, keyed by package **and chosen drawable**,
 *   since two layers may name the same pack and different drawables. Resolved off the main thread for the same
 *   reason [images] is: the first lookup into a pack parses an `appfilter.xml` of thousands of entries, and a
 *   layer whose pack does not cover this app is simply absent here.
 * @property browsing the pack whose drawables are being browsed, or null. Null in the global studio always — see
 *   [PackBrowse].
 * @property background what the canvas is drawn on. **Unlike [editing], this one *is* a projection of the store** —
 *   it is persisted, so the studio reopens on the backdrop the user left it on. It can be, because nothing edits it
 *   continuously: a cycle is one discrete tap, so there is no drag for an emission to overwrite. Its default here is
 *   [PreviewBackground.Default], the same value the settings slice falls back to, so the frame before storage answers
 *   shows what storage would have said.
 */
data class IconStudioState(
    val subject: StudioSubject = StudioSubject.Unchosen,
    val editing: IconLayerSet = IconLayerSet.Base,
    val parsed: ParsedIcon? = null,
    val label: String? = null,
    val background: PreviewBackground = PreviewBackground.Default,
    val selected: Int = 0,
    val layerKeys: List<Long> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
    val pickable: List<AppInfo> = emptyList(),
    val images: Map<String, Bitmap> = emptyMap(),
    val packs: List<InstalledIconPack> = emptyList(),
    val packImages: Map<String, Bitmap> = emptyMap(),
    val browsing: PackBrowse? = null,
    val presets: List<IconPreset> = emptyList(),
) {

    /** The layer the controls act on, or null before anything has loaded. */
    val selectedLayer: IconLayerSpec? get() = editing.layers.getOrNull(selected)

    /**
     * A stable identity for the layer at [index], for the row list to `key` on.
     *
     * **Why the model cannot supply this.** Animating one stack into another means Compose has to recognise a layer
     * across the change, and nothing in [IconLayerSpec] can say so: two identical custom layers are `equals`, the
     * index is exactly what an insert moves, and the instance is replaced on every frame of a slider drag. An id on
     * the spec itself would be worse than any of those — it is persisted data, and it would join `equals`, so `dirty`
     * and the history dedupe (both whole-set comparisons) would start reporting changes where the recipe is identical.
     *
     * So the keys live here, beside the set rather than inside it, and `IconStudioViewModel` maintains them through
     * every mutation. Falls back to the index when they are absent — the state before anything has loaded — which
     * degrades to unanimated rows rather than to a crash.
     */
    fun layerKey(index: Int): Long = layerKeys.getOrNull(index) ?: -(index.toLong() + 1)

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
