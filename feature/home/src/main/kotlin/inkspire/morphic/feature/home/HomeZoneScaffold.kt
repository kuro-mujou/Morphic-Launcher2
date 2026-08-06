package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.SideZoneEdge

/**
 * **HOME's two zones, stacked along the edge the side zone sits on** — the arrangement both layouts share.
 *
 * A `Column` under a strip and a `Row` beside a rail, with the side zone first or last as [SideZoneEdge.isLeading]
 * says, and the main area taking the remainder either way. That is four postures × two layouts described once;
 * before this existed the surface wrote the two-value version of it inline, and a second layout would have made it
 * the third and fourth copy. L1 had `Shell(dockLandscape, dockThickness, dockAtStart)` doing the same job with two
 * booleans, and every caller re-deriving what the pair meant.
 *
 * **Nothing about drag, folders or geometry is aware of the arrangement**, which is what makes this safe to share: a
 * grid publishes its bounds from wherever it is placed, and one `DragCoordinator` hit-tests every zone in one space.
 * Both slots are emitted with the same parameters in either branch.
 *
 * **The insets are applied here and the margins are not.** `uiInsets` (system bars ∪ cutout) is a system constraint
 * on the pair, so it pads the container; a zone's horizontal margin is *that zone's setting*, so it goes on that
 * zone's own modifier — which is also what keeps drag correct for free, since both drag surfaces publish geometry
 * from an `onGloballyPositioned` placed after the caller's modifier. Padding the container instead would inset both
 * zones by one shared number and quietly make them un-independent.
 *
 * The side zone's margin is applied *inside* its extent, never outside: the thickness is what the user set, and its
 * cells divide it, so insetting along that axis would silently shrink the zone they configured.
 *
 * @param extent the side zone's thickness — a height on a strip, a width on a rail.
 * @param mainPadding the main area's own horizontal margin.
 * @param sidePadding the side zone's own horizontal margin.
 * @param side the side zone, given a modifier that sizes it to [extent] across the container and insets it.
 * @param main the main area, given a modifier that takes the remaining space and insets it.
 */
@Composable
internal fun HomeZoneScaffold(
    edge: SideZoneEdge,
    extent: Dp,
    mainPadding: Dp,
    sidePadding: Dp,
    modifier: Modifier = Modifier,
    side: @Composable (Modifier) -> Unit,
    main: @Composable (Modifier) -> Unit,
) {
    val zones = modifier.fillMaxSize().windowInsetsPadding(uiInsets)
    if (edge.isStrip) {
        Column(zones) {
            // `weight` is a `ColumnScope`/`RowScope` member, so the remainder modifier can only be built inside the
            // container — which is why these four lines are here rather than hoisted above the branch.
            val sideModifier = Modifier.fillMaxWidth().height(extent).padding(horizontal = sidePadding)
            val mainModifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = mainPadding)
            if (edge.isLeading) side(sideModifier)
            main(mainModifier)
            if (!edge.isLeading) side(sideModifier)
        }
    } else {
        Row(zones) {
            val sideModifier = Modifier.fillMaxHeight().width(extent).padding(horizontal = sidePadding)
            val mainModifier = Modifier.fillMaxHeight().weight(1f).padding(horizontal = mainPadding)
            if (edge.isLeading) side(sideModifier)
            main(mainModifier)
            if (!edge.isLeading) side(sideModifier)
        }
    }
}
