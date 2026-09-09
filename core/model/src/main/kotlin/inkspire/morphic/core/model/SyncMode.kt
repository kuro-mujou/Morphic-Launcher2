package inkspire.morphic.core.model

/**
 * How a layout is carried between two orientations that are kept in step.
 *
 * Only meaningful while the pair is linked and only where the board actually turns
 * ([DeviceConfiguration.boardRotates]) — an independent layout is nobody's derivation, and a tablet's side zone
 * keeps its axis, so there is no board to turn. The setting is **absent rather than disabled** in both cases, per
 * the standing rule that a control which changes nothing should not appear.
 */
enum class SyncMode {

    /**
     * Re-lay the items in reading order, densely, into whatever shape the target grid is.
     *
     * The default, and the only one that works on any pair of grids. **Gaps do not survive**, which is the price of
     * filling a differently-shaped screen; a user who wants their gaps kept wants [ROTATE_IN_PLACE] or an
     * independent layout.
     */
    REFLOW,

    /**
     * Turn the whole board with the device, so every item keeps its physical position on the glass.
     *
     * **Bijective, so it round-trips exactly** — gaps and spans included — which is the entire reason it is worth
     * having beside [REFLOW]. That holds only because a linked landscape grid is portrait's transpose; against any
     * other shape the transform would put items outside the grid.
     *
     * Visible consequence, and it is the behavior rather than a flaw: the dock strip read left-to-right becomes the
     * rail read **bottom-to-top**. That is what physically turning the device does.
     */
    ROTATE_IN_PLACE,
}

/**
 * The mode actually in force on [device] — [SyncMode.ROTATE_IN_PLACE] only where the board genuinely turns.
 *
 * **The gate lives here rather than at each call site**, because forgetting it is silent: a rotation applied on a
 * tablet, whose grids are not transposes, drops every item whose turned footprint misses the target. One function
 * means the settings control and the projection cannot disagree about when the option applies.
 */
fun SyncMode.on(device: DeviceConfiguration): SyncMode =
    if (this == SyncMode.ROTATE_IN_PLACE && device.boardRotates) SyncMode.ROTATE_IN_PLACE else SyncMode.REFLOW
