package inkspire.morphic.core.icon.parse

import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.core.model.ComponentKey

/**
 * One answer to "what are this app's icon layers?" — load the raw drawable, split it into a [ParsedIcon].
 *
 * It exists because **two renderers ask the same question**: the baked path composites a bitmap for display, and
 * the editor renders the layers live so a slider responds per frame. Both start here, so neither can disagree with
 * the other about what an app's foreground or background *is* — the same reason both go on to share
 * `IconLayerResolver` and `LayerTransform`.
 *
 * ## Deliberately not cached
 *
 * A cache here looks obviously worthwhile and would be a bug. A [ParsedIcon] holds `Drawable`s, and a `Drawable` is
 * **mutable, shared state**: the compositor calls `setBounds` on it before drawing. Handing one instance to two
 * bakes running on the bake dispatcher at once — which is exactly what a screenful of icons does — would let them
 * scribble over each other's bounds, producing icons that are intermittently the wrong size. Today every call gets
 * its own drawable from the platform, and that isolation is what makes concurrent baking safe at all.
 *
 * The cache that matters is the one on the *output* (`IconRenderManager`'s baked bitmaps, which are immutable), and
 * it already coalesces concurrent requests for the same icon — so the parse it would save is a parse that mostly
 * does not happen twice anyway.
 *
 * ## Blocking, on purpose
 *
 * [load] does real work (a package-manager lookup and drawable inflation) and **does not hop threads**, matching
 * [RawIconSource.loadIcon] beneath it. That is what lets `IconRenderManager` run it inside its own bounded bake
 * dispatcher: a loader that hopped to `Dispatchers.Default` for itself would escape that cap and put the load and
 * parse of every icon on screen back onto every core, which is the thing the cap was added to stop. Callers outside
 * that path move it off the main thread themselves.
 */
class ParsedIconLoader(
    private val rawIconSource: RawIconSource,
    private val parser: DrawableParser,
) {

    /**
     * The parsed layers of [component]'s icon, or `null` when it no longer resolves (uninstalled, or a profile that
     * is locked). **Blocking — call it off the main thread.**
     *
     * @param densityDpi the density to load at; `0` means the device's own.
     */
    fun load(component: ComponentKey, densityDpi: Int = 0): ParsedIcon? =
    // The package is passed for the size diagnostics only — never read for a decision, which is why the parser
    // takes it as a nullable label rather than a `ComponentKey`. Without it every measurement line in the log
        // would be anonymous, and finding the one app that renders wrong is the whole exercise.
        rawIconSource.loadIcon(component, densityDpi)?.let { parser.parse(it, component.packageName) }
}
