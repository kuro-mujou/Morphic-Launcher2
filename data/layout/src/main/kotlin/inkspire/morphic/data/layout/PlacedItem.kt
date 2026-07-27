package inkspire.morphic.data.layout

import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/**
 * Where a grid item sits on HOME: its [placement] (page/row/col/spans) and which [zone] it lives in. The value
 * half of [LayoutRepository.placements] — pairing it with the [GridItem] key answers "what is where".
 *
 * Putting the [HomeZone] in this value (rather than in a per-zone flow) is what collapses L1's per-zone
 * placement-flow explosion into one [LayoutRepository.placements] stream.
 */
data class PlacedItem(val placement: GridPlacement, val zone: HomeZone)
