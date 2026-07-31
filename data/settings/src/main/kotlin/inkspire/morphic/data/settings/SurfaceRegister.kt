package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.SurfaceTransition
import kotlinx.serialization.Serializable

/**
 * What sits behind one edge of HOME — the surface, **plus the configuration that only that surface has**.
 *
 * **A sealed type rather than a `Surface` value, so illegal bindings cannot be written down.** Two things were wrong
 * with storing the plain enum: `Surface.HOME` is the centre and can never be a side (`LauncherShell` had to answer
 * that with a runtime `error()`), and the APPS surface needs an [AppsLayout] that no other surface would want. One
 * variant per bindable surface answers both — the layout lives on the binding that needs it, and a HOME binding has
 * no variant to write.
 *
 * That the sealed hierarchy has a single variant today is not premature: `Surface` has exactly two values and one of
 * them is the centre. It grows when a second side surface does, and each new surface brings its own config with it
 * rather than widening a shared record.
 *
 * **Why the layout is per binding, not per surface.** The same surface can be reached from different edges, and the
 * arrangement is deliberately allowed to differ — swipe left for an A–Z list, swipe up for the paged grid, over one
 * app collection. `AppsScreen` already takes `layout` as a parameter for exactly this reason.
 */
@Serializable
sealed interface SideBinding {

    /** The APPS surface, rendered in [layout]. */
    @Serializable
    data class Apps(val layout: AppsLayout = AppsLayout.VERTICAL_LIST) : SideBinding
}

/**
 * **The surface register**: HOME's own layout, what is bound to each of its edges, and how a crossing is animated.
 *
 * The first settings slice, and the one the launcher is most visibly missing — with an empty [sides] no edge is
 * swipeable at all, which is the state `LauncherShell` ships in today.
 *
 * **Sparse by design.** An edge with no entry is unbound, not "bound to nothing": `SurfacePager` reads the swipeable
 * set straight off the map's keys, so absence is the representation rather than a null to check. A fresh install
 * therefore stores an empty map, not four nulls — the same principle the per-surface metric overrides will use, where
 * absence means "the blueprint still decides".
 *
 * Every field is defaulted and the JSON reader ignores unknown keys, which together make additive evolution safe: an
 * older build reading a newer blob drops what it doesn't know, and a newer build reading an older one fills the gaps
 * from these defaults. There is deliberately **no `version` field** — for a blob store the honest migration seam is
 * the *key name*, since a renamed key lets the old and new formats coexist while a version int inside one key cannot.
 *
 * @property homeLayout HOME's main-area + side-zone pairing. Not yet read by anything — `HomeScreen` is
 *   `PAGER_WITH_DOCK` by construction — but it belongs to this slice rather than a later one, because "what is HOME"
 *   and "what is off its edges" is one question the user answers on one screen.
 * @property sides the binding for each swipeable edge; absent edge = not swipeable.
 * @property transition how HOME and a side surface animate past each other. Only `SLIDE` is implemented in
 *   `SurfacePager`; the rest of the enum is stored and ignored until the transforms land.
 */
@Serializable
data class SurfaceRegister(
    val homeLayout: HomeLayout = HomeLayout.PAGER_WITH_DOCK,
    val sides: Map<HomeEdge, SideBinding> = emptyMap(),
    val transition: SurfaceTransition = SurfaceTransition.SLIDE,
) {
    companion object {
        /**
         * The out-of-the-box register: HOME on `PAGER_WITH_DOCK`, **nothing bound to any edge**.
         *
         * Empty rather than "APPS somewhere sensible" on purpose. Which edge opens the app list is a *product*
         * decision, and seeding one here would make this file the place it was decided — quietly, in the data layer,
         * where nobody looks for product decisions. It belongs to whatever onboarding or default-preset step chooses
         * it, and until then the settings screen is how an edge gets bound.
         */
        val Default = SurfaceRegister()
    }
}
