package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AlphabetStripStyle
import inkspire.morphic.core.model.GridSlot
import kotlinx.serialization.Serializable

/**
 * **The A–Z rail, asked of each layout that can draw one**: is it on, and which of its two looks does it wear?
 *
 * **Slot-keyed, where this used to be one switch for the launcher.** The single value rested on the rail belonging to
 * *A–Z-ordered content* — a property two surfaces could share — so a user wanting it on the list and not the grid was
 * asking two answers to one question. HOME's list is what made that false: its order is the user's own, its rail
 * *filters* rather than indexes, and it costs a column of the home screen. With the ordering no longer the thing the
 * rail attaches to, what is left is the layout, and three of them can hold one — which is the shape `AppsChrome`'s
 * rule already prescribes for a setting more than one layout can hold.
 *
 * [SurfacePaging] is the near neighbor in every way, including the L1 precedent it records: one `infiniteScroll` flag
 * read by three pagers, configurable from the Home screen alone, so turning it on for home silently changed the app
 * drawer and a user configuring the drawer had no control to find. A single rail switch shown in three sections would
 * have been that, arrived at from the other direction.
 *
 * **Sparse**, like every override in this module: an absent slot follows its blueprint, and clearing a toggle removes
 * the entry rather than storing the default back. Which grids offer the setting at all — and what each falls back to
 * — is [inkspire.morphic.core.model.GridBlueprint.alphabetRail]'s answer, not this type's.
 *
 * **The style is stored per slot too, though only the rail wears one.** The category pager's letter *picker* is a grid
 * of letters with no rail to bow, and it is not in this map at all: it is drawn wherever that layout is, because a
 * button in a header costs nothing where a rail costs a column.
 */
@Serializable
data class AlphabetRails(
    val enabled: Map<GridSlot, Boolean> = emptyMap(),
    val style: Map<GridSlot, AlphabetStripStyle> = emptyMap(),
) {

    /**
     * Whether [slot] draws a rail: [base] — its blueprint's — unless the user has set one here.
     *
     * Consumers never see this; they ask [SettingsRepository.alphabetRails] for resolved answers, and the keying stays
     * inside this module exactly as [SurfacePaging]'s does.
     */
    fun enabledFor(slot: GridSlot, base: Boolean): Boolean = enabled[slot] ?: base

    /** Which look [slot]'s rail wears, defaulting to the plain one. A look has no blueprint to fall back to. */
    fun styleFor(slot: GridSlot): AlphabetStripStyle = style[slot] ?: AlphabetStripStyle.STANDARD

    /**
     * A copy with [slot]'s rail switched to [value], or **cleared** when it is null — after which that layout follows
     * its blueprint again.
     */
    fun withEnabled(slot: GridSlot, value: Boolean?): AlphabetRails =
        copy(enabled = if (value == null) enabled - slot else enabled + (slot to value))

    /** [withEnabled]'s twin, over the [style] map. */
    fun withStyle(slot: GridSlot, value: AlphabetStripStyle?): AlphabetRails =
        copy(style = if (value == null) style - slot else style + (slot to value))

    companion object {
        /** Nothing set — every layout on its blueprint, which is no rail anywhere. */
        val Default = AlphabetRails()
    }
}

/**
 * One layout's answer, resolved — what [SettingsRepository.alphabetRails] hands back per slot.
 *
 * The two travel together because a reader needs both at once and neither is useful alone: a style with nothing drawn
 * configures nothing, and a rail with no style cannot be drawn.
 */
data class AlphabetRail(val enabled: Boolean, val style: AlphabetStripStyle) {

    /** The style to draw, or null when this layout has no rail — the shape every consumer actually wants. */
    val drawn: AlphabetStripStyle? get() = style.takeIf { enabled }
}
